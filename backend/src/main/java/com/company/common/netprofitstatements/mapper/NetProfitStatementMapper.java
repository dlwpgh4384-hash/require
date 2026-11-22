package com.company.common.netprofitstatements.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NetProfitStatementMapper {
    int insertAccess(@Param("userId") String userId, @Param("target") String target);
}
