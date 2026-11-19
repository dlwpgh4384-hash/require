package com.company.common.bills.service;

import com.company.common.bills.dto.BillContracts.*;
import com.company.common.bills.mapper.BillMapper;
import com.company.common.bills.mapper.BillRows.*;
import com.company.common.common.BillingCalculationRules;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.support.service.*;
import java.nio.file.Path;
import java.util.stream.Collectors;
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
        if (resend && mapper.resent(id,step)) throw CodeFailure.invalid("billId","이 단계 다시 알림 보냄");
        if (!resend && mapper.automaticallySent(id,step)) return null;
        var recipients=mapper.recipients(new RecipientFilter(bill.projectId(),
            calendar.toDatabaseMonth(calendar.currentMonth()),step));
        if (recipients.isEmpty()) throw CodeFailure.invalid("delivery","현재 단계의 받는 사람이 없습니다.");
        String description=recipients.stream().map(r -> r.name()+" ("+r.userId()+")")
            .collect(Collectors.joining(", "));
        if (description.codePointCount(0,description.length())>300)
            throw CodeFailure.invalid("delivery","수신자 설명이 저장 범위를 초과합니다.");
        String content=id+" 미수 알림 "+step+"단계"+(resend?" (다시)":"")+" · 미수금 "+balance.toPlainString()+"원";
        Path accepted=mailbox.deliver(recipients,content);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
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
            mailbox.discard(accepted);
            throw exception;
        }
        return new SendResult(id,new DunningHistory(step,calendar.today(),description,"BOTH",resend));
    }
}
