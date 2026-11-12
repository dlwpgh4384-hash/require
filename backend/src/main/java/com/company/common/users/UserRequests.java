package com.company.common.users;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static com.company.common.users.UserContracts.*;

public final class UserRequests {
    private UserRequests() {}
    public static void id(String value) {
        if (value == null || !value.matches("[a-z0-9]{4,20}"))
            throw UserFailure.invalid("userId", "아이디는 영문 소문자·숫자 4~20자로 입력해 주세요.");
    }
    static void shape(JsonNode body, String... fields) {
        if (body == null || !body.isObject()) throw UserFailure.invalid(fields[0], "입력 형식을 확인해 주세요.");
        Set<String> allowed = Set.of(fields);
        body.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw UserFailure.invalid(field, "정해진 항목만 입력해 주세요.");
        });
        for (String field : fields) if (!body.has(field))
            throw UserFailure.invalid(field, "필수 항목을 입력해 주세요.");
    }
    static String text(JsonNode body, String field, int max) {
        JsonNode node = body.get(field);
        if (node == null || !node.isTextual() || node.textValue().isBlank()
            || node.textValue().length() > max) throw UserFailure.invalid(field, "입력 값과 길이를 확인해 주세요.");
        return node.textValue();
    }
    static List<String> strings(JsonNode body, String field, boolean nonempty) {
        JsonNode node = body.get(field);
        if (node == null || !node.isArray() || (nonempty && node.isEmpty()))
            throw UserFailure.invalid(field, "항목을 선택해 주세요.");
        var values = new ArrayList<String>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.textValue().isBlank() || values.contains(item.textValue()))
                throw UserFailure.invalid(field, "중복 없이 올바른 항목을 선택해 주세요.");
            values.add(item.textValue());
        }
        return List.copyOf(values);
    }
    static boolean headquarters(JsonNode body) {
        if (!body.get("headquarters").isBoolean()) throw UserFailure.invalid("headquarters", "본사 선택을 확인해 주세요.");
        return body.get("headquarters").booleanValue();
    }
    public static LocalDateTime token(JsonNode body) {
        if (!body.has("modDttm")) throw UserFailure.invalid("modDttm", "조회한 수정 시각을 보내 주세요.");
        JsonNode node = body.get("modDttm");
        if (node.isNull()) return null;
        try {
            if (!node.isTextual()) throw new IllegalArgumentException();
            var value = OffsetDateTime.parse(node.textValue());
            if (!value.getOffset().equals(ZoneOffset.UTC) || value.getNano() % 1000 != 0)
                throw new IllegalArgumentException();
            return value.toLocalDateTime();
        } catch (RuntimeException failure) {
            throw UserFailure.invalid("modDttm", "수정 시각의 형식을 확인해 주세요.");
        }
    }
    public static Create create(JsonNode body) {
        shape(body, "userId", "name", "email", "roleCds", "headquarters", "projectIds");
        String userId = text(body, "userId", 20);
        id(userId);
        String email = text(body, "email", 100);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw UserFailure.invalid("email", "이메일 형식으로 입력해 주세요.");
        return new Create(userId, text(body, "name", 50), email, strings(body, "roleCds", true),
            headquarters(body), strings(body, "projectIds", false));
    }
    public static Update update(JsonNode body) {
        shape(body, "roleCds", "headquarters", "projectIds", "modDttm", "confirmedProjectIds");
        return new Update(strings(body, "roleCds", true), headquarters(body), strings(body, "projectIds", false),
            token(body), strings(body, "confirmedProjectIds", false));
    }
    public static LocalDateTime version(JsonNode body) {
        shape(body, "modDttm");
        return token(body);
    }
    public static Stop stop(JsonNode body) {
        shape(body, "users", "confirmedProjectIds");
        if (!body.get("users").isArray() || body.get("users").isEmpty())
            throw UserFailure.invalid("users", "사용자를 선택해 주세요.");
        var users = new ArrayList<Version>();
        var ids = new HashSet<String>();
        for (JsonNode item : body.get("users")) {
            shape(item, "userId", "modDttm");
            String userId = text(item, "userId", 20);
            id(userId);
            if (!ids.add(userId)) throw UserFailure.invalid("users", "사용자를 중복 선택할 수 없습니다.");
            users.add(new Version(userId, token(item)));
        }
        return new Stop(List.copyOf(users), strings(body, "confirmedProjectIds", false));
    }
    public static Filter filter(int page, int pageSize, String role, String state, String keyword) {
        if (page < 1) throw UserFailure.invalid("page", "페이지는 1 이상이어야 합니다.");
        if (pageSize != 20 && pageSize != 50 && pageSize != 100)
            throw UserFailure.invalid("pageSize", "목록 건수는 20, 50, 100 중 선택해 주세요.");
        if (role != null && role.isBlank()) throw UserFailure.invalid("roleCd", "역할을 확인해 주세요.");
        if (state != null && !Set.of("USE", "LOCK", "STOP").contains(state))
            throw UserFailure.invalid("userStatCd", "상태를 확인해 주세요.");
        String escaped = keyword == null || keyword.isEmpty() ? null
            : "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return new Filter(page, pageSize, (long) (page - 1) * pageSize, role, state, escaped);
    }
}
