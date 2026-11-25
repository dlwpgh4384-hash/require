package com.company.common.dashboard.mapper;

import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DashboardMapper {
    record Utilization(int totalPeople, int assignedPeople) {}
    Utilization utilization(@Param("month") String month, @Param("first") LocalDate first, @Param("last") LocalDate last);
    int insertAccess(@Param("actor") String actor, @Param("target") String target);
}
