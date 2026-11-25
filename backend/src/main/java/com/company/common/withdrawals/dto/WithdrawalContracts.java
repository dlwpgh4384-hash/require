package com.company.common.withdrawals.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class WithdrawalContracts {
    private WithdrawalContracts() {}
    public record Filter(Integer year, String period, String extensionPossibilityCode, String jobCode) {}
    /**
     * 다음 배정 칸. recommendationTarget은 식-79 추천 대상(3개월 안·높음 아님·가배정 없음)인지,
     * note는 시안의 작은 글자(「연장 가능성 높음」·「3개월 뒤 철수」·「맞는 자리 없음」, 없으면 null).
     * nextProjectName·nextStartMonth(YYYY-MM)는 철수일 뒤 확정된 다음 투입(가장 가까운 것, 없으면 null),
     * assignmentCandidate는 가배정이 수주로 넘어가 투입 배정 「배정 후보」(CNVT)에 있는지. 둘 다 「다음 배정 있음」으로 센다.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record NextAssignment(String opportunityName, int recommendationCount, boolean hasTentativeAssignment,
        boolean recommendationTarget, String note, String nextProjectName, String nextStartMonth,
        boolean assignmentCandidate) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Row(String assignmentId, String developerId, String name, String projectName,
        String jobName, LocalDate endDate, int remainingDays, boolean withdrawalCompleted,
        int extensionCount, List<String> earlyWithdrawalHistory, String extensionPossibilityCode,
        String extensionPossibilityName, NextAssignment nextAssignment, Instant modifiedAt,
        String projectId, String managementNumber) {}
    /** 식-82: soonCount=A(3개월 안 철수 예정 배정 수), unassignedCount=B, matchedCount=C. */
    public record Banner(int soonCount, int unassignedCount, int matchedCount) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record WithdrawalList(List<Row> items, int totalCount, Banner banner) {}
    /** startMonth는 자리 시작월, tentative는 이 사람이 이미 이 기회에 가배정되어 있는지(시안 [가배정됨]). */
    public record Recommendation(int needSn, String opportunityId, String opportunityName,
        String jobName, String gradeName, String startMonth, BigDecimal score,
        List<String> reasons, int waitingDays, String waitingLabel, int remainingCount,
        String customerName, String stageName, boolean tentative) {}
    public record Shortage(String month, String jobName, BigDecimal shortageCount) {}
    /** 추천 창 머리(이름·직무·등급·프로젝트·철수일, 기술·연장 가능성·대기 시작, 지금 가배정). */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RecommendationTarget(String assignmentId, String name, String jobName, String gradeName,
        String projectName, LocalDate endDate, List<String> skills, String extensionPossibilityName,
        LocalDate waitingStartDate, String tentativeOpportunityId, String tentativeOpportunityName,
        String tentativeStartMonth) {}
    public record Recommendations(List<Recommendation> items, List<Shortage> shortages, RecommendationTarget target) {}
    public record ExtensionResult(String assignmentId, String extensionPossibilityCode, Instant modifiedAt) {}
    public record TentativeAssignment(String assignmentId, String developerId, String opportunityId,
        String opportunityName, int needSn, String startMonth) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TentativeResult(TentativeAssignment assignment) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SharedRow(String assignmentId, String developerId, String name,
        String extensionPossibilityCode, String extensionPossibilityName, TentativeAssignment tentativeAssignment) {}
    public record SharedList(List<SharedRow> items) {}
}
