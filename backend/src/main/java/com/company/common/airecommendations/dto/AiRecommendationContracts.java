package com.company.common.airecommendations.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AiRecommendationContracts {
    private AiRecommendationContracts() {}

    public record Input(String requestId, String targetType, String targetId, String jobCode,
        String startMonth, String gradeCode, Integer needSn) {}
    public record Condition(String targetType, String targetId, String jobCode,
        String startMonth, String gradeCode, Integer needSn) {}
    public record ScoreItem(String name, BigDecimal score, BigDecimal maximumScore) {}
    public record CostBasis(BigDecimal cost, BigDecimal price) {}
    public record Card(String developerId, String developerName, String managementNumber,
        String jobName, String gradeName, String employeeTypeName, BigDecimal totalScore,
        Map<String, ScoreItem> scores, String reason, String otherTentativeOpportunityName,
        boolean assignmentCandidate,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) CostBasis costBasis) {}
    public record Result(String recommendationId, Condition condition, List<Card> items) {}
    public record AssignmentInput(String developerId, String assignmentId) {}
    public record AssignmentResult(String recommendationId, String developerId,
        String assignmentId, String userActCode) {}
    public record ExclusionInput(String developerId, String reasonCode) {}
    public record ExclusionResult(String recommendationId, String developerId, boolean excluded,
        String reasonCode, String actedBy, Instant actedAt) {}
    public record TentativeInput(String developerId, Integer needSn) {}
    public record TentativeResult(
        com.company.common.withdrawals.dto.WithdrawalContracts.TentativeAssignment assignment,
        String releasedOpportunityName, int overCapacityCount, String message) {}
}
