package com.company.common.dto;

import com.company.common.service.UserFailure;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static com.company.common.dto.UserContracts.*;

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
    public static Instant token(JsonNode body) {
        if (!body.has("modDttm")) throw UserFailure.invalid("modDttm", "조회한 수정 시각을 보내 주세요.");
        JsonNode node = body.get("modDttm");
        if (node.isNull()) return null;
        try {
            if (!node.isTextual()) throw new IllegalArgumentException();
            var value = OffsetDateTime.parse(node.textValue());
            if (!value.getOffset().equals(ZoneOffset.UTC) || value.getNano() % 1000 != 0)
                throw new IllegalArgumentException();
            return value.toInstant();
        } catch (RuntimeException failure) {
            throw UserFailure.invalid("modDttm", "수정 시각의 형식을 확인해 주세요.");
        }
    }
    public static Create create(JsonNode body) {
        shape(body, "userId", "name", "email", "roleCds", "headquarters", "projectIds");
        String userId = text(body, "userId", 20);
        id(userId);
        String email = email(body);
        return new Create(userId, text(body, "name", 50), email, strings(body, "roleCds", true),
            headquarters(body), strings(body, "projectIds", false));
    }
    public static Update update(JsonNode body) {
        shape(body, "name", "email", "roleCds", "headquarters", "projectIds", "modDttm", "confirmedProjectIds");
        return new Update(text(body, "name", 50), email(body), strings(body, "roleCds", true), headquarters(body),
            strings(body, "projectIds", false), token(body), strings(body, "confirmedProjectIds", false));
    }
    static String email(JsonNode body) {
        String email = text(body, "email", 100);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw UserFailure.invalid("email", "이메일 형식이 맞지 않습니다.");
        return email;
    }
    public static Instant version(JsonNode body) {
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
    /** 목록 정렬에 쓸 수 있는 열(SFR-010-03). 담당 파트는 계산 값이라 정렬하지 않는다. */
    public static final Set<String> SORT_KEYS = Set.of("userId", "name", "role", "state", "last");
    public static Filter filter(int page, int pageSize, String role, String state, String keyword) {
        return filter(page, pageSize, role, state, keyword, true, null, null);
    }
    public static Filter filter(int page, int pageSize, String role, String state, String keyword,
        boolean includeStopped, String sort, String dir) {
        if (sort != null && !SORT_KEYS.contains(sort)) throw UserFailure.invalid("sort", "정렬할 열을 확인해 주세요.");
        if (dir != null && !Set.of("asc", "desc").contains(dir)) throw UserFailure.invalid("dir", "정렬 방향을 확인해 주세요.");
        Filter base = basic(page, pageSize, role, state, keyword);
        return new Filter(base.page(), base.pageSize(), base.offset(), base.roleCd(), base.userStatCd(), base.keyword(),
            includeStopped, sort, sort == null ? null : (dir == null ? "ASC" : dir.toUpperCase()));
    }
    private static Filter basic(int page, int pageSize, String role, String state, String keyword) {
        if (page < 1) throw UserFailure.invalid("page", "페이지는 1 이상이어야 합니다.");
        if (pageSize != 20 && pageSize != 50 && pageSize != 100)
            throw UserFailure.invalid("pageSize", "목록 건수는 20, 50, 100 중 선택해 주세요.");
        if (role != null && role.isBlank()) throw UserFailure.invalid("roleCd", "역할을 확인해 주세요.");
        if (state != null && !Set.of("USE", "LOCK", "STOP").contains(state))
            throw UserFailure.invalid("userStatCd", "상태를 확인해 주세요.");
        String escaped = keyword == null || keyword.isEmpty() ? null
            : "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return new Filter(page, pageSize, (long) (page - 1) * pageSize, role, state, escaped, true, null, null);
    }
}
