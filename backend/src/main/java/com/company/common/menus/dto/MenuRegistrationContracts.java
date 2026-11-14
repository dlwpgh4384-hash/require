package com.company.common.menus.dto;

import java.util.List;

public final class MenuRegistrationContracts {
    private MenuRegistrationContracts() {}

    public record Create(Integer menuLvl, String menuNm, String upperMenuId,
        String scrnId, String pcOnlyYn) {}
    public record Item(String menuId, String upperMenuId, String menuNm, String scrnId,
        int menuLvl, int sortOrd, String useYn, String pcOnlyYn, String lockYn, String iconNm) {}
    public record Parent(String menuId, String menuNm) {}
    public record Screen(String scrnId, String menuNm) {}
    public record Options(List<Parent> upperMenus, List<Screen> screens) {}
}
