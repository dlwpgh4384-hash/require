package com.company.common.receipts.service;

import com.company.common.clients.service.ClientRules;
import com.company.common.common.BillingCalculationRules;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.receipts.dto.ReceiptContracts.*;
import com.company.common.receipts.mapper.ReceiptMapper;
import com.company.common.receipts.mapper.ReceiptMapper.*;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
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
        ClientRules.object(body,Set.of("receiptDate","items"));
        LocalDate date=date(body.path("receiptDate"));
        JsonNode rows=body.path("items");
        if (!rows.isArray() || rows.isEmpty())
            throw CodeFailure.invalid("items","입금할 청구를 선택해 주세요.");
        var items=new ArrayList<Item>();
        var ids=new TreeSet<String>();
        for (JsonNode row:rows) {
            ClientRules.object(row,Set.of("billId","amount"));
            JsonNode id=row.path("billId");
            if (!id.isTextual() || id.asText().isBlank() || id.asText().length()>20
                || id.asText().chars().anyMatch(Character::isISOControl))
                throw CodeFailure.invalid("items.billId","청구를 확인해 주세요.");
            if (!ids.add(id.asText()))
                throw CodeFailure.invalid("items.billId","같은 청구는 한 번만 선택해 주세요.");
            JsonNode raw=row.path("amount");
            if (!raw.isNumber() || raw.decimalValue().signum()==0)
                throw CodeFailure.invalid("items.amount","입금액을 입력해 주세요");
            BigDecimal amount=raw.decimalValue();
            BillingCalculationRules.positiveMoney(amount,"items.amount");
            items.add(new Item(id.asText(),amount));
        }
        // Same source/table boundary as the existing billing creators; closure cannot change mid-write.
        projects.lockSources();
        var bills=new HashMap<String,Bill>();
        for (String id:ids) bills.put(id,requiredBill(id,400));
        open(YearMonth.from(calendar.today()),"receiptDate");
        open(YearMonth.from(date),"receiptDate");
        for (Item item:items) {
            Bill bill=bills.get(item.billId());
            if (bill.cancelled()) throw CodeFailure.invalid("items.billId","취소된 청구에는 입금할 수 없습니다.");
            BigDecimal balance=rules.balance(bill.totalAmount(),mapper.normalSum(bill.billId()));
            if (item.amount().compareTo(balance)>0)
                throw CodeFailure.invalid("items.amount","입금액이 미수금보다 큽니다");
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
        ClientRules.object(body,Set.of("reason"));
        JsonNode raw=body.path("reason");
        if (!raw.isTextual() || raw.asText().strip().isBlank())
            throw CodeFailure.invalid("reason","취소 사유를 입력해 주세요.");
        String reason=raw.asText().strip();
        if (reason.codePointCount(0,reason.length())>500)
            throw CodeFailure.invalid("reason","취소 사유는 500자 이내로 입력해 주세요.");
        if (id==null || !id.matches("[0-9]{1,12}"))
            throw new CodeFailure(404,"receiptId","입금을 찾을 수 없습니다.");
        projects.lockSources();
        ReceiptRow found=mapper.receipt(id);
        if (found==null) throw new CodeFailure(404,"receiptId","입금을 찾을 수 없습니다.");
        Bill bill=requiredBill(found.billId(),404);
        ReceiptRow receipt=mapper.lockReceipt(id);
        if (receipt==null) throw new CodeFailure(404,"receiptId","입금을 찾을 수 없습니다.");
        if (receipt.cancelled()) throw CodeFailure.invalid("receiptId","이미 취소된 입금입니다.");
        open(YearMonth.from(receipt.receiptDate()),"receiptId");
        if (mapper.cancel(id,reason,actor.userId())!=1)
            throw CodeFailure.invalid("receiptId","이미 취소된 입금입니다.");
        recompute(bill);
        return new Result(List.of(bill.billId()),List.of(mapper.receipt(id).view()));
    }

    private Bill requiredBill(String id,int status) {
        Bill bill=mapper.lockBill(id);
        if (bill==null) throw new CodeFailure(status,"items.billId","청구를 찾을 수 없습니다.");
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
