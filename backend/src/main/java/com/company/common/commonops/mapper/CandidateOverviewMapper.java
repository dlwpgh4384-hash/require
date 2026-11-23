package com.company.common.commonops.mapper;

import com.company.common.commonops.dto.CandidateOverviewContracts.Summary;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CandidateOverviewMapper {
    record Dates(LocalDate today, LocalDate nextMonth, LocalDate overdueBefore) {}
    record CandidateCount(String jobCode, LocalDate availableDate) {}
    record Dictionary(String groupId, String code, String name, String target) {}
    record Company(String id, String name) {}
    record Area(String code, String sido, String sidoFull, String district) {}
    Summary summary(Dates dates);
    List<CandidateCount> activeCandidates();
    List<Dictionary> dictionary(@Param("groupId") String groupId);
    List<Company> companies();
    List<Area> areas();
}
