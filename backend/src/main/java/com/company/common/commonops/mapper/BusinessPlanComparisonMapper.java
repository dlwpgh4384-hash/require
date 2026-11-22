package com.company.common.commonops.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.company.common.commonops.mapper.BusinessPlanComparisonRows.Target;

@Mapper
public interface BusinessPlanComparisonMapper {
    List<Target> targets(@Param("year") int year);
}
