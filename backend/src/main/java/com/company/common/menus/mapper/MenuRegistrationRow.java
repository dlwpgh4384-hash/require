package com.company.common.menus.mapper;

import com.company.common.menus.dto.MenuRegistrationContracts.Item;

public class MenuRegistrationRow {
    public String menuId;
    public String upperMenuId;
    public String menuNm;
    public String scrnId;
    public int menuLvl;
    public int sortOrd;
    public String useYn;
    public String pcOnlyYn;
    public String lockYn;
    public String iconNm;

    public Item value() {
        return new Item(menuId, upperMenuId, menuNm, scrnId, menuLvl, sortOrd,
            useYn, pcOnlyYn, lockYn, iconNm);
    }
}
