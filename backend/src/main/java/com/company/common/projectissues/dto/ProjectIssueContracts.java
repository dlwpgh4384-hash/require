package com.company.common.projectissues.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProjectIssueContracts {
    private ProjectIssueContracts() {}

    public record Input(String projectId, String title, LocalDate dueDate,
        String importanceCode, String statusCode, String actionText) {}
    public record Filter(String statusCode, Boolean overdue, String importanceCode, String projectId) {}
    public record Query(Filter filter, LocalDate today) {}
    public record IssueList(List<Issue> items, long openCount) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class Issue {
        public String issueId, projectId, projectName, title, importanceCode, statusCode, actionText;
        public String assigneeId, assigneeName, createdBy, updatedBy;
        public LocalDate dueDate;
        public Instant createdAt, updatedAt;
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
