package com.company.common.service;

import com.company.common.dto.CodeMapContracts.DeleteInput;
import com.company.common.dto.CodeMapContracts.Item;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class CodeMapRules {
    private CodeMapRules() {}

    public static Item parseItem(JsonNode body) {
        object(body, Set.of("excelText", "groupId", "code"));
        return validate(new Item(text(body, "excelText"), text(body, "groupId"), text(body, "code")));
    }

    public static DeleteInput parseDelete(JsonNode body) {
        object(body, Set.of("items"));
        var items = body.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) throw invalid("items");
        var result = new ArrayList<Item>();
        for (var item : items) result.add(parseItem(item));
        return validate(new DeleteInput(result));
    }

    public static Item validate(Item item) {
        if (item == null) throw invalid("body");
        length(item.excelText(), "excelText", 100);
        length(item.groupId(), "groupId", 30);
        length(item.code(), "code", 20);
        return item;
    }

    public static DeleteInput validate(DeleteInput input) {
        if (input == null || input.items() == null || input.items().isEmpty()) throw invalid("items");
        var identities = new HashSet<java.util.List<String>>();
        for (var item : input.items()) {
            validate(item);
            if (!identities.add(java.util.List.of(item.excelText(), item.groupId())))
                throw new CodeFailure("items", "같은 대응을 중복 선택할 수 없습니다.");
        }
        return input;
    }

    private static void object(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) throw invalid("body");
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw invalid(field);
        });
    }

    private static String text(JsonNode body, String field) {
        var node = body.get(field);
        if (node == null || !node.isTextual()) throw invalid(field);
        return node.textValue();
    }

    private static void length(String value, String field, int maximum) {
        if (value == null || value.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
            || value.codePointCount(0, value.length()) > maximum) throw invalid(field);
    }

    public static CodeFailure invalid(String field) {
        return new CodeFailure(field, "필수 입력 값과 길이를 확인해 주세요.");
    }
}
