package com.company.common.commonops.mapper;

import java.math.BigDecimal;

public final class BusinessPlanComparisonRows {
    private BusinessPlanComparisonRows() {}
    public record Target(String yearMonth, BigDecimal revenueTarget, BigDecimal operatingProfitTarget) {}
    /** B.10 한 달 목표(PART_ID='ALL'). */
    public record TargetWrite(String yearMonth, BigDecimal revenueTarget, BigDecimal operatingProfitTarget,
        long uploadId, String userId) {}
}
