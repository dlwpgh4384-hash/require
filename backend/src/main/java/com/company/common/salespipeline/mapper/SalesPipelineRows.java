package com.company.common.salespipeline.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class SalesPipelineRows {
    private SalesPipelineRows() {}
    public record Opportunity(String opportunityId, String opportunityName, String customerId,
        String customerName, String stageCode, BigDecimal winningProbability, BigDecimal expectedAmount,
        String startMonth, String nextAction, LocalDate nextActionDueDate, String approvalStatusCode,
        Instant modifiedAt, BigDecimal manpower) {}
    public record Job(String jobCode, String jobName) {}
    public record Revenue(BigDecimal projectRevenue, BigDecimal weightedOpportunityRevenue,
        BigDecimal opportunityRevenue) {}
    public record MonthQuery(String jobCode, String month, LocalDate firstDay, LocalDate lastDay,
        LocalDate horizonStart) {}
}
