package com.company.common.projects.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProjectProgressContracts {
    private ProjectProgressContracts() {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Memo(String memoId, String projectId, String workDone, String nextWork,
        String requests, Integer actualProgress, Instant createdAt, String createdBy,
        Instant updatedAt, String updatedBy) {}
    public record MemoList(List<Memo> items) {}
    public record MemoInput(String workDone, String nextWork, String requests, Integer actualProgress) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Step(String stepId, String projectId, String stepName, BigDecimal weight,
        LocalDate plannedStartDate, LocalDate plannedEndDate, LocalDate completedDate) {}
    public record StepList(List<Step> items) {}
    public record StepDate(String stepId, LocalDate plannedStartDate, LocalDate plannedEndDate) {}
    public record StepDatesInput(List<StepDate> items) {}
}
