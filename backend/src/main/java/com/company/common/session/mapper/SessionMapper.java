package com.company.common.session.mapper;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SessionMapper {
    BigDecimal selectSessionMinutes(@Param("month") String month);
    String selectAccountStatus(@Param("userId") String userId);
    String selectTemporaryPassword(@Param("userId") String userId);
}
