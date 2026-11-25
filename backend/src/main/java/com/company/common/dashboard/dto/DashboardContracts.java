package com.company.common.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class DashboardContracts {
    private DashboardContracts() {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Summary(BigDecimal revenue, BigDecimal revenuePreviousComparison, BigDecimal revenueTargetRate,
        BigDecimal operatingProfit, BigDecimal operatingProfitRate, BigDecimal operatingProfitPreviousComparison,
        BigDecimal cumulativeOperatingProfit, BigDecimal cumulativeAchievementRate, BigDecimal utilizationRate,
        BigDecimal utilizationTargetRate, BigDecimal idlePeople, BigDecimal unpaidAmount, int overdueCount,
        BigDecimal overdueAmount) {}
    public record Todo(String title, int count, String description, String targetScreenId, boolean urgent) {}
    public record Closing(int completedCount, int totalCount, List<String> remainingItems) {}
    public record ProjectAlert(String projectId, String projectName, String alertName, String mainCause) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TrendMonth(String month, BigDecimal revenue, BigDecimal cost, BigDecimal targetRevenue) {}
    public record ManpowerMonth(String month, BigDecimal supply, BigDecimal demand, BigDecimal difference, boolean shortage) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SalesPart(String partId, String partName, BigDecimal revenue, BigDecimal profitRate) {}
    public record Opportunity(String opportunityName, String startMonth) {}
    public record Pipeline(int activeCount, BigDecimal weightedExpectedRevenue, List<Opportunity> upcoming) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CashMonth(String month, BigDecimal monthEndBalance, boolean shortage, String warning) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Result(String selectedMonth, LocalDate today, String closingState, String lastClosedMonth,
        boolean provisional, String mixedBasisNotice, Summary summary, List<Todo> todos, Closing closing,
        List<ProjectAlert> projectAlerts, List<TrendMonth> trend, List<ManpowerMonth> manpower,
        List<SalesPart> salesParts, Pipeline pipeline, List<CashMonth> cash) {}
    public record ExcelFile(byte[] bytes, String fileName, int count) {}
}
