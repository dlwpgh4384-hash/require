package com.cost.shell.menu;

public record MenuItem(
    String menuId,
    String upperMenuId,
    String menuNm,
    String scrnId,
    int menuLvl,
    int sortOrd,
    String pcOnlyYn,
    String iconNm
) {
}
