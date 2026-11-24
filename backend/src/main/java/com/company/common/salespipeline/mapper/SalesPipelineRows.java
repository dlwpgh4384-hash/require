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
    /** 진행 기회의 필요 직무(직무마다 한 줄, 인원 합, 첫 필요 인력 줄 순서). */
    public record RequiredJob(String opportunityId, String jobCode, String jobName, int headcount) {}
    /** 예측 6개월의 달 하나(DB 달 YYYYMM, 첫날·말일). */
    public record MonthWindow(String month, LocalDate firstDay, LocalDate lastDay) {}
    public record MonthRevenue(String month, BigDecimal projectRevenue, BigDecimal weightedOpportunityRevenue,
        BigDecimal opportunityRevenue) {}
    /** 진행 기회 하나의 예측 달 값(식-92 반올림 전): 예상 금액 ÷ 예상 기간 × 수주 확률. 기간 안의 달만 한 줄. */
    public record OpportunityMonthRevenue(String opportunityId, String opportunityName, BigDecimal winningProbability,
        String month, BigDecimal weightedRevenue) {}
    public record MonthSupply(String month, String jobCode, String developerId, BigDecimal confirmedMm) {}
    public record MonthExtension(String month, String jobCode, String developerId, BigDecimal monthlyMm,
        String extensionPossibilityCode) {}
    public record MonthOpportunityDemand(String month, String jobCode, BigDecimal requiredCount,
        BigDecimal monthlyMm, BigDecimal winningProbability) {}
    public record MonthProjectDemand(String month, String jobCode, BigDecimal plannedMm, BigDecimal confirmedMm,
        int remainingMonths) {}
    public record CandidateCount(String jobCode, int candidateCount) {}
}
