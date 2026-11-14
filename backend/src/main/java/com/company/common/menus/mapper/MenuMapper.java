package com.company.common.menus.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MenuMapper {
    List<MenuRow> selectCandidates();
}
