package com.company.common.bills.service;

import com.company.common.bills.dto.BillContracts.*;
import com.company.common.clients.service.ClientRules;
import com.company.common.common.BillingCalculationRules;
import com.company.common.projects.service.ProjectRules;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

final class BillInput {
    private BillInput() {}
    private static final Set<String> FIELDS=Set.of("supplyAmount","vatRate","billDate","payDueDate","taxInvoiceNo");

    static Values edit(JsonNode body) {
        ClientRules.object(body,FIELDS);
        return values(body);
    }

    static CreateInput create(JsonNode body, BusinessCalendar calendar) {
        var fields=new HashSet<>(FIELDS);
        fields.addAll(Set.of("projectId","billMonth","sourceBillId","duplicateConfirmed"));
        ClientRules.object(body,fields);
        String project=ProjectRules.text(body,"projectId",20,true);
        String month=ProjectRules.text(body,"billMonth",6,true);
        month(month,calendar);
        var confirmed=body.get("duplicateConfirmed");
        if (confirmed!=null && !confirmed.isBoolean())
            throw CodeFailure.invalid("duplicateConfirmed","중복 청구 확인 값을 확인해 주세요.");
        return new CreateInput(project,month,values(body),
            ProjectRules.text(body,"sourceBillId",20,false),confirmed!=null && confirmed.booleanValue());
    }

    static void month(String value, BusinessCalendar calendar) {
        try { calendar.fromDatabaseMonth(value); }
        catch (RuntimeException e) { throw CodeFailure.invalid("billMonth","청구월은 YYYYMM 형식으로 입력해 주세요."); }
    }

    private static Values values(JsonNode body) {
        var supply=ProjectRules.money(body,"supplyAmount",true);
        BillingCalculationRules.positiveMoney(supply,"supplyAmount");
        var raw=body.get("vatRate");
        BigDecimal rate=BigDecimal.TEN;
        if (raw!=null) {
            if (!raw.isNumber()) throw CodeFailure.invalid("vatRate","부가세율을 입력해 주세요.");
            rate=raw.decimalValue();
        }
        BillingCalculationRules.rate(rate,"vatRate");
        LocalDate date=ProjectRules.date(body,"billDate"),due=ProjectRules.date(body,"payDueDate");
        if (due.isBefore(date)) throw CodeFailure.invalid("payDueDate","입금 예정일은 청구일 이후 또는 같은 날이어야 합니다.");
        return new Values(supply,rate,date,due,ProjectRules.text(body,"taxInvoiceNo",30,false));
    }

    static String reason(JsonNode body) {
        ClientRules.object(body,Set.of("reason"));
        return ProjectRules.text(body,"reason",500,true);
    }
}
