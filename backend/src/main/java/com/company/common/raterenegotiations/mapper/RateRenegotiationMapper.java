package com.company.common.raterenegotiations.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RateRenegotiationMapper {
    List<com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Cost> selectMonthlyCosts(@Param("yearMonth") String yearMonth);
    List<RateRenegotiationRows.AssignmentInfo> selectAssignmentInfo(@Param("yearMonth") String yearMonth,
        @Param("closed") boolean closed);
    int insertAccess(@Param("userId") String userId, @Param("target") String target);
}
