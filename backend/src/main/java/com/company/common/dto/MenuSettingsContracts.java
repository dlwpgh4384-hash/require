package com.company.common.dto;

import com.company.common.dto.MenuRegistrationContracts.Item;
import java.util.List;

public final class MenuSettingsContracts {
    private MenuSettingsContracts() {}

    public record Result(List<Item> items) {}
    public record Update(String menuId, String menuNm, String upperMenuId,
        Integer sortOrd, String useYn, String pcOnlyYn) {}
    public record Input(List<Update> items, List<String> movedMenuIds) {}
}
