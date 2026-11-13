package com.company.common.service;

import com.company.common.dto.MenuRegistrationContracts.Create;
import com.company.common.service.MenuRegistrationFailure;
import com.company.common.service.MenuRegistrationRules;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import static com.company.common.dto.MenuSettingsContracts.*;

public final class MenuSettingsRules {
    private MenuSettingsRules() {}

    public static void level(Integer level) {
        if (level != null && level != 1 && level != 2)
            throw invalid("menuLvl");
    }

    public static MenuSettingsFailure invalid(String field) {
        return new MenuSettingsFailure(field, "입력 값을 확인해 주세요: " + field);
    }

    public static void validate(Input input) {
        if (input == null || input.items() == null || input.items().isEmpty()) throw invalid("items");
        var ids = new HashSet<String>();
        for (var row : input.items()) {
            if (row == null) throw invalid("items");
            id(row.menuId(), "menuId");
            if (!ids.add(row.menuId())) throw invalid("menuId");
            try {
                MenuRegistrationRules.validate(new Create(1, row.menuNm(), null, null, null));
            } catch (MenuRegistrationFailure failure) {
                throw new MenuSettingsFailure(failure.getField(), failure.getMessage());
            }
            if (row.upperMenuId() != null && row.upperMenuId().length() > 20) throw invalid("upperMenuId");
            if (row.sortOrd() == null || row.sortOrd() < 1 || row.sortOrd() > 99999) throw invalid("sortOrd");
            flag(row.useYn(), "useYn");
            flag(row.pcOnlyYn(), "pcOnlyYn");
        }
        if (input.movedMenuIds() == null) throw invalid("movedMenuIds");
        var moved = new HashSet<String>();
        for (String id : input.movedMenuIds()) {
            id(id, "movedMenuIds");
            if (!moved.add(id) || !ids.contains(id)) throw invalid("movedMenuIds");
        }
    }

    private static void id(String value, String field) {
        if (value == null || value.isEmpty() || value.length() > 20) throw invalid(field);
    }

    private static void flag(String value, String field) {
        if (value != null && !"Y".equals(value) && !"N".equals(value)) throw invalid(field);
    }

    private static void object(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) throw invalid("body");
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw invalid(field);
        });
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isTextual()) throw invalid(field);
        return node.textValue();
    }

    private static String optional(JsonNode body, String field) {
        return body.has(field) ? text(body.get(field), field) : null;
    }

    public static Input parse(JsonNode body) {
        object(body, Set.of("items", "movedMenuIds"));
        if (!body.path("items").isArray()) throw invalid("items");
        if (!body.path("movedMenuIds").isArray()) throw invalid("movedMenuIds");
        var items = new ArrayList<Update>();
        for (var row : body.get("items")) {
            object(row, Set.of("menuId", "menuNm", "upperMenuId", "sortOrd", "useYn", "pcOnlyYn"));
            if (!row.has("upperMenuId")) throw invalid("upperMenuId");
            String parent = row.get("upperMenuId").isNull() ? null : text(row.get("upperMenuId"), "upperMenuId");
            if (!row.path("sortOrd").isIntegralNumber() || !row.path("sortOrd").canConvertToInt())
                throw invalid("sortOrd");
            items.add(new Update(text(row.get("menuId"), "menuId"), text(row.get("menuNm"), "menuNm"),
                parent, row.get("sortOrd").intValue(), optional(row, "useYn"), optional(row, "pcOnlyYn")));
        }
        var moved = new ArrayList<String>();
        for (var value : body.get("movedMenuIds")) moved.add(text(value, "movedMenuIds"));
        var input = new Input(items, moved);
        validate(input);
        return input;
    }
}
