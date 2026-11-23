package com.company.common.cashflowstatements.service;

import com.company.common.cashflowstatements.dto.CashFlowStatementContracts.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

public final class CashFlowStatementProjection {
    private CashFlowStatementProjection() {}
    public static NumericTable table(List<Month> months, List<Warning> warnings) {
        var columns = new ArrayList<Column>();
        for (int i = 0; i < 12; i++) {
            var month = months.get(i);
            columns.add(new Column((i + 1) + "월", month.closed(),
                warnings.stream().anyMatch(w -> w.yearMonth().equals(month.yearMonth()))));
        }
        for (String label : List.of("상반기", "하반기", "연간")) columns.add(new Column(label, null, false));
        var rows = new ArrayList<NumericRow>();
        rows.add(row("전월이월금", "bold", months, Month::openingBalance, 1));
        rows.add(row("수입", "group", months, m -> null, 0));
        rows.add(row("현금매출", "indent", months, Month::cashSales, 0));
        rows.add(row("잡수익", "indent", months, Month::miscIncome, 0));
        rows.add(row("대여금", "indent", months, Month::loan, 0));
        rows.add(row("수입 합계", "bold", months, Month::incomeTotal, 0));
        rows.add(row("지출", "group", months, m -> null, 0));
        rows.add(row("인건비(정규직)", "indent", months, Month::regularPayroll, 0));
        rows.add(row("인건비(계약직·프리랜서)", "indent", months, Month::contractFreelancePayroll, 0));
        rows.add(row("용역비(외주)", "indent", months, Month::outsourcing, 0));
        rows.add(row("미지급 인건비", "indent", months, Month::unpaidPayroll, 0));
        rows.add(row("제경비", "indent", months, Month::overhead, 0));
        rows.add(row("법인세 납부", "indent", months, Month::corporateTaxPayment, 0));
        rows.add(row("지출 합계", "bold", months, Month::expenseTotal, 0));
        rows.add(row("이월금", "bold", months, Month::closingBalance, 2));
        return new NumericTable(columns, rows);
    }
    private static NumericRow row(String label, String style, List<Month> months,
        Function<Month, BigDecimal> amount, int endpoint) {
        var values = new ArrayList<BigDecimal>();
        months.forEach(m -> values.add(amount.apply(m)));
        for (int[] range : List.of(new int[]{0, 6}, new int[]{6, 12}, new int[]{0, 12})) {
            BigDecimal value = endpoint == 1 ? values.get(range[0])
                : endpoint == 2 ? values.get(range[1] - 1) : sum(values.subList(range[0], range[1]));
            values.add(value);
        }
        return new NumericRow(label, style, values);
    }
    public static BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().filter(Objects::nonNull).reduce(BigDecimal::add).orElse(null);
    }
}
