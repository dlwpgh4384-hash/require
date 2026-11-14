package com.company.common.menus.service;

import com.company.common.menus.dto.MenuRegistrationContracts.Item;
import com.company.common.users.dto.UserContracts.History;
import com.company.common.menus.mapper.MenuSettingsMapper;
import com.company.common.menus.mapper.MenuSettingsRow;
import com.company.common.users.mapper.UserMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.company.common.menus.dto.MenuSettingsContracts.*;
import static com.company.common.menus.service.MenuSettingsRules.invalid;

@Service
@Transactional(readOnly = true)
public class CommonMenuSettingsService {
    private final MenuSettingsMapper mapper;
    private final UserMapper management;

    public CommonMenuSettingsService(MenuSettingsMapper mapper, UserMapper management) {
        this.mapper = mapper;
        this.management = management;
    }

    public Result getCommonMenuSettings(Integer menuLvl) {
        MenuSettingsRules.level(menuLvl);
        var rows = mapper.selectSettings().stream().map(MenuSettingsRow::value)
            .sorted(Comparator.comparingInt(Item::sortOrd).thenComparing(Item::menuId)).toList();
        var ordered = new ArrayList<Item>();
        var seen = new HashSet<String>();
        for (var group : rows) {
            if (group.menuLvl() != 1) continue;
            ordered.add(group);
            seen.add(group.menuId());
            for (var child : rows) {
                if (child.menuLvl() == 2 && group.menuId().equals(child.upperMenuId())) {
                    ordered.add(child);
                    seen.add(child.menuId());
                }
            }
        }
        rows.stream().filter(row -> !seen.contains(row.menuId())).forEach(ordered::add);
        return new Result(ordered.stream().filter(row -> menuLvl == null || row.menuLvl() == menuLvl).toList());
    }

    @Transactional
    public Result putCommonMenuSettings(Input input) {
        management.lockManagement();
        MenuSettingsRules.validate(input);
        Map<String, Item> current = new LinkedHashMap<>();
        mapper.selectSettings().forEach(row -> current.put(row.menuId, row.value()));
        Map<String, Item> next = new LinkedHashMap<>(current);
        for (var update : input.items()) {
            var old = current.get(update.menuId());
            if (old == null) throw invalid("menuId");
            if (old.menuLvl() == 1 && update.upperMenuId() != null) throw invalid("upperMenuId");
            if ("Y".equals(old.lockYn()) && !Objects.equals(old.upperMenuId(), update.upperMenuId()))
                throw new MenuSettingsFailure("upperMenuId", "시스템 메뉴는 다른 묶음으로 옮길 수 없습니다.");
            String use = old.menuLvl() == 1 || update.useYn() == null ? old.useYn() : update.useYn();
            String pc = old.menuLvl() == 1 || update.pcOnlyYn() == null ? old.pcOnlyYn() : update.pcOnlyYn();
            if (old.menuLvl() == 2 && "Y".equals(old.lockYn()) && "N".equals(use))
                throw new MenuSettingsFailure("useYn", "시스템 메뉴는 사용 해제할 수 없습니다.");
            next.put(old.menuId(), new Item(old.menuId(), update.upperMenuId(), update.menuNm(),
                old.scrnId(), old.menuLvl(), update.sortOrd(), use, pc, old.lockYn(), old.iconNm()));
        }
        validateTree(next);
        var moved = new HashSet<>(input.movedMenuIds());
        for (var update : input.items()) {
            var old = current.get(update.menuId());
            var value = next.get(update.menuId());
            if (old.equals(value)) continue;
            if (mapper.updateSetting(value) != 1) throw invalid("menuId");
            history(old.menuId(), "MENU_NM", old.menuNm(), value.menuNm());
            history(old.menuId(), "UPPER_MENU_ID", old.upperMenuId(), value.upperMenuId());
            history(old.menuId(), "USE_YN", old.useYn(), value.useYn());
            history(old.menuId(), "PC_ONLY_YN", old.pcOnlyYn(), value.pcOnlyYn());
            if (moved.contains(old.menuId()))
                history(old.menuId(), "SORT_ORD", String.valueOf(old.sortOrd()), String.valueOf(value.sortOrd()));
        }
        return getCommonMenuSettings(null);
    }

    private void validateTree(Map<String, Item> rows) {
        Map<String, HashSet<Integer>> orders = new HashMap<>();
        for (var row : rows.values()) {
            if (row.menuLvl() == 1) {
                if (row.upperMenuId() != null) throw invalid("upperMenuId");
            } else if (row.menuLvl() == 2) {
                var parent = rows.get(row.upperMenuId());
                if (parent == null || parent.menuLvl() != 1) throw invalid("upperMenuId");
            } else throw invalid("menuLvl");
            if (row.sortOrd() < 1 || row.sortOrd() > 99999) throw invalid("sortOrd");
            if (!orders.computeIfAbsent(row.upperMenuId(), key -> new HashSet<>()).add(row.sortOrd()))
                throw new MenuSettingsFailure("sortOrd", "같은 묶음의 순서를 중복 없이 반영해 주세요.");
        }
    }

    private void history(String id, String item, String before, String after) {
        if (!Objects.equals(before, after))
            management.insertHistory(new History("TB_SY_MENU", id, "UPD", item, before, after));
    }
}
