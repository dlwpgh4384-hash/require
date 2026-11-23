package com.company.common.menus.mapper;

import com.company.common.menus.dto.MenuRegistrationContracts.Item;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MenuSettingsMapper {
    List<MenuSettingsRow> selectSettings();
    int updateSetting(Item item);
    /** 메뉴 표 전체의 마지막 수정 시각(없으면 등록 시각). 표가 비면 null. */
    java.time.Instant selectVersion();
}
