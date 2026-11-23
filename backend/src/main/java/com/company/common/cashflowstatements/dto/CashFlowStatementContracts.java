package com.company.common.cashflowstatements.dto;

import java.math.BigDecimal;
import java.util.List;

public final class CashFlowStatementContracts {
    private CashFlowStatementContracts() {}
    public record Filter(Integer year) {}
    public record Month(String yearMonth, boolean closed, BigDecimal openingBalance, BigDecimal closingBalance,
        BigDecimal cashSales, BigDecimal miscIncome, BigDecimal loan, BigDecimal incomeTotal,
        BigDecimal regularPayroll, BigDecimal contractFreelancePayroll, BigDecimal outsourcing,
        BigDecimal unpaidPayroll, BigDecimal overhead, BigDecimal corporateTaxPayment, BigDecimal expenseTotal) {}
    public record Warning(String yearMonth, BigDecimal expectedBalance, BigDecimal minimumCash) {}
    public record Column(String label, Boolean actual, boolean warning) {}
    public record NumericRow(String label, String style, List<BigDecimal> values) {}
    public record NumericTable(List<Column> columns, List<NumericRow> rows) {}
    public record Result(int year, BigDecimal minimumCash, List<Month> months,
        List<Warning> warnings, NumericTable numericTable) {}
}
