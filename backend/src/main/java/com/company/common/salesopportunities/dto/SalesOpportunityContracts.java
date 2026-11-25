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
        /** 근무지 지역(선택, 2026-11-25): 고른 마지막 단계의 행정구역 코드. */
        String workAreaCode,
        String nextAction, LocalDate nextActionDueDate, String memo, List<Need> needs) {}
    public record ApprovalHistory(String actionCode, Instant actedAt, String reason) {}
    public record Opportunity(String opportunityId, String opportunityName, String customerId,
        String customerName, String salesPartId, String stageCode, BigDecimal winningProbability,
        BigDecimal expectedAmount, String contractTypeCode, String startMonth, int expectedMonths,
        String workplace,
        /** 근무지 지역 코드와 보여 주기 글자(시·도 줄임 이름 + 시·군·구 + 동, 예: 「서울 중구 을지로동」). 없으면 null. */
        String workAreaCode, String workAreaName,
        String nextAction, LocalDate nextActionDueDate, String memo,
        List<Need> needs, List<String> requiredJobs, boolean stale, String approvalStatusCode,
        Instant modifiedAt, String projectId, LocalDate resultDate, String opportunityStatusCode,
        String lossReasonCode, List<ApprovalHistory> approvalHistory, String lossMemo,
        /** 저장 때 계산해 둔 예상 이익률(EXPCT_PRFT_RT). 승인 띠는 이 값을 보인다. */
        BigDecimal expectedProfitRate,
        /** 승인한 때의 예상 이익률(APRV_PRFT_RT). 승인 전이면 null. */
        BigDecimal approvedProfitRate,
        /** 이번 달 견적 승인 기준 이익률(QUOTE_APRV_RT). 기준값이 없으면 null. */
        BigDecimal approvalRate) {}
    public record Tentative(Instant modifiedAt, String developerId, int needSn, String startMonth) {}
    public record Warning(Integer needSn, String developerId, String message) {}
    /** 저장 전 줄(새 기회·새 줄)의 가배정 점검. opportunityId는 고치는 중인 기회(없으면 null). */
    public record DeveloperCheck(String opportunityId, String developerId, String startMonth) {}
    public record Warnings(List<Warning> warnings) {}
    public record Win(Instant modifiedAt, BigDecimal contractAmount, String contractTypeCode,
        LocalDate contractStartDate, LocalDate contractEndDate, String projectStatusCode) {}
}
