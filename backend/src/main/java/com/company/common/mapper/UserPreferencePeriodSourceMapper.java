package com.company.common.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserPreferencePeriodSourceMapper {
    BigDecimal selectCashBaseYm();
    List<String> selectClosedMonths();
}
