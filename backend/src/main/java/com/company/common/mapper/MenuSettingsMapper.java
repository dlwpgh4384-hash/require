package com.company.common.mapper;

import com.company.common.dto.MenuRegistrationContracts.Item;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MenuSettingsMapper {
    List<MenuSettingsRow> selectSettings();
    int updateSetting(Item item);
}
