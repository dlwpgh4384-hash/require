package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.cashflowstatements.service.CommonCashFlowStatementService;
import com.company.common.cashflowstatements.dto.CashFlowStatementContracts;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisCashFunctions implements AiAnalysisFunction {
    private final CommonCashFlowStatementService cash;
    private final UserPreferencePeriodBasis periods;
    private final AiAnalysisTables tables;
    public AiAnalysisCashFunctions(CommonCashFlowStatementService cash, UserPreferencePeriodBasis periods, AiAnalysisTables tables) {
        this.cash = cash; this.periods = periods; this.tables = tables;
    }
    public Set<String> functions() { return Set.of("F-07", "P-03"); }
    public Result read(AiAnalysisInterpreter.Query query) {
        var first = YearMonth.parse(query.period().startMonth());
        var end = YearMonth.parse(query.period().endMonth());
        var rows = new ArrayList<CashFlowStatementContracts.Month>();
        if ("P-03".equals(query.function())) {
            if (!(periods.metadata().get("periodBasis") instanceof UserPreferencePeriodBasis.Basis basis)) return null;
            String last = basis.closedMonths().stream().max(String::compareTo).orElse(null);
            if (last == null) return null;
            first = YearMonth.parse(last.substring(0, 4) + "-" + last.substring(4)).plusMonths(1);
            end = first.plusMonths(2);
            rows.addAll(cash.forward(first, 3).months());
        } else {
            for (int year = first.getYear(); year <= end.getYear(); year++)
                rows.addAll(cash.getCommonCashFlowStatements(new CashFlowStatementContracts.Filter(year)).data().months());
        }
        String from = first.toString().replace("-", ""), through = end.toString().replace("-", "");
        var selected = rows.stream().filter(r -> r.yearMonth().compareTo(from) >= 0 && r.yearMonth().compareTo(through) <= 0).toList();
        if ("P-03".equals(query.function()) && (selected.size() != 3 || selected.stream().anyMatch(r -> r.closingBalance() == null))) return null;
        return new Result(tables.table(selected, "yearMonth", "연월", "openingBalance", "전월 이월금", "incomeTotal", "수입",
            "expenseTotal", "지출", "closingBalance", "이월금"), new Period(first.toString(), end.toString()),
            selected.stream().anyMatch(r -> !r.closed()), "UI-21", "P-03".equals(query.function())
                ? List.of("마지막 마감 다음 달부터 3개월의 기존 자금수지표 forward 결과입니다.") : List.of());
    }
}
