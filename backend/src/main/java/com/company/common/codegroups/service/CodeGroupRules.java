package com.company.common.codegroups.service;

import com.company.common.codegroups.mapper.CodeRows.Code;
import com.company.common.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class CodeGroupRules {
    private CodeGroupRules() {}
    private static final Set<String> COMMON = Set.of("code", "codeName", "sortOrd", "useYn", "codeVal", "unitNm");
    private static final Set<String> EXTRA = Set.of("relatedJobs", "estimateUnitPrice", "requiredJobs", "weight",
        "contractTypes", "bankCode", "targetField", "job");
    public static CodeFailure invalid(String field) {
        return new CodeFailure(field, "입력값을 확인해 주세요.");
    }
    public static String groupId(String id) {
        if (id == null || !id.matches("[A-Z][A-Z0-9_]{0,29}")) throw invalid("groupId");
        return id;
    }
    public static String code(String code, boolean create) {
        if (code == null || !code.matches(create ? "[A-Za-z0-9_]{1,20}" : "[A-Z0-9_]{1,20}"))
            throw invalid("code");
        return code.toUpperCase(Locale.ROOT);
    }
    public static void object(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) throw invalid("body");
        body.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw invalid(field);
        });
    }
    public static String text(JsonNode body, String field, int max, boolean required) {
        var node = body.get(field);
        if (node == null || node.isNull()) {
            if (required) throw invalid(field);
            return null;
        }
        if (!node.isTextual()) throw invalid(field);
        String value = node.textValue();
        if (value.codePointCount(0, value.length()) > max
            || (required && value.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))))
            throw invalid(field);
        return value;
    }
    public static int order(JsonNode body, int fallback) {
        if (!body.has("sortOrd")) return fallback;
        var node = body.get("sortOrd");
        if (!node.isIntegralNumber() || !node.canConvertToInt()) throw invalid("sortOrd");
        int value = node.intValue();
        if (value < 1 || value > 99999) throw invalid("sortOrd");
        return value;
    }
    public static BigDecimal number(JsonNode body, String field, BigDecimal fallback) {
        var node = body.get(field);
        if (node == null) return fallback;
        if (node.isNull()) {
            if ("codeVal".equals(field)) return BigDecimal.ZERO;
            throw invalid(field);
        }
        if (!node.isNumber()) throw invalid(field);
        BigDecimal value = node.decimalValue().stripTrailingZeros();
        if (Math.max(0, value.scale()) > 4 || value.precision() - value.scale() > 11) throw invalid(field);
        return node.decimalValue();
    }
    public static Code parse(String group, JsonNode body, Code old, int nextOrder, boolean numeric) {
        var allowed = new HashSet<>(COMMON);
        allowed.addAll(EXTRA);
        object(body, allowed);
        boolean create = old == null;
        if (!create && (!body.has("sortOrd") || !body.has("useYn")))
            throw invalid(!body.has("sortOrd") ? "sortOrd" : "useYn");
        var row = new Code();
        row.groupId = group;
        row.code = code(text(body, "code", 20, true), create);
        row.codeName = text(body, "codeName", 100, true);
        row.sortOrd = order(body, nextOrder);
        if (row.sortOrd > 99999) throw invalid("sortOrd");
        row.useYn = body.has("useYn") ? text(body, "useYn", 1, true) : "Y";
        if (!Set.of("Y", "N").contains(row.useYn)) throw invalid("useYn");
        row.delYn = "N";
        row.codeVal = body.has("codeVal") ? number(body, "codeVal", null)
            : create ? (numeric ? BigDecimal.ZERO : null) : old.codeVal;
        row.unitNm = body.has("unitNm") ? text(body, "unitNm", 10, false) : create ? null : old.unitNm;
        row.addVal1 = create ? null : old.addVal1;
        row.addVal2 = create ? null : old.addVal2;
        row.addVal3 = create ? null : old.addVal3;
        Set<String> relevant = switch (group) {
            case "JOB" -> Set.of("relatedJobs", "estimateUnitPrice");
            case "SKILL" -> Set.of("requiredJobs");
            case "PROJ_STEP" -> Set.of("weight", "contractTypes");
            case "BANK" -> Set.of("bankCode");
            case "RSM_LABEL" -> Set.of("targetField");
            case "RSM_JOB_KW" -> Set.of("job");
            default -> Set.of();
        };
        for (String field : EXTRA) {
            if (body.has(field) && !relevant.contains(field)) throw invalid(field);
        }
        for (String field : relevant) {
            if (!create && !body.has(field)) continue;
            String value;
            if (field.equals("estimateUnitPrice") || field.equals("weight")) {
                var number = number(body, field, null);
                if (number == null) throw invalid(field);
                value = number.toPlainString();
            } else value = text(body, field, 100, true);
            if (field.equals("targetField") && !Set.of("NAME", "TEL", "MAIL", "ADDR", "SKILL", "WANT", "FROM", "VNDR").contains(value))
                throw new CodeFailure(field, "넣을 칸은 NAME·TEL·MAIL·ADDR·SKILL·WANT·FROM·VNDR 중 하나로 넣어 주세요.");
            if (field.equals("estimateUnitPrice") || field.equals("contractTypes")) row.addVal2 = value;
            else row.addVal1 = value;
        }
        return row;
    }
}
