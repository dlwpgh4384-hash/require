package com.company.common.salesopportunities.service;

import com.company.common.clients.service.ClientRules;
import com.company.common.salesopportunities.dto.SalesOpportunityContracts.*;
import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class SalesOpportunityRules {
    private SalesOpportunityRules() {}
    public static CodeFailure invalid(String field) {
        return CodeFailure.invalid(field, "입력 값을 확인해 주세요.");
    }
    public static String text(JsonNode body, String field, int limit, boolean required) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            if (required) throw invalid(field);
            return null;
        }
        if (!node.isTextual()) throw invalid(field);
        String value = node.textValue();
        if (value.codePointCount(0, value.length()) > limit || required && value.isBlank()) throw invalid(field);
        return value.isBlank() ? null : value;
    }
    public static BigDecimal number(JsonNode body, String field, boolean required) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || node.isTextual() && node.textValue().isBlank()) {
            if (required) throw invalid(field);
            return null;
        }
        if (!node.isNumber()) throw invalid(field);
        return node.decimalValue();
    }
    public static int integer(JsonNode body, String field, Integer fallback) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || node.isTextual() && node.textValue().isBlank()) {
            if (fallback == null) throw invalid(field);
            return fallback;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() < 1 || node.intValue() > 999)
            throw invalid(field);
        return node.intValue();
    }
    public static String month(JsonNode body, String field) {
        String value = text(body, field, 7, true);
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}") || YearMonth.parse(value).getYear() < 1) throw invalid(field);
        } catch (DateTimeException exception) { throw invalid(field); }
        return value;
    }
    public static LocalDate date(JsonNode body, String field, boolean required) {
        String value = text(body, field, 10, required);
        if (value == null) return null;
        try { return LocalDate.parse(value); }
        catch (DateTimeException exception) { throw invalid(field); }
    }
    public static Instant version(JsonNode body) {
        String value = text(body, "modifiedAt", 40, true);
        try { return Instant.parse(value); }
        catch (DateTimeException exception) { throw invalid("modifiedAt"); }
    }
    public static Need need(JsonNode body) {
        ClientRules.object(body, Set.of("needSn", "jobCode", "gradeCode", "requiredCount", "startMonth",
            "months", "monthlyMm", "estimatedUnitPrice", "tentativeDeveloperIds"));
        BigDecimal mm = number(body, "monthlyMm", true);
        if (mm.signum() <= 0 || mm.compareTo(BigDecimal.ONE) > 0 || mm.scale() > 2) throw invalid("monthlyMm");
        BigDecimal price = number(body, "estimatedUnitPrice", false);
        if (price != null && (price.signum() <= 0 || price.precision() - price.scale() > 15
            || price.stripTrailingZeros().scale() > 0)) throw invalid("estimatedUnitPrice");
        var developers = new LinkedHashSet<String>();
        var ids = body.get("tentativeDeveloperIds");
        if (ids != null && !ids.isNull()) {
            if (!ids.isArray()) throw invalid("tentativeDeveloperIds");
            for (var id : ids) {
                if (!id.isTextual() || id.textValue().isBlank() || id.textValue().length() > 20)
                    throw invalid("tentativeDeveloperIds");
                developers.add(id.textValue());
            }
        }
        return new Need(body.hasNonNull("needSn") ? integer(body, "needSn", null) : null,
            text(body, "jobCode", 10, true), text(body, "gradeCode", 10, false),
            integer(body, "requiredCount", null), month(body, "startMonth"), integer(body, "months", null),
            mm, price, List.copyOf(developers));
    }
    public static Input input(JsonNode body, boolean update) {
        var fields = new HashSet<>(Set.of("opportunityName", "customerId", "salesPartId", "stageCode",
            "winningProbability", "expectedAmount", "contractTypeCode", "startMonth", "expectedMonths",
            "workplace", "nextAction", "nextActionDueDate", "memo", "needs"));
        if (update) fields.add("modifiedAt");
        ClientRules.object(body, fields);
        // 확률을 비우면 단계 기본값으로 저장한다(SFR-129-07, 서비스에서 정함).
        var probability = number(body, "winningProbability", false);
        if (probability != null && (probability.signum() < 0 || probability.compareTo(new BigDecimal("100")) > 0
            || probability.stripTrailingZeros().scale() > 2)) throw invalid("winningProbability");
        var amount = number(body, "expectedAmount", true);
        if (amount.signum() <= 0 || amount.precision() - amount.scale() > 15
            || amount.stripTrailingZeros().scale() > 0) throw invalid("expectedAmount");
        var array = body.get("needs");
        if (array == null || !array.isArray() || array.isEmpty() || array.size() > 999) throw invalid("needs");
        var needs = new ArrayList<Need>();
        for (int i = 0; i < array.size(); i++) {
            try { needs.add(need(array.get(i))); }
            catch (CodeFailure failure) { throw row(i, failure.getField()); }
        }
        return new Input(text(body, "opportunityName", 100, true), text(body, "customerId", 20, true),
            text(body, "salesPartId", 20, true), text(body, "stageCode", 10, true), probability, amount,
            text(body, "contractTypeCode", 10, false), month(body, "startMonth"),
            integer(body, "expectedMonths", 6), text(body, "workplace", 200, false),
            text(body, "nextAction", 200, false), date(body, "nextActionDueDate", false),
            text(body, "memo", 500, false), List.copyOf(needs));
    }
    /** 필요 인력 줄 칸 오류: 「n번째 줄의 ○○ 칸을 확인해 주세요.」(칸 이름은 폼 머리 글자, 영어 칸 이름은 보이지 않는다). */
    public static CodeFailure row(int index, String field) {
        return CodeFailure.invalid("needs[" + index + "]." + field,
            (index + 1) + "번째 줄의 " + label(field) + " 칸을 확인해 주세요.");
    }
    private static String label(String field) {
        return switch (field) {
            case "jobCode" -> "직무"; case "gradeCode" -> "등급";
            case "requiredCount" -> "인원"; case "startMonth" -> "시작월";
            case "months" -> "개월"; case "monthlyMm" -> "월 MM";
            case "estimatedUnitPrice" -> "단가"; case "tentativeDeveloperIds" -> "가배정 인력";
            case "needSn" -> "필요 인력 줄"; default -> "입력";
        };
    }
    public static Tentative tentative(JsonNode body) {
        ClientRules.object(body, Set.of("modifiedAt", "developerId", "needSn", "startMonth"));
        return new Tentative(version(body), text(body, "developerId", 20, true),
            integer(body, "needSn", null), month(body, "startMonth"));
    }
    public static DeveloperCheck developerCheck(JsonNode body) {
        ClientRules.object(body, Set.of("opportunityId", "developerId", "startMonth"));
        return new DeveloperCheck(text(body, "opportunityId", 20, false), text(body, "developerId", 20, true),
            month(body, "startMonth"));
    }
    public static Win win(JsonNode body) {
        ClientRules.object(body, Set.of("modifiedAt", "contractAmount", "contractTypeCode", "contractStartDate",
            "contractEndDate", "projectStatusCode"));
        var amount = number(body, "contractAmount", true);
        if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0
            || amount.precision() - amount.scale() > 15) throw invalid("contractAmount");
        LocalDate start = date(body, "contractStartDate", true), end = date(body, "contractEndDate", true);
        if (!end.isAfter(start)) throw invalid("contractEndDate");
        String status = text(body, "projectStatusCode", 10, true);
        if (!Set.of("ING", "PRE").contains(status)) throw invalid("projectStatusCode");
        return new Win(version(body), amount, text(body, "contractTypeCode", 10, true), start, end, status);
    }
}
