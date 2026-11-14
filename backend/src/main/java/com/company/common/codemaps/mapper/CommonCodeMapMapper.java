package com.company.common.codemaps.mapper;

import com.company.common.codemaps.dto.CodeMapContracts.Item;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommonCodeMapMapper {
    List<CodeMapRow> selectMaps();
    CodeMapRow selectIdentity(Item item);
    boolean selectableCode(Item item);
    int insertMap(CodeMapRow row);
    int deleteMap(@Param("mapId") Long mapId);
}
