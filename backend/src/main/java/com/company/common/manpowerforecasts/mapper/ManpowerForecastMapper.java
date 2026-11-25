package com.company.common.manpowerforecasts.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ManpowerForecastMapper {
    int insertAccess(@Param("userId") String userId, @Param("count") int count);
}
