package com.company.common.profitstructures.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ProfitStructureContracts {
    private ProfitStructureContracts() {}
    public record Filter(Integer year, String periodUnit, String baseMonth) {}
    public record Summary(BigDecimal revenue, BigDecimal grossProfit, BigDecimal grossProfitRate,
        BigDecimal operatingProfit, BigDecimal operatingProfitRate, BigDecimal idleCostShare) {}
    public record Comparison(Summary previousPeriod, Summary previousYear, boolean previousYearVisible,
        String shareDifferenceUnit) {}
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
    public record Result(String periodStart, String periodEnd, Summary summary, Comparison comparison,
        Waterfall waterfall, List<Trend> trends, Composition composition, boolean compositionVisible,
        boolean referenceRevenueVisible, boolean provisional, String provisionalNotice, List<PeriodRow> numericRows) {}
    public record ExcelFile(byte[] bytes, String fileName, int count) {}
}
