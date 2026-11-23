package com.company.common.monthcloses.service;

import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Set;

public final class MonthCloseRules {
    private MonthCloseRules() {}
    public static YearMonth month(String value) {
        try {
            if (value == null || !value.matches("[0-9]{4}-(0[1-9]|1[0-2])"))
                throw new IllegalArgumentException();
            var month = YearMonth.parse(value);
            if (month.getYear() < 1) throw new IllegalArgumentException();
            return month;
        } catch (RuntimeException failure) {
            throw CodeFailure.invalid("yearMonth", "년월은 YYYY-MM 형식으로 입력해 주세요.");
        }
    }
    public static Instant version(JsonNode body) {
        if (body == null || !body.isObject() || !body.has("modifiedAt"))
            throw CodeFailure.invalid("modifiedAt", "조회한 수정 시각을 입력해 주세요.");
        var value = body.get("modifiedAt");
        if (value.isNull()) return null;
        try {
            if (!value.isTextual()) throw new IllegalArgumentException();
            return Instant.parse(value.textValue());
        } catch (RuntimeException failure) {
            throw CodeFailure.invalid("modifiedAt", "수정 시각을 확인해 주세요.");
        }
    }
    public static String reason(JsonNode body) {
        var value = body == null ? null : body.get("reason");
        String text = value != null && value.isTextual() ? value.textValue() : null;
        reason(text);
        return text;
    }
    public static void reason(String value) {
        if (value == null || value.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
            || value.codePointCount(0, value.length()) > 500)
            throw CodeFailure.invalid("reason", "해제 사유를 1~500자로 입력해 주세요.");
    }
    public static boolean canReopen(Set<String> roles) { return roles != null && roles.contains("ADMIN"); }
    public static void version(Instant actual, Instant expected) {
        if (!Objects.equals(actual, expected)) throw CodeFailure.conflict();
    }
    public static void confirmable(YearMonth month, YearMonth firstOpen, YearMonth current) {
        if (!month.equals(firstOpen) || !month.isBefore(current))
            throw CodeFailure.invalid("yearMonth", "자금수지 시작 월부터 지난달까지 첫 미마감월만 마감할 수 있습니다.");
    }
}
