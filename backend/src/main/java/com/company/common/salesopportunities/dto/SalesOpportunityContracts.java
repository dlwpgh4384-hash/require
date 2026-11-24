package com.company.common.salesopportunities.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SalesOpportunityContracts {
    private SalesOpportunityContracts() {}

    public record Need(Integer needSn, String jobCode, String gradeCode, int requiredCount,
        String startMonth, int months, BigDecimal monthlyMm, BigDecimal estimatedUnitPrice,
        List<String> tentativeDeveloperIds) {}
    public record Input(String opportunityName, String customerId, String salesPartId,
        String stageCode, BigDecimal winningProbability, BigDecimal expectedAmount,
        String contractTypeCode, String startMonth, int expectedMonths, String workplace,
        String nextAction, LocalDate nextActionDueDate, String memo, List<Need> needs) {}
    public record ApprovalHistory(String actionCode, Instant actedAt, String reason) {}
    public record Opportunity(String opportunityId, String opportunityName, String customerId,
        String customerName, String salesPartId, String stageCode, BigDecimal winningProbability,
        BigDecimal expectedAmount, String contractTypeCode, String startMonth, int expectedMonths,
        String workplace, String nextAction, LocalDate nextActionDueDate, String memo,
        List<Need> needs, List<String> requiredJobs, boolean stale, String approvalStatusCode,
        Instant modifiedAt, String projectId, LocalDate resultDate, String opportunityStatusCode,
        String lossReasonCode, List<ApprovalHistory> approvalHistory, String lossMemo) {}
    public record Tentative(Instant modifiedAt, String developerId, int needSn, String startMonth) {}
    public record Warning(int needSn, String developerId, String message) {}
    public record Warnings(List<Warning> warnings) {}
    public record Win(Instant modifiedAt, BigDecimal contractAmount, String contractTypeCode,
        LocalDate contractStartDate, LocalDate contractEndDate, String projectStatusCode) {}
}
