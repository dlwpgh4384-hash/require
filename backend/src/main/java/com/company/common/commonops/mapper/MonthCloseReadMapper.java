package com.company.common.commonops.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MonthCloseReadMapper {
    boolean isClosed(@Param("month") String month);
    List<MonthCloseHistoryRow> selectHistory(@Param("month") String month);
}
