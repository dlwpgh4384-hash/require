package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.cashflowstatements.service.CommonCashFlowStatementService;
import com.company.common.cashflowstatements.dto.CashFlowStatementContracts;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.support.service.BusinessCalendar;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.company.common.aianalysis.service.AiAnalysisTables.*;

/** F-07 자금 현황, P-03 자금 잔액 예측(대시보드와 같은 시작 달, 자금수지표 forward). */
@Component
public class AiAnalysisCashFunctions implements AiAnalysisFunction {
    private final CommonCashFlowStatementService cash;
    private final UserPreferencePeriodBasis periods;
    private final BusinessCalendar calendar;
    private final AiAnalysisTables tables;
    public AiAnalysisCashFunctions(CommonCashFlowStatementService cash, UserPreferencePeriodBasis periods,
        BusinessCalendar calendar, AiAnalysisTables tables) {
        this.cash = cash; this.periods = periods; this.calendar = calendar; this.tables = tables;
    }
    public Set<String> functions() { return Set.of("F-07", "P-03"); }
    public Result read(AiAnalysisInterpreter.Query query) {
        var first = YearMonth.parse(query.period().startMonth());
        var end = YearMonth.parse(query.period().endMonth());
        boolean forecast = "P-03".equals(query.function());
        var rows = new ArrayList<CashFlowStatementContracts.Month>();
        var warnings = new ArrayList<CashFlowStatementContracts.Warning>();
        java.math.BigDecimal minimum = null;
        long asked = 0;
        if (forecast) {
            if (!(periods.metadata().get("periodBasis") instanceof UserPreferencePeriodBasis.Basis basis)) throw new NoAnswer(null);
            int count = 3;
            if (query.periodGiven()) {
                asked = java.time.temporal.ChronoUnit.MONTHS.between(first, end) + 1;
                count = (int) Math.max(1, Math.min(3, asked));
            }
            first = basis.firstOpen(calendar.currentMonth());
            end = first.plusMonths(count - 1);
            var result = cash.forward(first, count);
            rows.addAll(result.months());
            warnings.addAll(result.warnings());
            minimum = result.minimumCash();
        } else {
            for (int year = first.getYear(); year <= end.getYear(); year++) {
                var result = cash.getCommonCashFlowStatements(new CashFlowStatementContracts.Filter(year)).data();
                rows.addAll(result.months());
                warnings.addAll(result.warnings());
                minimum = result.minimumCash();
            }
        }
        String from = key(first), through = key(end);
        var selected = rows.stream().filter(r -> r.yearMonth().compareTo(from) >= 0 && r.yearMonth().compareTo(through) <= 0).toList();
        if (selected.isEmpty() || selected.stream().anyMatch(r -> r.closingBalance() == null)) throw new NoAnswer(null);
        var shortage = warnings.stream().filter(w -> w.yearMonth().compareTo(from) >= 0 && w.yearMonth().compareTo(through) <= 0).toList();
        var table = AiAnalysisTables.table(null, List.of(Column.month("month", "연월"), Column.money("openingBalance", "전월 이월금"),
            Column.money("incomeTotal", "수입"), Column.money("expenseTotal", "지출"), Column.money("closingBalance", "이월금")),
            selected.stream().map(r -> Arrays.<Object>asList(month(r.yearMonth()).toString(), r.openingBalance(), r.incomeTotal(),
                r.expenseTotal(), r.closingBalance())).toList(),
            selected.stream().map(r -> Arrays.asList(null, null, null, null,
                shortage.stream().anyMatch(w -> w.yearMonth().equals(r.yearMonth())) ? "bad" : null)).toList(), null);
        var period = span(first, end);
        String conclusion;
        if (selected.size() == 1) conclusion = month(selected.get(0).yearMonth()) + " 말 잔액(이월금)은 "
            + bold(money(selected.get(0).closingBalance())) + "입니다.";
        else conclusion = (forecast ? "앞으로 " + selected.size() + "개월(" + period(period) + ") 예상 잔액은 " : period(period) + " 잔액은 ")
            + String.join(" · ", selected.stream().map(r -> month(r.yearMonth()).getMonthValue() + "월 " + bold(money(r.closingBalance()))).toList())
            + "입니다.";
        if (!shortage.isEmpty()) conclusion += " 최소 운영자금(" + money(minimum) + ")보다 적은 달: "
            + String.join(" · ", shortage.stream().map(w -> month(w.yearMonth()).getMonthValue() + "월 " + bold(money(w.expectedBalance()))).toList()) + ".";
        else if (forecast && minimum != null) conclusion += " 모든 달이 최소 운영자금(" + money(minimum) + ") 이상입니다.";
        var assumptions = new ArrayList<String>();
        if (forecast) assumptions.add("마지막 마감 달 다음 달부터 " + selected.size() + "개월, 자금수지표의 예정 값과 같습니다(대시보드 자금 칸과 같은 달).");
        else assumptions.add("자금수지표와 같은 숫자입니다. 마감 전 달은 예정 값입니다.");
        if (forecast && asked > selected.size())
            assumptions.add(AiAnalysisPipelineFunctions.trimmed("자금 잔액", asked, selected.size(), "마지막 마감 다음 달부터 최대 3개월"));
        if (minimum != null) assumptions.add("최소 운영자금은 운영 기준값 " + money(minimum) + "입니다.");
        // 예측 답은 기준월(BASE_YM)이 이번 달이고 잠정 표시를 달지 않는다(「예측」 표시).
        return new Result(conclusion, List.of(table), null, assumptions, used(tables.screen("UI-21"), period),
            forecast ? calendar.currentMonth().toString() : end.toString(),
            !forecast && selected.stream().anyMatch(r -> !r.closed()), forecast, List.of(tables.open("UI-21", period)));
    }
}
