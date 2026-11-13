package com.company.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static com.company.common.dto.RoleContracts.*;

public final class RoleRules {
    private RoleRules() {}
    public static boolean basic(String id) {
        return Set.of("ADMIN", "EXEC", "FIN", "SALES_LDR", "PM").contains(id);
    }
    public static void name(String name) {
        if (name == null || name.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
            || name.codePointCount(0, name.length()) > 50)
            throw RoleFailure.invalid("roleName", "새 역할 이름을 공백 없이 1~50자로 입력해 주세요.");
    }
    public static void flag(String value, String field) {
        if (!"Y".equals(value) && !"N".equals(value))
            throw RoleFailure.invalid(field, "Y 또는 N으로 입력해 주세요.");
    }
    private static void keys(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) throw RoleFailure.invalid("body", "입력 형식을 확인해 주세요.");
        node.fieldNames().forEachRemaining(key -> {
            if (!allowed.contains(key)) throw RoleFailure.invalid(key, "허용되지 않은 입력 항목입니다.");
        });
    }
    private static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual() || value.textValue().isBlank())
            throw RoleFailure.invalid(key, "필수 입력 값을 확인해 주세요.");
        return value.textValue();
    }
    public static Copy copy(JsonNode body) {
        keys(body, Set.of("sourceRoleId", "roleName", "dataScopeCd"));
        String name = text(body, "roleName");
        name(name);
        // sourceRoleId가 없으면 「역할 추가」(시안), 있으면 「역할 복사」.
        String source = body.hasNonNull("sourceRoleId") ? text(body, "sourceRoleId") : null;
        return new Copy(source, name, text(body, "dataScopeCd"));
    }
    public static Update update(JsonNode body) {
        keys(body, Set.of("dataScopeCd", "snstvViewYn", "menuAuths", "confirmedAffectedUserIds"));
        String scope = text(body, "dataScopeCd");
        String sensitive = text(body, "snstvViewYn");
        flag(sensitive, "snstvViewYn");
        if (!body.path("menuAuths").isArray()) throw RoleFailure.invalid("menuAuths", "권한 표를 보내 주세요.");
        var menus = new ArrayList<MenuInput>();
        for (JsonNode menu : body.path("menuAuths")) {
            keys(menu, Set.of("menuId", "readYn", "createYn", "updateYn", "deleteYn", "uploadYn", "downloadYn", "cnfmYn"));
            var input = new MenuInput(text(menu, "menuId"), text(menu, "readYn"), text(menu, "createYn"),
                text(menu, "updateYn"), text(menu, "deleteYn"), text(menu, "uploadYn"),
                text(menu, "downloadYn"), text(menu, "cnfmYn"));
            input.flags().forEach(value -> flag(value, "menuAuths"));
            menus.add(input);
        }
        if (!body.path("confirmedAffectedUserIds").isArray())
            throw RoleFailure.invalid("confirmedAffectedUserIds", "영향 사용자를 확인해 주세요.");
        var confirmed = new ArrayList<String>();
        for (JsonNode value : body.path("confirmedAffectedUserIds")) {
            if (!value.isTextual() || value.textValue().isBlank())
                throw RoleFailure.invalid("confirmedAffectedUserIds", "영향 사용자를 확인해 주세요.");
            confirmed.add(value.textValue());
        }
        return new Update(scope, sensitive, List.copyOf(menus), List.copyOf(confirmed));
    }
    public static void matrix(String id, List<MenuAuth> current, List<MenuInput> next) {
        if (next == null) throw RoleFailure.invalid("menuAuths", "권한 표를 보내 주세요.");
        var ids = new HashSet<String>();
        for (var menu : next) {
            if (menu == null || menu.menuId() == null || !ids.add(menu.menuId()))
                throw RoleFailure.invalid("menuAuths", "메뉴를 중복 없이 보내 주세요.");
            menu.flags().forEach(value -> flag(value, "menuAuths"));
            if (id.equals("ADMIN") && menu.menuId().equals("M028") && !"Y".equals(menu.updateYn()))
                throw RoleFailure.invalid("menuAuths", "시스템 관리자의 권한 메뉴 수정 권한은 해제할 수 없습니다.");
            if (id.equals("SALES_LDR") && menu.menuId().equals("M024")
                && menu.flags().subList(1, 7).stream().anyMatch("Y"::equals))
                throw RoleFailure.invalid("menuAuths", "영업 파트장의 월 마감은 조회만 허용합니다.");
        }
        if (!ids.equals(new HashSet<>(current.stream().map(MenuAuth::menuId).toList())))
            throw RoleFailure.invalid("menuAuths", "현재 권한 표의 모든 메뉴를 빠짐없이 보내 주세요.");
    }
}
