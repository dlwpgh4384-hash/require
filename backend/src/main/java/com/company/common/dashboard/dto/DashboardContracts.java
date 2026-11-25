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
        BigDecimal utilizationTargetRate, int idlePeople, BigDecimal unpaidAmount, int unpaidCount, int overdueCount,
        BigDecimal overdueAmount) {}
    /**
     * Todo row (mockup warns). tone: bad(red) or warn(orange). visible: false for empty rows the mockup filters out;
     * alwaysVisible: the withdrawal row shows even at zero. amount: the raw money behind the description, or null.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Todo(String key, String title, int count, String description, BigDecimal amount,
        String targetScreenId, String targetScreenName, String tone, boolean urgent, boolean alwaysVisible,
        boolean visible) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ClosingItem(String name, String status, String description, String targetScreenId) {}
    public record Closing(int completedCount, int totalCount, List<ClosingItem> remainingItems) {}
    public record AlertBadge(String name, String tone) {}
    /** marginLabel: "예상 최종 마진율" for turnkey, otherwise "월간 마진율"; mainCause is null without a cause amount. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProjectAlert(String projectId, String projectName, String contractType, String marginLabel,
        BigDecimal marginRate, BigDecimal budgetBurnRate, List<String> alerts, List<AlertBadge> alertBadges,
        String alertName, String mainCause, BigDecimal mainCauseAmount) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TrendMonth(String month, BigDecimal revenue, BigDecimal cost, BigDecimal targetRevenue) {}
    public record ManpowerMonth(String month, BigDecimal supply, BigDecimal demand, BigDecimal difference, boolean shortage) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SalesPart(String partId, String partName, BigDecimal revenue, BigDecimal profitRate) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Opportunity(String opportunityId, String opportunityName, String startMonth, String stageName,
        BigDecimal probability) {}
    public record Pipeline(int activeCount, BigDecimal weightedExpectedRevenue, List<Opportunity> upcoming) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CashMonth(String month, BigDecimal monthEndBalance, boolean shortage, String warning) {}
    /**
     * asOfText: "오늘 yyyy-MM-dd · n월까지 마감". provisionalNotice: shown after the 잠정 badge when the selected
     * month is not closed. mixedBasisNotice: only for a past month. projectAlertMonth: the yyyy-MM month the
     * project alerts were read for (the project detail opens that same month).
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Result(String selectedMonth, LocalDate today, String closingState, String lastClosedMonth,
        boolean provisional, String asOfText, String provisionalNotice, String mixedBasisNotice, Summary summary,
        List<Todo> todos, Closing closing, List<ProjectAlert> projectAlerts, String projectAlertMonth, List<TrendMonth> trend,
        List<ManpowerMonth> manpower, List<SalesPart> salesParts, Pipeline pipeline, List<CashMonth> cash,
        BigDecimal minimumCash) {}
    public record ExcelFile(byte[] bytes, String fileName, int count) {}
}
