package com.company.common.receipts.service;

import com.company.common.common.BillingCalculationRules;
import com.company.common.common.BillingInputRules;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.receipts.dto.ReceiptContracts.*;
import com.company.common.receipts.mapper.ReceiptMapper;
import com.company.common.receipts.mapper.ReceiptMapper.*;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class ReceiptService {
    private final ReceiptMapper mapper;
    private final ProjectMapper projects;
    private final BillingCalculationRules rules;
    private final BusinessCalendar calendar;
    private final CurrentBusinessActor actor;

    public ReceiptService(ReceiptMapper mapper, ProjectMapper projects, BillingCalculationRules rules,
        BusinessCalendar calendar, CurrentBusinessActor actor) {
        this.mapper=mapper;
        this.projects=projects;
        this.rules=rules;
        this.calendar=calendar;
        this.actor=actor;
    }

    @Transactional
    public Result postCommonReceipt(JsonNode body) {
        BillingInputRules.object(body,Set.of("receiptDate","items"));
        LocalDate date=date(body.path("receiptDate"));
        JsonNode rows=body.path("items");
        if (!rows.isArray() || rows.isEmpty())
            throw CodeFailure.invalid("items","입금할 청구를 선택해 주세요.");
        var items=new ArrayList<Item>();
        var ids=new TreeSet<String>();
        // 여러 청구를 한 번에 받을 때 오류가 그 줄에만 보이도록 칸 이름에 줄 번호를 붙인다(예: items[2].amount).
        var rowOf=new HashMap<String,Integer>();
        for (int index=0;index<rows.size();index++) {
            JsonNode row=rows.get(index);
            String prefix="items["+index+"].";
            BillingInputRules.object(row,Set.of("billId","amount"));
            JsonNode id=row.path("billId");
            if (!id.isTextual() || id.asText().isBlank() || id.asText().length()>20
                || id.asText().chars().anyMatch(Character::isISOControl))
                throw CodeFailure.invalid(prefix+"billId","청구를 확인해 주세요.");
            if (!ids.add(id.asText()))
                throw CodeFailure.invalid(prefix+"billId","같은 청구는 한 번만 선택해 주세요.");
            JsonNode raw=row.path("amount");
            if (!raw.isNumber() || raw.decimalValue().signum()==0)
                throw CodeFailure.invalid(prefix+"amount","입금액을 입력해 주세요.");
            BigDecimal amount=raw.decimalValue();
            if (amount.signum()<0 || amount.stripTrailingZeros().scale()>0 || amount.compareTo(BigDecimal.TEN.pow(15))>=0)
                throw CodeFailure.invalid(prefix+"amount","입금액은 15자리 이내의 원 단위 양수로 입력해 주세요.");
            rowOf.put(id.asText(),index);
            items.add(new Item(id.asText(),amount));
        }
        // Same source/table boundary as the existing billing creators; closure cannot change mid-write.
        projects.lockSources();
        var bills=new HashMap<String,Bill>();
        for (String id:ids) bills.put(id,requiredBill(id,400,"items["+rowOf.get(id)+"].billId"));
        open(YearMonth.from(calendar.today()),"receiptDate");
        open(YearMonth.from(date),"receiptDate");
        for (int index=0;index<items.size();index++) {
            Item item=items.get(index);
            Bill bill=bills.get(item.billId());
            if (bill.cancelled()) throw CodeFailure.invalid("items["+index+"].billId","취소된 청구에는 입금할 수 없습니다.");
            BigDecimal balance=rules.balance(bill.totalAmount(),mapper.normalSum(bill.billId()));
            if (item.amount().compareTo(balance)>0)
                throw CodeFailure.invalid("items["+index+"].amount","입금액이 미수금보다 큽니다.");
        }
        var receipts=new ArrayList<Receipt>();
        for (Item item:items) {
            String id=mapper.insertReceipt(new ReceiptWrite(item.billId(),date,item.amount(),actor.userId()));
            receipts.add(mapper.receipt(id).view());
        }
        for (String id:ids) recompute(bills.get(id));
        return new Result(items.stream().map(Item::billId).toList(),receipts);
    }

    @Transactional
    public Result postCommonReceiptCancel(String id, JsonNode body) {
        BillingInputRules.object(body,Set.of("reason","modifiedAt"));
        // 청구 취소와 같은 문구(「취소 사유를 입력해 주세요.」·「취소 사유는 500자까지 입력해 주세요.」).
        String reason=BillingInputRules.text(body,"reason","취소 사유",500,true);
        Instant token=token(body);
        if (id==null || !id.matches("[0-9]{1,12}"))
            throw new CodeFailure(404,"receiptId","입금을 찾을 수 없습니다.");
        projects.lockSources();
        ReceiptRow found=mapper.receipt(id);
        if (found==null) throw new CodeFailure(404,"receiptId","입금을 찾을 수 없습니다.");
        Bill bill=requiredBill(found.billId(),404,"receiptId");
        ReceiptRow receipt=mapper.lockReceipt(id);
        if (receipt==null) throw new CodeFailure(404,"receiptId","입금을 찾을 수 없습니다.");
        if (receipt.cancelled()) throw CodeFailure.invalid("receiptId","이미 취소된 입금입니다.");
        if (mapper.closed(calendar.toDatabaseMonth(YearMonth.from(receipt.receiptDate()))))
            throw CodeFailure.invalid("receiptId",calendar.toDatabaseMonth(YearMonth.from(receipt.receiptDate())).substring(0,4)
                +"-"+calendar.toDatabaseMonth(YearMonth.from(receipt.receiptDate())).substring(4)+"는 마감한 달이라 이 입금은 취소할 수 없습니다.");
        // 00-1 동시 수정: 화면이 읽은 수정 시각과 지금 값이 다르면 409(공통 문구).
        if (!Objects.equals(receipt.modifiedAt(),token)) throw CodeFailure.conflict();
        if (mapper.cancel(id,reason,actor.userId(),token)!=1) throw CodeFailure.conflict();
        recompute(bill);
        return new Result(List.of(bill.billId()),List.of(mapper.receipt(id).view()));
    }

    private static Instant token(JsonNode body) {
        JsonNode node=body.get("modifiedAt");
        if (node==null || node.isNull()) throw CodeFailure.invalid("modifiedAt","수정 시각을 제출해 주세요.");
        try {
            if (!node.isTextual()) throw new IllegalArgumentException();
            return Instant.parse(node.textValue());
        } catch (RuntimeException exception) { throw CodeFailure.invalid("modifiedAt","수정 시각을 확인해 주세요."); }
    }

    private Bill requiredBill(String id,int status,String field) {
        Bill bill=mapper.lockBill(id);
        if (bill==null) throw new CodeFailure(status,field,"청구를 찾을 수 없습니다.");
        return bill;
    }

    private void recompute(Bill bill) {
        BigDecimal sum=mapper.normalSum(bill.billId());
        BigDecimal unpaid=rules.balance(bill.totalAmount(),sum);
        mapper.updateBalance(new BalanceWrite(bill.billId(),sum,unpaid,actor.userId()));
    }

    private void open(YearMonth month,String field) {
        if (mapper.closed(calendar.toDatabaseMonth(month)))
            throw CodeFailure.invalid(field,"마감된 달에는 입금을 등록하거나 취소할 수 없습니다.");
    }

    private LocalDate date(JsonNode value) {
        LocalDate date;
        try {
            if (!value.isTextual() || !value.asText().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
                throw new IllegalArgumentException();
            date=LocalDate.parse(value.asText());
        } catch (RuntimeException exception) {
            throw CodeFailure.invalid("receiptDate","입금일을 입력해 주세요.");
        }
        LocalDate today=calendar.today();
        if (date.getYear()!=today.getYear() || date.isAfter(today))
            throw CodeFailure.invalid("receiptDate","입금일은 올해의 미래가 아닌 날짜로 입력해 주세요.");
        return date;
    }
}
