package com.company.common.menus.service;

import com.company.common.menus.mapper.MenuRow;

public final class MenuFixtures {
    private MenuFixtures() {}

    public static MenuRow row(String id, String parent, String screen, int order) {
        MenuRow row = new MenuRow();
        row.setMenuId(id);
        row.setUpperMenuId(parent);
        row.setMenuNm("메뉴 " + id);
        row.setScrnId(screen);
        row.setMenuLvl(7);
        row.setSortOrd(order);
        row.setUseYn("Y");
        row.setPcOnlyYn("N");
        row.setIconNm(null);
        return row;
    }
}
