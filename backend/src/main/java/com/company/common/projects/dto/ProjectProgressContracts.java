package com.company.common.projects.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProjectProgressContracts {
    private ProjectProgressContracts() {}

    /**
     * 주간 메모. writtenDate는 작성일(WRT_DT, 한국 날짜), modifiedAt은 동시 수정 확인 값(고친 시각, 없으면 등록 시각).
     * 수정 때 modifiedAt을 그대로 돌려보내고, 그 사이 다른 사람이 고쳤으면 409.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Memo(String memoId, String projectId, String workDone, String nextWork,
        String requests, Integer actualProgress, Instant createdAt, String createdBy,
        Instant updatedAt, String updatedBy, LocalDate writtenDate, Instant modifiedAt) {}
    public record MemoList(List<Memo> items) {}
    public record MemoInput(String workDone, String nextWork, String requests, Integer actualProgress) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Step(String stepId, String projectId, String stepName, BigDecimal weight,
        LocalDate plannedStartDate, LocalDate plannedEndDate, LocalDate completedDate) {}
    /** 단계 목록. modifiedAt은 단계 전체의 동시 수정 확인 값(단계들 중 가장 늦은 고친·등록 시각). 단계가 없으면 null. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StepList(List<Step> items, Instant modifiedAt) {}
    public record StepDate(String stepId, LocalDate plannedStartDate, LocalDate plannedEndDate) {}
    public record StepDatesInput(List<StepDate> items, Instant modifiedAt) {}
}
