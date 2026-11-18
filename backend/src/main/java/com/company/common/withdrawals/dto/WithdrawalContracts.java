package com.company.common.withdrawals.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class WithdrawalContracts {
    private WithdrawalContracts() {}
    public record Filter(Integer year, String period, String extensionPossibilityCode, String jobCode) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record NextAssignment(String opportunityName, int recommendationCount, boolean hasTentativeAssignment) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Row(String assignmentId, String developerId, String name, String projectName,
        String jobName, LocalDate endDate, int remainingDays, boolean withdrawalCompleted,
        int extensionCount, List<String> earlyWithdrawalHistory, String extensionPossibilityCode,
        String extensionPossibilityName, NextAssignment nextAssignment, Instant modifiedAt) {}
    public record Banner(int unassignedCount, int matchedCount) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record WithdrawalList(List<Row> items, int totalCount, Banner banner) {}
    public record Recommendation(int needSn, String opportunityId, String opportunityName,
        String jobName, String gradeName, String startMonth, BigDecimal score,
        List<String> reasons, int waitingDays, String waitingLabel) {}
    public record Shortage(String month, String jobName, BigDecimal shortageCount) {}
    public record Recommendations(List<Recommendation> items, List<Shortage> shortages) {}
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
