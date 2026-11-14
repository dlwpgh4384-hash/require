package com.company.common.menus.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.company.common.menus.mapper.MenuRow;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record MenuItem(String menuId, String upperMenuId, String menuNm, String scrnId,
                       int menuLvl, int sortOrd, String pcOnlyYn, String iconNm) {
    public static MenuItem from(MenuRow row) {
        return new MenuItem(row.getMenuId(), row.getUpperMenuId(), row.getMenuNm(), row.getScrnId(),
            row.getMenuLvl(), row.getSortOrd(), row.getPcOnlyYn(), row.getIconNm());
    }
}
