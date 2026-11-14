package com.company.common.menus.service;

import com.company.common.menus.dto.MenuRegistrationContracts.Create;
import com.company.common.menus.dto.MenuRegistrationContracts.Item;
import com.company.common.menus.dto.MenuRegistrationContracts.Options;
import com.company.common.menus.dto.MenuRegistrationContracts.Parent;
import com.company.common.users.dto.UserContracts.History;
import com.company.common.menus.mapper.MenuRegistrationMapper;
import com.company.common.users.mapper.UserMapper;
import java.math.BigInteger;
import java.util.Collections;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MenuRegistrationService {
    private final MenuRegistrationMapper mapper;
    private final UserMapper management;
    private final DevelopedScreenRegistry screens;

    public MenuRegistrationService(MenuRegistrationMapper mapper, UserMapper management,
        DevelopedScreenRegistry screens) {
        this.mapper = mapper;
        this.management = management;
        this.screens = screens;
    }

    public Options getCommonMenuOptions(Integer menuLvl) {
        MenuRegistrationRules.level(menuLvl);
        if (menuLvl == 1) return new Options(Collections.emptyList(), Collections.emptyList());
        return new Options(mapper.selectParents().stream()
            .map(row -> new Parent(row.menuId, row.menuNm)).toList(), screens.screens());
    }

    @Transactional
    public Item postCommonMenu(Create input) {
        management.lockManagement();
        MenuRegistrationRules.validate(input);
        boolean group = input.menuLvl() == 1;
        if (!group) {
            var parent = mapper.selectMenu(input.upperMenuId());
            if (parent == null || parent.menuLvl != 1)
                throw new MenuRegistrationFailure("upperMenuId", "유효한 상위 대메뉴를 선택해 주세요.");
            if (!screens.contains(input.scrnId()))
                throw new MenuRegistrationFailure("scrnId", "개발된 화면을 선택해 주세요.");
            if (mapper.screenExists(input.scrnId()))
                throw new MenuRegistrationFailure("scrnId", "이미 다른 메뉴에 등록된 화면입니다.");
        }
        String prefix = group ? "G" : "M";
        String suffix = mapper.selectIssuedNumber(prefix).add(BigInteger.ONE).toString();
        String id = prefix + "0".repeat(Math.max(0, 3 - suffix.length())) + suffix;
        int sort = mapper.selectNextSortOrder(input.upperMenuId());
        if (id.length() > 20 || sort < 1 || sort > 99999)
            throw new MenuRegistrationFailure("menuNm", "메뉴를 더 등록할 수 없습니다.");
        Item item = new Item(id, group ? null : input.upperMenuId(), input.menuNm(),
            group ? null : input.scrnId(), input.menuLvl(), sort, "Y",
            group ? "N" : input.pcOnlyYn(), "N", group ? "folder" : null);
        mapper.insertMenu(item);
        mapper.insertPermissions(id);
        management.insertHistory(new History("TB_SY_MENU", id, "NEW", "MENU_NM", null, input.menuNm()));
        return item;
    }
}
