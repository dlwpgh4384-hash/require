package com.company.common.menus.service;

import com.company.common.menus.dto.MenuRegistrationContracts.Item;
import com.company.common.users.dto.UserContracts.History;
import com.company.common.menus.mapper.MenuSettingsMapper;
import com.company.common.menus.mapper.MenuSettingsRow;
import com.company.common.users.mapper.UserMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public final class MenuSettingsTestData {
    public final Map<String, Item> rows = new LinkedHashMap<>();
    public final List<History> histories = new ArrayList<>();

    public MenuSettingsTestData(MenuSettingsMapper mapper, UserMapper management) {
        add(new Item("G001", null, "합성 첫 묶음", null, 1, 1, "Y", "N", "N", "folder"));
        add(new Item("G002", null, "합성 시스템", null, 1, 2, "N", "Y", "Y", "gear"));
        add(new Item("M001", "G001", "첫 메뉴", "UI-01", 2, 1, "Y", "N", "N", null));
        add(new Item("M002", "G001", "중지 메뉴", "UI-02", 2, 2, "N", "N", "N", null));
        add(new Item("M003", "G001", "직접 메뉴", "UI-03", 2, 3, "Y", "N", "N", null));
        add(new Item("M004", "G002", "잠금 메뉴", "UI-24", 2, 1, "Y", "Y", "Y", null));
        when(mapper.selectSettings()).thenAnswer(call -> rows.values().stream().map(MenuSettingsTestData::row).toList());
        when(mapper.updateSetting(any())).thenAnswer(call -> {
            Item value = call.getArgument(0);
            rows.put(value.menuId(), value);
            return 1;
        });
        doAnswer(call -> { histories.add(call.getArgument(0)); return null; }).when(management).insertHistory(any());
    }

    private void add(Item value) { rows.put(value.menuId(), value); }

    static MenuSettingsRow row(Item value) {
        var row = new MenuSettingsRow();
        row.menuId = value.menuId(); row.upperMenuId = value.upperMenuId(); row.menuNm = value.menuNm();
        row.scrnId = value.scrnId(); row.menuLvl = value.menuLvl(); row.sortOrd = value.sortOrd();
        row.useYn = value.useYn(); row.pcOnlyYn = value.pcOnlyYn(); row.lockYn = value.lockYn(); row.iconNm = value.iconNm();
        return row;
    }
}
