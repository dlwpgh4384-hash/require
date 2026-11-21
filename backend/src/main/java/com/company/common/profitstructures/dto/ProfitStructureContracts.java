package com.company.common.profitstructures.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ProfitStructureContracts {
    private ProfitStructureContracts() {}
    public record Filter(Integer year, String periodUnit, String baseMonth) {}
    public record Summary(BigDecimal revenue, BigDecimal grossProfit, BigDecimal grossProfitRate,
        BigDecimal operatingProfit, BigDecimal operatingProfitRate, BigDecimal idleCostShare) {}
    /** previousPeriod·previousPeriodLabel은 연 누계에서 null(시안: 연 누계는 전년 같은 기간만 비교). */
    public record Comparison(Summary previousPeriod, String previousPeriodLabel, Summary previousYear,
        boolean previousYearVisible, String shareDifferenceUnit) {}
    public record Waterfall(BigDecimal revenue, BigDecimal costOfSales, BigDecimal grossProfit,
        BigDecimal sellingAdministrativeExpense, BigDecimal previousAdjustment, BigDecimal operatingProfit,
        BigDecimal nonOperatingProfitLoss, BigDecimal monthlyCorporateTax, BigDecimal netProfit) {}
    public record Trend(String period, BigDecimal revenue, BigDecimal costOfSales, BigDecimal grossProfit,
        BigDecimal operatingProfit, BigDecimal allocatedCost, BigDecimal idleCost, BigDecimal directExpense,
        BigDecimal referenceRevenue) {}
    public record CompositionRow(String name, BigDecimal revenue, BigDecimal share, BigDecimal profitRate) {}
    public record Composition(List<CompositionRow> contractTypes, List<CompositionRow> topClients,
        List<CompositionRow> projectSalesParts, String salesPartNotice) {}
    public record PeriodRow(String period, Summary summary, Waterfall waterfall, Trend trend,
        Composition composition, boolean provisional) {}
    /** 숫자로 보기 표(시안: 줄 = 항목, 열 = 기간). 엑셀도 이 표 그대로 쓴다. */
    public record NumericColumn(String label, boolean provisional) {}
    /** style: bold(굵은 줄)·indent(└ 아래 줄)·group(매출 구성 머리 줄)·plain, format: money·percent. */
    public record NumericLine(String label, String style, String format, List<BigDecimal> values) {}
    public record NumericTable(List<NumericColumn> columns, List<NumericLine> rows) {}
    public record Result(String periodStart, String periodEnd, String periodName, Summary summary, Comparison comparison,
        Waterfall waterfall, List<Trend> trends, Composition composition, boolean compositionVisible,
        boolean referenceRevenueVisible, boolean provisional, String provisionalNotice, List<PeriodRow> numericRows,
        NumericTable numericTable) {}
    public record ExcelFile(byte[] bytes, String fileName, int count) {}
}
