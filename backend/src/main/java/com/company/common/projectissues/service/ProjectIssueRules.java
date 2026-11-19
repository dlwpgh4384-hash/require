package com.company.common.projectissues.service;

import com.company.common.clients.service.ClientRules;
import com.company.common.projectissues.dto.ProjectIssueContracts.Input;
import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

public final class ProjectIssueRules {
    private ProjectIssueRules() {}

    public static Input input(JsonNode body) { return input(body, false); }

    /** update=true(이슈 수정)이면 modifiedAt을 꼭 받는다(공통 00-1 동시 수정 확인). */
    public static Input input(JsonNode body, boolean update) {
        ClientRules.object(body, update
            ? Set.of("projectId", "title", "dueDate", "importanceCode", "statusCode", "actionText", "modifiedAt")
            : Set.of("projectId", "title", "dueDate", "importanceCode", "statusCode", "actionText"));
        String projectId = ClientRules.text(body, "projectId", 20, true);
        String title = ClientRules.text(body, "title", 100, true);
        String date = ClientRules.text(body, "dueDate", 10, true);
        LocalDate due;
        try {
            if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid("dueDate");
            due = LocalDate.parse(date);
        } catch (DateTimeParseException failure) {
            throw invalid("dueDate");
        }
        String action = body.hasNonNull("actionText") ? ClientRules.text(body, "actionText", 500, false) : null;
        String importance = ClientRules.text(body, "importanceCode", 10, true);
        String status = ClientRules.text(body, "statusCode", 10, true);
        java.time.Instant version = null;
        if (update) {
            if (!body.has("modifiedAt")) throw CodeFailure.invalid("modifiedAt", "수정 시각을 제출해 주세요.");
            try {
                if (!body.get("modifiedAt").isTextual()) throw new IllegalArgumentException();
                version = java.time.Instant.parse(body.get("modifiedAt").textValue());
            } catch (RuntimeException failure) {
                throw CodeFailure.invalid("modifiedAt", "수정 시각을 확인해 주세요.");
            }
        }
        return new Input(projectId, title, due, importance, status, action, version);
    }

    public static long issueId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,9}")) throw invalid("issueId");
        return Long.parseLong(id);
    }

    public static CodeFailure invalid(String field) {
        return CodeFailure.invalid(field, "입력 값을 확인해 주세요.");
    }
}
