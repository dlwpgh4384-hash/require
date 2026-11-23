package com.company.common.commonops.dto;

import java.math.BigDecimal;
import java.util.List;

public final class BusinessPlanComparisonContracts {
    private BusinessPlanComparisonContracts() {}
    public record Filter(Integer year, String baseMonth) {}
    public record Month(String yearMonth, BigDecimal revenueTarget, BigDecimal revenueActual,
        BigDecimal operatingProfitTarget, BigDecimal operatingProfitActual,
        BigDecimal achievementRate, BigDecimal profitRateDifference) {}
    public record Summary(BigDecimal cumulativeRevenueAchievementRate, BigDecimal cumulativeRevenueActual,
        BigDecimal cumulativeRevenueTarget, BigDecimal cumulativeOperatingProfitAchievementRate,
        BigDecimal cumulativeOperatingProfitActual, BigDecimal cumulativeOperatingProfitTarget,
        BigDecimal baseMonthRevenueAchievementRate, BigDecimal baseMonthRevenueTarget) {}
    public record Result(int year, String baseMonth, boolean baseMonthClosed, boolean hasTargets,
        Summary summary, List<Month> months) {}
}
