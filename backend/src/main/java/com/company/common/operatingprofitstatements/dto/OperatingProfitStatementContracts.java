package com.company.common.operatingprofitstatements.dto;

import java.math.BigDecimal;
import java.util.List;

public final class OperatingProfitStatementContracts {
    private OperatingProfitStatementContracts() {}
    public record Filter(Integer year, String baseMonth) {}
    public record Metric(BigDecimal amount, BigDecimal previousYearChangePercent, BigDecimal previousMonthChangePercent) {}
    public record Summary(Metric cumulativeRevenue, Metric cumulativeGrossProfit, Metric cumulativeOperatingProfit,
        BigDecimal operatingProfitRate, Metric baseMonthOperatingProfit) {}
    public record Cell(BigDecimal value, String format) {}
    public record Column(String label, boolean provisional, boolean working) {
        public String heading() { return label + (working ? " 작업중" : "") + (provisional ? " 잠정" : ""); }
    }
    public record Line(String label, String style, boolean isCost, List<Cell> values) {}
    public record NumericTable(List<Column> columns, List<Line> rows) {}
    public record Result(int year, String baseMonth, boolean provisional, String provisionalNotice,
        Summary summary, NumericTable numericTable) {}
    public record ExcelFile(byte[] bytes, String fileName, int count) {}
}
