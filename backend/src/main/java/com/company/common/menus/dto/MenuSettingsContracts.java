package com.company.common.menus.dto;

import com.company.common.menus.dto.MenuRegistrationContracts.Item;
import java.time.Instant;
import java.util.List;

public final class MenuSettingsContracts {
    private MenuSettingsContracts() {}

    /** modifiedAt: 메뉴 표 전체의 마지막 수정 시각(동시 수정 확인 FR-0106). */
    public record Result(List<Item> items, Instant modifiedAt) {}
    public record Update(String menuId, String menuNm, String upperMenuId,
        Integer sortOrd, String useYn, String pcOnlyYn) {}
    /** checkVersion이 참이면 modifiedAt(읽었을 때의 메뉴 표 수정 시각)이 지금과 달라 저장을 막는다(409). */
    public record Input(List<Update> items, List<String> movedMenuIds, boolean checkVersion, Instant modifiedAt) {
        public Input(List<Update> items, List<String> movedMenuIds) { this(items, movedMenuIds, false, null); }
    }
}
