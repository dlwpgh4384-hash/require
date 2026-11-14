package com.company.common.service;

import com.company.common.dto.ClientContracts.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class ClientRules {
    private ClientRules() {}
    private static final Set<String> FIELDS = Set.of("clientTypeCode", "clientName", "businessNumber",
        "representativeName", "industryCode", "contactName", "contactPhone", "taxEmail",
        "bankCode", "accountNumber", "accountHolder");

    public static void object(JsonNode body, Set<String> fields) {
        if (body == null || !body.isObject()) throw invalid("body");
        body.fieldNames().forEachRemaining(field -> {
            if (!fields.contains(field)) throw invalid(field);
        });
    }
    public static String text(JsonNode body, String field, int limit, boolean required) {
        var node = body.get(field);
        if (node == null) {
            if (required) throw invalid(field);
            return null;
        }
        if (!node.isTextual()) throw invalid(field);
        String value = node.textValue().trim();
        if ((required && value.isEmpty()) || value.codePointCount(0, value.length()) > limit)
            throw invalid(field);
        return value.isEmpty() ? null : value;
    }
    public static Input parse(JsonNode body, boolean update) {
        if (update && body != null && body.has("businessNumber"))
            throw CodeFailure.invalid("businessNumber", "사업자번호는 등록 후 변경할 수 없습니다. 중지 후 새로 등록해 주세요.");
        var allowed = new HashSet<>(FIELDS);
        if (update) { allowed.remove("businessNumber"); allowed.add("modifiedAt"); }
        object(body, allowed);
        String type = text(body, "clientTypeCode", 10, true);
        String business = update ? null : text(body, "businessNumber", 12, true);
        if (!update && !business.matches("[0-9]{3}-[0-9]{2}-[0-9]{5}")) throw invalid("businessNumber");
        boolean customer = customer(type);
        boolean vendor = vendor(type);
        if (!customer && !vendor) throw invalid("clientTypeCode");
        var input = new Input(type, text(body, "clientName", 50, true), business,
            text(body, "representativeName", 20, false), text(body, "industryCode", 10, false),
            text(body, "contactName", 20, false), text(body, "contactPhone", 20, false),
            customer ? text(body, "taxEmail", 100, true) : null,
            vendor ? text(body, "bankCode", 10, true) : null,
            vendor ? text(body, "accountNumber", 20, true) : null,
            vendor ? text(body, "accountHolder", 30, true) : null);
        if (customer && !input.taxEmail().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw invalid("taxEmail");
        if (vendor && !input.accountNumber().matches("[0-9-]{8,20}")) throw invalid("accountNumber");
        return input;
    }
    public static Update update(JsonNode body) {
        Input input = parse(body, true);
        return new Update(input, token(body));
    }
    public static Instant token(JsonNode body) {
        if (!body.has("modifiedAt")) throw invalid("modifiedAt");
        var node = body.get("modifiedAt");
        if (node.isNull()) return null;
        if (!node.isTextual()) throw invalid("modifiedAt");
        try { return Instant.parse(node.textValue()); }
        catch (DateTimeParseException failure) { throw invalid("modifiedAt"); }
    }
    public static StateInput state(JsonNode body) {
        object(body, Set.of("items", "useYn"));
        String use = text(body, "useYn", 1, true);
        if (!Set.of("Y", "N").contains(use)) throw invalid("useYn");
        var array = body.get("items");
        if (array == null || !array.isArray() || array.isEmpty()) throw invalid("items");
        var items = new ArrayList<Version>();
        var seen = new HashSet<String>();
        for (var node : array) {
            object(node, Set.of("clientId", "modifiedAt"));
            String id = text(node, "clientId", 20, true);
            if (!seen.add(id)) throw invalid("items");
            items.add(new Version(id, token(node)));
        }
        return new StateInput(java.util.List.copyOf(items), use);
    }
    public static Filter filter(String type, String use, String keyword) {
        if (type != null && !type.isBlank() && !customer(type) && !vendor(type)) throw invalid("clientTypeCode");
        // 시안: 사용 여부 조건 [전체]·[사용]·[중지]. ALL이면 사용 여부로 거르지 않는다.
        if (!Set.of("Y", "N", "ALL").contains(use)) throw invalid("useYn");
        return new Filter(type == null || type.isBlank() ? null : type, "ALL".equals(use) ? null : use,
            keyword == null || keyword.isBlank() ? null : keyword.trim());
    }
    public static boolean customer(String type) { return "CUST".equals(type) || "BOTH".equals(type); }
    public static boolean vendor(String type) { return "VNDR".equals(type) || "BOTH".equals(type); }
    public static CodeFailure invalid(String field) {
        return CodeFailure.invalid(field, "입력 값을 확인해 주세요.");
    }

}
