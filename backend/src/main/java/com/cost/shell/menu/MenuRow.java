package com.cost.shell.menu;

public record MenuRow(
    String menuId,
    String upperMenuId,
    String menuNm,
    String scrnId,
    int menuLvl,
    int sortOrd,
    String pcOnlyYn,
    String iconNm,
    String useYn
) {
    public MenuItem publicItem() {
        return new MenuItem(menuId, upperMenuId, menuNm, scrnId, menuLvl, sortOrd, pcOnlyYn, iconNm);
    }
}
