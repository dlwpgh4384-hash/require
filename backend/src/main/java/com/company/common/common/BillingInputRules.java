package com.company.common.common;

import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * 청구·입금(UI-13) 입력 검사. 00-1 FR-0106 문구 형식으로 칸마다 이유를 돌려준다
 * (비어 있음 「○○을(를) 입력해 주세요.」, 길이 「○○은(는) n자까지 입력해 주세요.」).
 */
public final class BillingInputRules {
    private BillingInputRules() {}

    /** 받을 수 있는 항목만 왔는지 본다. */
    public static void object(JsonNode body, Set<String> fields) {
        if (body == null || !body.isObject()) throw CodeFailure.invalid("body", "요청 내용을 확인해 주세요.");
        body.fieldNames().forEachRemaining(field -> {
            if (!fields.contains(field)) throw CodeFailure.invalid(field, "받을 수 없는 항목이 있습니다.");
        });
    }

    public static String required(String label) {
        return withObject(label) + " 입력해 주세요.";
    }

    public static String tooLong(String label, int limit) {
        return withTopic(label) + " " + limit + "자까지 입력해 주세요.";
    }

    /** 앞뒤 공백을 지운 글자. 비면 null(필수면 오류). 길이는 글자 수로 센다. */
    public static String text(JsonNode body, String field, String label, int limit, boolean required) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            if (required) throw CodeFailure.invalid(field, required(label));
            return null;
        }
        if (!node.isTextual()) throw CodeFailure.invalid(field, withObject(label) + " 확인해 주세요.");
        String value = node.textValue().strip();
        if (value.isEmpty()) {
            if (required) throw CodeFailure.invalid(field, required(label));
            return null;
        }
        if (value.codePointCount(0, value.length()) > limit) throw CodeFailure.invalid(field, tooLong(label, limit));
        return value;
    }

    /** 원 단위 0 이상 정수(15자리 이내). */
    public static BigDecimal money(JsonNode body, String field, String label, boolean required) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            if (required) throw CodeFailure.invalid(field, required(label));
            return null;
        }
        if (!node.isNumber()) throw CodeFailure.invalid(field, required(label));
        BigDecimal value = node.decimalValue();
        if (value.signum() < 0 || value.stripTrailingZeros().scale() > 0 || value.compareTo(BigDecimal.TEN.pow(15)) >= 0)
            throw CodeFailure.invalid(field, withTopic(label) + " 15자리 이내의 원 단위 정수로 입력해 주세요.");
        return value;
    }

    /** YYYY-MM-DD 날짜. */
    public static LocalDate date(JsonNode body, String field, String label) {
        String value = text(body, field, label, 10, true);
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeException(value);
            LocalDate result = LocalDate.parse(value);
            if (result.getYear() < 1) throw new DateTimeException(value);
            return result;
        } catch (DateTimeException failure) {
            throw CodeFailure.invalid(field, withObject(label) + " YYYY-MM-DD 형식으로 입력해 주세요. (예: 2026-11-25)");
        }
    }

    /** 화면이 읽은 수정 시각(00-1 동시 수정). 없거나 형식이 틀리면 400, 값이 지금과 다르면 부르는 쪽이 409. */
    public static Instant token(JsonNode body) {
        JsonNode node = body.get("modifiedAt");
        if (node == null || node.isNull()) throw CodeFailure.invalid("modifiedAt", "수정 시각을 제출해 주세요.");
        try {
            if (!node.isTextual()) throw new IllegalArgumentException();
            return Instant.parse(node.textValue());
        } catch (RuntimeException failure) {
            throw CodeFailure.invalid("modifiedAt", "수정 시각을 확인해 주세요.");
        }
    }

    private static boolean hasFinal(String label) {
        char last = label.charAt(label.length() - 1);
        return last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0;
    }

    static String withObject(String label) {
        return label + (hasFinal(label) ? "을" : "를");
    }

    static String withTopic(String label) {
        return label + (hasFinal(label) ? "은" : "는");
    }
}
