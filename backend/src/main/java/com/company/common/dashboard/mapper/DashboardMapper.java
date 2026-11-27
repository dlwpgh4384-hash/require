package com.company.common.dashboard.mapper;

import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DashboardMapper {
    record Utilization(int totalPeople, int assignedPeople) {}
    Utilization utilization(@Param("month") String month, @Param("first") LocalDate first, @Param("last") LocalDate last);
    /** 가동률과 같은 데이터 범위의 인력 ID(전체 범위가 아닐 때 대기 인원을 셀 때 쓴다). */
    java.util.List<String> scopedDevelopers(@Param("month") String month);
    int insertAccess(@Param("actor") String actor, @Param("target") String target);
}
