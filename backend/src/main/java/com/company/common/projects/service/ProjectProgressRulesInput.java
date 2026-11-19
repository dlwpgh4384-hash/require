package com.company.common.projects.service;

import com.company.common.clients.service.ClientRules;
import com.company.common.projects.dto.ProjectProgressContracts.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class ProjectProgressRulesInput {
    private ProjectProgressRulesInput() {}

    public static String projectId(String id) {
        ProjectRules.id(id);
        if (!id.matches("[A-Za-z0-9_-]{1,20}")) throw ProjectRules.invalid("projectId");
        return id;
    }
    public static int sequence(String value, String field, int digits) {
        if (value == null || !value.matches("[1-9][0-9]{0," + (digits - 1) + "}"))
            throw ProjectRules.invalid(field);
        return Integer.parseInt(value);
    }
    private static String text(JsonNode body, String field) {
        var value = body.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() || value.textValue().codePointCount(0, value.textValue().length()) > 200)
            throw ProjectRules.invalid(field);
        String trimmed = value.textValue().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
    public static MemoInput memo(JsonNode body) { return memo(body, false); }
    /** update=true(메모 수정)이면 본문에 modifiedAt도 받는다(값은 modifiedAt(body)로 읽는다). */
    public static MemoInput memo(JsonNode body, boolean update) {
        ClientRules.object(body, update ? Set.of("workDone", "nextWork", "requests", "actualProgress", "modifiedAt")
            : Set.of("workDone", "nextWork", "requests", "actualProgress"));
        var progress = body.get("actualProgress");
        Integer rate = null;
        if (progress != null && !progress.isNull()) {
            if (!progress.isIntegralNumber() || !progress.canConvertToInt()
                || progress.intValue() < 0 || progress.intValue() > 100)
                throw ProjectRules.invalid("actualProgress");
            rate = progress.intValue();
        }
        return new MemoInput(text(body, "workDone"), text(body, "nextWork"), text(body, "requests"), rate);
    }
    public static void memoType(MemoInput input, String type) {
        if ("MO".equals(type) || "DP".equals(type)) {
            if (input.actualProgress() != null) throw ProjectRules.invalid("actualProgress");
            if (blank(input.workDone()) && blank(input.nextWork()) && blank(input.requests()))
                throw com.company.common.support.service.CodeFailure.invalid("workDone", "한 일·할 일·요청 사항 중 하나를 적어 주세요.");
        }
    }
    private static boolean blank(String text) { return text == null || text.isBlank(); }

    public static StepDatesInput dates(JsonNode body) {
        ClientRules.object(body, Set.of("items", "modifiedAt"));
        var version = modifiedAt(body);
        var items = body.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) throw ProjectRules.invalid("items");
        var result = new ArrayList<StepDate>();
        var seen = new HashSet<String>();
        for (var row : items) {
            ClientRules.object(row, Set.of("stepId", "plannedStartDate", "plannedEndDate"));
            String id = ProjectRules.text(row, "stepId", 2, true);
            sequence(id, "stepId", 2);
            if (!seen.add(id)) throw ProjectRules.invalid("stepId");
            var start = ProjectRules.date(row, "plannedStartDate");
            var end = ProjectRules.date(row, "plannedEndDate");
            if (end.isBefore(start)) throw ProjectRules.invalid("plannedEndDate");
            result.add(new StepDate(id, start, end));
        }
        return new StepDatesInput(java.util.List.copyOf(result), version);
    }

    /** 동시 수정 확인 값(공통 00-1): 꼭 보내야 하고, 처음 받은 modifiedAt 그대로(null 가능). */
    public static java.time.Instant modifiedAt(JsonNode body) {
        if (!body.has("modifiedAt"))
            throw com.company.common.support.service.CodeFailure.invalid("modifiedAt", "수정 시각을 제출해 주세요.");
        var value = body.get("modifiedAt");
        if (value.isNull()) return null;
        try {
            if (!value.isTextual()) throw new IllegalArgumentException();
            return java.time.Instant.parse(value.textValue());
        } catch (RuntimeException failure) {
            throw com.company.common.support.service.CodeFailure.invalid("modifiedAt", "수정 시각을 확인해 주세요.");
        }
    }
}
