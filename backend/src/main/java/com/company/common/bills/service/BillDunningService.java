package com.company.common.bills.service;

import com.company.common.bills.dto.BillContracts.*;
import com.company.common.bills.mapper.BillMapper;
import com.company.common.bills.mapper.BillRows.*;
import com.company.common.common.BillingCalculationRules;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.support.service.*;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class BillDunningService {
    private final BillMapper mapper;
    private final ProjectMapper projects;
    private final BillingCalculationRules rules;
    private final BusinessCalendar calendar;
    private final CurrentBusinessActor actor;
    private final BillLocalMailbox mailbox;

    public BillDunningService(BillMapper mapper, ProjectMapper projects, BillingCalculationRules rules,
        BusinessCalendar calendar, CurrentBusinessActor actor, BillLocalMailbox mailbox) {
        this.mapper=mapper;
        this.projects=projects;
        this.rules=rules;
        this.calendar=calendar;
        this.actor=actor;
        this.mailbox=mailbox;
    }

    @Transactional
    public SendResult postCommonBillDunningResend(String id) {
        return deliver(id,true);
    }

    @Transactional
    public SendResult sendAutomatic(String id) {
        return deliver(id,false);
    }

    /**
     * 미수 알림을 받을 사람 이름(보내기와 같은 규칙: 1단계 PM이 없으면 2단계 받는 사람). 목록 「다음 알림」 칸에 쓴다
     * (시안 「날짜 n단계 → 받는 사람」, 2026-11-24 사용자 결정).
     */
    public List<String> recipientNames(String projectId, int step) {
        String month=calendar.toDatabaseMonth(calendar.currentMonth());
        var recipients=mapper.recipients(new RecipientFilter(projectId,month,step));
        if (recipients.isEmpty() && step==1) recipients=mapper.recipients(new RecipientFilter(projectId,month,2));
        return recipients.stream().map(r -> r.name()).toList();
    }

    /** 받는 사람 설명이 300자(TB 칸 길이)를 넘으면 앞사람부터 넣고 「외 n명」으로 줄인다(2026-11-24 사용자 결정). */
    static String fitDescription(List<String> people, String tail) {
        String full=String.join(", ",people)+tail;
        if (full.codePointCount(0,full.length())<=300) return full;
        for (int keep=people.size()-1;keep>=1;keep--) {
            String text=String.join(", ",people.subList(0,keep))+" 외 "+(people.size()-keep)+"명"+tail;
            if (text.codePointCount(0,text.length())<=300) return text;
        }
        String first=people.get(0);
        String suffix=" 외 "+(people.size()-1)+"명"+tail;
        int room=Math.max(1,300-suffix.codePointCount(0,suffix.length())-1);
        return first.substring(0,first.offsetByCodePoints(0,Math.min(room,first.codePointCount(0,first.length()))))+"…"+suffix;
    }

    private SendResult deliver(String id, boolean resend) {
        if (id==null || id.isBlank() || id.length()>20 || id.chars().anyMatch(Character::isISOControl))
            throw CodeFailure.invalid("billId","청구번호를 확인해 주세요.");
        projects.lockSources();
        var bill=mapper.lockInvoice(id);
        if (bill==null) throw new CodeFailure(404,"billId","청구를 찾을 수 없습니다.");
        var balance=rules.balance(bill.totalAmount(),mapper.normalReceipts(id));
        Integer step=rules.dunningStep(bill.cancelled(),balance,bill.payDueDate(),calendar.today());
        if (step==null) {
            if (!resend) return null;
            throw CodeFailure.invalid("billId","미수 알림 1~3단계 청구만 다시 알릴 수 있습니다.");
        }
        if (resend && mapper.resent(id,step)) throw CodeFailure.invalid("billId","이 단계의 다시 알림은 이미 보냈습니다.");
        if (!resend && mapper.automaticallySent(id,step)) return null;
        String month=calendar.toDatabaseMonth(calendar.currentMonth());
        var recipients=mapper.recipients(new RecipientFilter(bill.projectId(),month,step));
        String memo=null;
        if (recipients.isEmpty() && step==1) {
            // 사용자 결정(2026-11-19): 1단계 받을 PM이 없으면 2단계 받는 사람(그 달 파트장, 본사 직접 영업은 본사 영업
            // 책임자, 없으면 경영진)에게 한 번 대신 보내고 이력에 남긴다(명세 「파트 없으면 경영진」과 같은 방식).
            recipients=mapper.recipients(new RecipientFilter(bill.projectId(),month,2));
            memo="PM 없음, 대신 보냄";
        }
        if (recipients.isEmpty()) throw CodeFailure.invalid("delivery","현재 단계의 받는 사람이 없습니다.");
        // 메일 주소가 없거나 틀린 사람이 있어도 다른 사람에게는 보내고(시스템 알림은 모두), 이력에 「메일 없음」을 남긴다.
        var mailable=recipients.stream().filter(BillLocalMailbox::deliverable).toList();
        String description=fitDescription(recipients.stream().map(r -> r.name()+" ("+r.userId()+")"
                +(BillLocalMailbox.deliverable(r)?"":" (메일 없음)")).toList(), memo==null?"":" ("+memo+")");
        String content=content(bill,step,resend,balance);
        Path accepted=mailable.isEmpty()?null:mailbox.deliver(mailable,content);
        if (accepted!=null && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status!=STATUS_COMMITTED) mailbox.discard(accepted);
                }
            });
        }
        try {
            for (var recipient:recipients) {
                if (mapper.notification(new Notice(id,recipient.userId(),content,actor.userId()))!=1)
                    throw CodeFailure.invalid("delivery","시스템 알림 저장에 실패했습니다. 다시 시도해 주세요.");
            }
            if (mapper.history(new Delivery(id,step,calendar.today(),description,resend,actor.userId()))!=1)
                throw CodeFailure.invalid("delivery","발송 이력 저장에 실패했습니다. 다시 시도해 주세요.");
        } catch (RuntimeException exception) {
            if (accepted!=null) mailbox.discard(accepted);
            throw exception;
        }
        return new SendResult(id,new DunningHistory(step,calendar.today(),description,"BOTH",resend));
    }

    /** 시안 알림 글: 「미수 2단계 알림: 다솜생명 보험금 지급 고도화 8월분 (32일 지남)」 + 청구번호·미수금. */
    private String content(Invoice bill, int step, boolean resend, java.math.BigDecimal balance) {
        long days=java.time.temporal.ChronoUnit.DAYS.between(bill.payDueDate(),calendar.today());
        int month=Integer.parseInt(bill.billMonth().substring(4,6));
        String text="미수 "+step+"단계 알림"+(resend?"(다시)":"")+": "+bill.projectName()+" "+month+"월분 ("
            +days+"일 지남) · "+bill.billId()+" · 미수금 "+new java.text.DecimalFormat("#,##0").format(balance)+"원";
        return text.codePointCount(0,text.length())<=300?text:text.substring(0,text.offsetByCodePoints(0,299))+"…";
    }
}
