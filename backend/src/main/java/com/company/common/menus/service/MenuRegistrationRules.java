package com.company.common.menus.service;

import com.company.common.menus.dto.MenuRegistrationContracts.Create;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

public final class MenuRegistrationRules {
    private MenuRegistrationRules() {}

    public static void level(Integer level) {
        if (level == null || (level != 1 && level != 2))
            throw new MenuRegistrationFailure("menuLvl", "레벨을 선택해 주세요.");
    }

    public static void validate(Create input) {
        if (input == null) throw new MenuRegistrationFailure("body", "입력 형식을 확인해 주세요.");
        level(input.menuLvl());
        String name = input.menuNm();
        if (name == null || name.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
            || name.codePointCount(0, name.length()) > 50)
            throw new MenuRegistrationFailure("menuNm", "메뉴 이름을 1~50자로 입력해 주세요.");
        if (input.menuLvl() == 1) {
            if (input.upperMenuId() != null || input.scrnId() != null || input.pcOnlyYn() != null)
                throw new MenuRegistrationFailure("body", "대메뉴는 레벨과 이름만 입력해 주세요.");
        } else {
            if (input.upperMenuId() == null || input.upperMenuId().isBlank() || input.upperMenuId().length() > 20)
                throw new MenuRegistrationFailure("upperMenuId", "상위 대메뉴를 선택해 주세요.");
            if (input.scrnId() == null || !input.scrnId().matches("UI-[0-9]{2}"))
                throw new MenuRegistrationFailure("scrnId", "개발된 화면을 선택해 주세요.");
            if (!"Y".equals(input.pcOnlyYn()) && !"N".equals(input.pcOnlyYn()))
                throw new MenuRegistrationFailure("pcOnlyYn", "PC 전용 여부를 선택해 주세요.");
        }
    }

    public static Create create(JsonNode body) {
        if (body == null || !body.isObject())
            throw new MenuRegistrationFailure("body", "입력 형식을 확인해 주세요.");
        JsonNode value = body.get("menuLvl");
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt())
            throw new MenuRegistrationFailure("menuLvl", "레벨을 선택해 주세요.");
        int level = value.intValue();
        level(level);
        Set<String> allowed = level == 1 ? Set.of("menuLvl", "menuNm")
            : Set.of("menuLvl", "menuNm", "upperMenuId", "scrnId", "pcOnlyYn");
        body.fieldNames().forEachRemaining(key -> {
            if (!allowed.contains(key))
                throw new MenuRegistrationFailure(key, "허용되지 않은 입력 항목입니다.");
        });
        Create input = new Create(level, text(body, "menuNm"),
            level == 1 ? null : text(body, "upperMenuId"),
            level == 1 ? null : text(body, "scrnId"),
            level == 1 ? null : text(body, "pcOnlyYn"));
        validate(input);
        return input;
    }

    private static String text(JsonNode body, String key) {
        JsonNode value = body.get(key);
        if (value == null || !value.isTextual())
            throw new MenuRegistrationFailure(key, "필수 입력 값을 확인해 주세요.");
        return value.textValue();
    }
}
