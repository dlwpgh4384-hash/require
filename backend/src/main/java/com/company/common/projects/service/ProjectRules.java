package com.company.common.projects.service;

import com.company.common.clients.service.ClientRules;
import com.company.common.projects.dto.ProjectContracts.*;
import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class ProjectRules {
    private ProjectRules() {}
    private static final Set<String> MUTABLE = Set.of("projectName", "customerId", "startDate", "endDate",
        "workplace", "estimateAmount", "contractAmount", "budgetAmount", "teamExpenseAmount",
        "projectExpenseAmount", "feature");
    public static Input input(JsonNode body, boolean update) {
        var allowed = new HashSet<>(MUTABLE);
        if (!update) allowed.addAll(Set.of("contractTypeCode", "statusCode", "salesPartId"));
        ClientRules.object(body, allowed);
        LocalDate start = date(body, "startDate"), end = date(body, "endDate");
        if (end.isBefore(start)) throw invalid("endDate");
        String type = update ? null : text(body, "contractTypeCode", 10, true);
        String status = update ? null : text(body, "statusCode", 10, true);
        if (!update && !Set.of("ING", "PRE").contains(status)) throw invalid("statusCode");
        return new Input(text(body, "projectName", 100, true), text(body, "customerId", 20, false),
            type, start, end, update ? null : text(body, "salesPartId", 20, false),
            text(body, "workplace", 200, false), status, money(body, "estimateAmount", false),
            money(body, "contractAmount", false), money(body, "budgetAmount", false),
            money(body, "teamExpenseAmount", false), money(body, "projectExpenseAmount", false),
            text(body, "feature", 500, false));
    }
    public static String text(JsonNode body, String field, int limit, boolean required) {
        JsonNode n = body.get(field);
        if (n == null || n.isNull()) {
            if (required) throw invalid(field);
            return null;
        }
        return ClientRules.text(body, field, limit, required);
    }
    public static BigDecimal money(JsonNode body, String field, boolean required) {
        JsonNode n = body.get(field);
        if (n == null || n.isNull()) {
            if (required) throw invalid(field);
            return null;
        }
        if (!n.isNumber()) throw invalid(field);
        BigDecimal value = n.decimalValue();
        if (value.signum() < 0 || value.stripTrailingZeros().scale() > 0
            || value.compareTo(new BigDecimal("999999999999999")) > 0) throw invalid(field);
        return value;
    }
    public static LocalDate date(JsonNode body, String field) {
        try {
            String value = text(body, field, 10, true);
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid(field);
            LocalDate result = LocalDate.parse(value);
            if (result.getYear() < 1) throw invalid(field);
            return result;
        } catch (java.time.DateTimeException e) { throw invalid(field); }
    }
    public static YearMonth month(String value, String field) {
        try {
            if (value == null || !value.matches("[0-9]{4}-(0[1-9]|1[0-2])")) throw invalid(field);
            YearMonth result = YearMonth.parse(value);
            if (result.getYear() < 1) throw invalid(field);
            return result;
        } catch (java.time.DateTimeException e) { throw invalid(field); }
    }
    public static ExpenseInput expense(JsonNode body) {
        ClientRules.object(body, Set.of("yearMonth", "itemCode", "amount", "memo"));
        String month = text(body, "yearMonth", 7, true);
        month(month, "yearMonth");
        return new ExpenseInput(month, text(body, "itemCode", 10, true), money(body, "amount", true),
            text(body, "memo", 500, false));
    }
    public static PartInput part(JsonNode body) {
        ClientRules.object(body, Set.of("salesPartId", "effectiveMonth", "reason"));
        String month = text(body, "effectiveMonth", 7, true);
        month(month, "effectiveMonth");
        return new PartInput(text(body, "salesPartId", 20, true), month, text(body, "reason", 500, false));
    }
    public static LoseInput lose(JsonNode body) {
        ClientRules.object(body, Set.of("reasonCode", "memo"));
        return new LoseInput(text(body, "reasonCode", 10, true), text(body, "memo", 500, false));
    }
    public static ManpowerInput manpower(JsonNode body) {
        ClientRules.object(body, Set.of("jobs"));
        var jobs = body.get("jobs");
        if (jobs == null || !jobs.isArray()) throw invalid("jobs");
        var result = new ArrayList<JobInput>();
        var seen = new HashSet<String>();
        for (var job : jobs) {
            ClientRules.object(job, Set.of("jobCode", "plannedMm"));
            String code = text(job, "jobCode", 10, true);
            if (!seen.add(code)) throw invalid("jobs");
            var n = job.get("plannedMm");
            if (n != null && !n.isNull() && !n.isNumber()) throw invalid("jobs");
            BigDecimal mm = n == null || n.isNull() ? BigDecimal.ZERO : n.decimalValue();
            if (mm.signum() < 0 || mm.stripTrailingZeros().scale() > 2
                || mm.compareTo(new BigDecimal("9999.99")) > 0) throw invalid("jobs");
            result.add(new JobInput(code, mm));
        }
        return new ManpowerInput(result);
    }
    public static String id(String value) {
        if (value == null || value.isBlank() || value.length() > 20 || value.chars().anyMatch(Character::isISOControl))
            throw invalid("projectId");
        return value;
    }
    public static CodeFailure invalid(String field) {
        return CodeFailure.invalid(field, "입력 값을 확인해 주세요.");
    }
}
