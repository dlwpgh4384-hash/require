package com.company.common.projectissues.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProjectIssueContracts {
    private ProjectIssueContracts() {}

    /** modifiedAt은 수정 때만: 처음 받은 값을 그대로 보내고, 그 사이 다른 사람이 고쳤으면 409(공통 00-1). */
    public record Input(String projectId, String title, LocalDate dueDate,
        String importanceCode, String statusCode, String actionText, Instant modifiedAt) {
        public Input(String projectId, String title, LocalDate dueDate,
            String importanceCode, String statusCode, String actionText) {
            this(projectId, title, dueDate, importanceCode, statusCode, actionText, null);
        }
    }
    /**
     * 조건. 아무것도 주지 않으면 열림·조치 중. all=true면 상태 상관없이 전부(종료는 맨 뒤).
     * 프로젝트를 고르지 않은 모아 보기는 종료·실주 프로젝트의 이슈를 빼고 보인다.
     */
    public record Filter(String statusCode, Boolean overdue, String importanceCode, String projectId, Boolean all) {}
    public record Query(Filter filter, LocalDate today) {}
    public record IssueList(List<Issue> items, long openCount) {}

    /**
     * createdByName·updatedByName은 등록한·고친 사람 이름(사용자 표, 없으면 계정 ID).
     * modifiedAt은 동시 수정 확인 값(고친 시각, 없으면 등록 시각).
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Issue {
        public String issueId, projectId, projectName, title, importanceCode, statusCode, actionText;
        public String assigneeId, assigneeName, createdBy, updatedBy;
        public String createdByName, updatedByName;
        public LocalDate dueDate;
        public Instant createdAt, updatedAt, modifiedAt;
        public int overdueDays;
    }

    public static class ProjectState {
        public String projectId, statusCode;
        public LocalDate endDate;
    }

    public static class Write {
        public Long issueId;
        public final Input input;
        public final String actor;
        public Write(Long issueId, Input input, String actor) {
            this.issueId = issueId;
            this.input = input;
            this.actor = actor;
        }
    }
}
