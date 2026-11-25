package com.company.common.aianalysis.service;

import com.company.common.bills.service.CommonBillService;
import com.company.common.bills.dto.BillContracts;
import com.company.common.billchecks.service.BillCheckService;
import com.company.common.common.BillingCalculationRules;
import com.company.common.support.service.BusinessCalendar;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisBillFunctions implements AiAnalysisFunction {
    private final CommonBillService bills;
    private final BillCheckService checks;
    private final BillingCalculationRules rules;
    private final BusinessCalendar calendar;
    private final AiAnalysisTables tables;
    public AiAnalysisBillFunctions(CommonBillService bills, BillCheckService checks, BillingCalculationRules rules,
        BusinessCalendar calendar, AiAnalysisTables tables) {
        this.bills = bills; this.checks = checks; this.rules = rules; this.calendar = calendar; this.tables = tables;
    }
    public Set<String> functions() { return Set.of("F-06", "P-05"); }
    public Result read(AiAnalysisInterpreter.Query query) {
        var first = YearMonth.parse(query.period().startMonth());
        var end = YearMonth.parse(query.period().endMonth());
        var source = new ArrayList<BillContracts.Bill>();
        for (int year = first.getYear(); year <= end.getYear(); year++)
            source.addAll(bills.getCommonBills(tables.filter(Map.of("year", year), BillContracts.Filter.class)).items());
        var projects = source.stream().filter(r -> query.question().contains(r.projectId())
            || query.question().contains(r.projectName()) || query.question().contains(r.customerName())).map(BillContracts.Bill::projectId).distinct().toList();
        if (query.question().contains("프로젝트") && projects.size() != 1) return null;
        var rows = new ArrayList<Object>();
        for (var bill : source) {
            if (bill.cancelled() || !projects.isEmpty() && !projects.contains(bill.projectId())) continue;
            if (bill.billMonth().compareTo(first.toString().replace("-", "")) < 0
                || bill.billMonth().compareTo(end.toString().replace("-", "")) > 0) continue;
            if (query.question().contains("미수") && bill.unpaidAmount().signum() <= 0) continue;
            var row = new LinkedHashMap<String, Object>();
            row.put("projectName", bill.projectName()); row.put("customerName", bill.customerName());
            row.put("unpaidAmount", bill.unpaidAmount()); row.put("totalAmount", bill.totalAmount());
            row.put("payDueDate", bill.payDueDate()); row.put("dunningStep", bill.dunningStep());
            row.put("nextDate", rules.nextDunningDate(false, bill.unpaidAmount(), bill.payDueDate(), calendar.today()));
            rows.add(row);
        }
        var checked = checks.getCommonBillChecks(end.toString().replace("-", ""));
        return new Result(tables.table(rows, "projectName", "프로젝트", "customerName", "거래처", "totalAmount", "청구 합계",
            "unpaidAmount", "미수금", "payDueDate", "입금 예정일", "dunningStep", "미수 알림 단계", "nextDate", "다음 단계 날짜"),
            query.period(), checked.plannedBasis(), "UI-13", List.of("청구·입금 및 청구 점검의 기존 결과를 사용합니다. 미수 알림은 오늘 기준입니다."));
    }
}
