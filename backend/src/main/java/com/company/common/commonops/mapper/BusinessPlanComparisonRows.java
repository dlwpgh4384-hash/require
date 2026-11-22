package com.company.common.commonops.mapper;

import java.math.BigDecimal;

public final class BusinessPlanComparisonRows {
    private BusinessPlanComparisonRows() {}
    public record Target(String yearMonth, BigDecimal revenueTarget, BigDecimal operatingProfitTarget) {}
}
