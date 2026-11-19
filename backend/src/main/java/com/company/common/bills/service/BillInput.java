package com.company.common.bills.service;

import com.company.common.bills.dto.BillContracts.*;
import com.company.common.common.BillingCalculationRules;
import com.company.common.common.BillingInputRules;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

final class BillInput {
    private BillInput() {}
    private static final Set<String> FIELDS=Set.of("supplyAmount","vatRate","billDate","payDueDate","taxInvoiceNo");

    /** [수정]: 화면이 읽은 수정 시각(modifiedAt)을 함께 받는다(00-1 동시 수정 409). */
    static EditInput edit(JsonNode body, Supplier<BigDecimal> defaultRate) {
        var fields=new HashSet<>(FIELDS);
        fields.add("modifiedAt");
        BillingInputRules.object(body,fields);
        return new EditInput(values(body,defaultRate),token(body));
    }

    static CreateInput create(JsonNode body, BusinessCalendar calendar, BillingCalculationRules rules) {
        var fields=new HashSet<>(FIELDS);
        fields.addAll(Set.of("projectId","billMonth","sourceBillId","duplicateConfirmed"));
        BillingInputRules.object(body,fields);
        String project=BillingInputRules.text(body,"projectId","프로젝트",20,true);
        String month=BillingInputRules.text(body,"billMonth","청구월",6,true);
        month(month,calendar);
        var confirmed=body.get("duplicateConfirmed");
        if (confirmed!=null && !confirmed.isBoolean())
            throw CodeFailure.invalid("duplicateConfirmed","중복 청구 확인 값을 확인해 주세요.");
        return new CreateInput(project,month,values(body,() -> rules.defaultVatRate(calendar.fromDatabaseMonth(month))),
            BillingInputRules.text(body,"sourceBillId","원청구 번호",20,false),confirmed!=null && confirmed.booleanValue());
    }

    static void month(String value, BusinessCalendar calendar) {
        try { calendar.fromDatabaseMonth(value); }
        catch (RuntimeException e) { throw CodeFailure.invalid("billMonth","청구월은 YYYYMM 형식으로 입력해 주세요."); }
    }

    private static Values values(JsonNode body, Supplier<BigDecimal> defaultRate) {
        var supply=BillingInputRules.money(body,"supplyAmount","공급가",true);
        if (supply.signum()==0) throw CodeFailure.invalid("supplyAmount",BillingInputRules.required("공급가"));
        BillingCalculationRules.positiveMoney(supply,"supplyAmount");
        var raw=body.get("vatRate");
        BigDecimal rate;
        if (raw!=null && !raw.isNull()) {
            if (!raw.isNumber()) throw CodeFailure.invalid("vatRate","부가세율을 입력해 주세요.");
            rate=raw.decimalValue();
        } else rate=defaultRate.get();
        BillingCalculationRules.rate(rate,"vatRate");
        LocalDate date=BillingInputRules.date(body,"billDate","청구일");
        LocalDate due=BillingInputRules.date(body,"payDueDate","입금 예정일");
        if (due.isBefore(date)) throw CodeFailure.invalid("payDueDate","입금 예정일이 청구일보다 빠릅니다.");
        return new Values(supply,rate,date,due,BillingInputRules.text(body,"taxInvoiceNo","세금계산서 번호",30,false));
    }

    /** [청구 취소]: 사유와 화면이 읽은 수정 시각(modifiedAt). 문구는 입금 취소와 같다. */
    static CancelInput cancel(JsonNode body) {
        BillingInputRules.object(body,Set.of("reason","modifiedAt"));
        String reason=BillingInputRules.text(body,"reason","취소 사유",500,true);
        return new CancelInput(reason,token(body));
    }

    /** 화면이 읽은 수정 시각. 없거나 형식이 틀리면 400, 값이 지금과 다르면 서비스가 409. */
    static Instant token(JsonNode body) {
        return BillingInputRules.token(body);
    }
}
