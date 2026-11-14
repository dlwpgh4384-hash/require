package com.company.common.codehistory.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommonCodeHistoryMapper {
    List<CodeHistoryRow> selectChanges();
}
