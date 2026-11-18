package com.company.common.assignments.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AssignmentContracts {
    private AssignmentContracts() {}
    public record Items<T>(List<T> items) {}
    public record Filter(Integer year, String projectId, String jobCode, String warning, String keyword) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Assignment(String assignmentId, String projectId, String projectName, String developerId,
        String name, String managementNumber, String jobCode, String jobName, LocalDate startDate,
        LocalDate endDate, BigDecimal billingPrice, BigDecimal monthlyMm, BigDecimal yearMm,
        String extensionPossibilityCode, String extensionPossibilityName, String inputTypeCode,
        String inputTypeName, int extensionCount, List<String> warnings, BigDecimal monthlyMmTotal,
        int historyCount, @JsonInclude(JsonInclude.Include.ALWAYS) Instant modifiedAt) {}
    public record History(String historyId, String assignmentId, LocalDate effectiveDate, String changeTypeCode,
        String changeTypeName, String beforeValue, String afterValue, String reason, String memo,
        String changedBy, Instant changedAt, String retroactiveYn, String correctedYn, Instant modifiedAt) {}
    public record Candidate(String candidateId, String projectId, String projectName, String developerId,
        String name, String managementNumber, String jobCode, String jobName, LocalDate startDate, LocalDate endDate,
        @JsonInclude(JsonInclude.Include.ALWAYS) Instant modifiedAt) {}
    public record Removed(String candidateId) {}
    public record ContractDeveloper(String developerId, String name, String jobName, LocalDate endDate, BigDecimal billingPrice,
        List<Assignment> overlappingAssignments) {}
    public record Version(String assignmentId, Instant modifiedAt) {}
}
