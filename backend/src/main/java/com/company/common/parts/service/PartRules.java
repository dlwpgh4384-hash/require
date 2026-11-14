package com.company.common.parts.service;

import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Set;
import static com.company.common.parts.dto.PartContracts.*;

public final class PartRules {
    private PartRules() {}

    public static Create create(JsonNode body) {
        fields(body, Set.of("partName", "effectiveMonth", "leaderUserId", "distributionRate", "salesExpense"));
        return new Create(name(body), values(body));
    }
    public static StandardInput standard(JsonNode body) {
        fields(body, Set.of("effectiveMonth", "leaderUserId", "distributionRate", "salesExpense", "modifiedAt"));
        return new StandardInput(values(body), token(body));
    }
    public static Rename rename(JsonNode body) {
        fields(body, Set.of("partName", "modifiedAt"));
        return new Rename(name(body), token(body));
    }
    public static Version version(JsonNode body) {
        fields(body, Set.of("modifiedAt"));
        return new Version(token(body));
    }
    private static void fields(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) throw invalid("body", "입력 형식을 확인해 주세요.");
        body.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw invalid(field, "허용하지 않는 입력 항목입니다.");
        });
        for (String field : allowed) {
            if (!body.has(field)) throw invalid(field, "필수 입력 값이 없습니다.");
        }
    }
    private static String text(JsonNode body, String field) {
        var value = body.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank())
            throw invalid(field, "필수 입력 값을 확인해 주세요.");
        return value.textValue().trim();
    }
    private static String name(JsonNode body) {
        String value = text(body, "partName");
        if (value.codePointCount(0, value.length()) > 30)
            throw invalid("partName", "파트명은 30자 이내로 입력해 주세요.");
        return value;
    }
    private static Values values(JsonNode body) {
        String month = text(body, "effectiveMonth");
        month(month);
        String leader = text(body, "leaderUserId");
        if (leader.length() > 30) throw invalid("leaderUserId", "파트장을 확인해 주세요.");
        BigDecimal rate = number(body, "distributionRate");
        if (rate.signum() < 0 || rate.compareTo(new BigDecimal("100")) > 0 || rate.scale() > 2)
            throw invalid("distributionRate", "배분율은 0~100, 소수 둘째 자리까지 입력해 주세요.");
        BigDecimal expense = number(body, "salesExpense");
        if (expense.signum() < 0 || expense.stripTrailingZeros().scale() > 0
            || expense.compareTo(new BigDecimal("999999999999999")) > 0)
            throw invalid("salesExpense", "공통관리비는 15자리 이내의 음수가 아닌 정수로 입력해 주세요.");
        return new Values(month, leader, rate, expense);
    }
    private static BigDecimal number(JsonNode body, String field) {
        var value = body.get(field);
        if (value == null || !value.isNumber()) throw invalid(field, "숫자로 입력해 주세요.");
        return value.decimalValue();
    }
    private static Instant token(JsonNode body) {
        var value = body.get("modifiedAt");
        if (value.isNull()) return null;
        try {
            if (value.isTextual()) return Instant.parse(value.textValue());
        } catch (RuntimeException ignored) {
            throw invalid("modifiedAt", "수정 시각을 확인해 주세요.");
        }
        throw invalid("modifiedAt", "수정 시각을 확인해 주세요.");
    }
    public static YearMonth month(String value) {
        try {
            if (value != null && value.matches("[0-9]{4}-(0[1-9]|1[0-2])")
                && !value.startsWith("0000")) return YearMonth.parse(value);
        } catch (RuntimeException ignored) {
            throw invalid("effectiveMonth", "적용 시작월을 확인해 주세요.");
        }
        throw invalid("effectiveMonth", "적용 시작월을 확인해 주세요.");
    }
    public static CodeFailure invalid(String field, String message) {
        return CodeFailure.invalid(field, message);
    }
}
