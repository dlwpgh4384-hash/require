package com.company.common.parts.mapper;

import com.company.common.users.mapper.UserRows;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.company.common.parts.dto.PartContracts.*;

@Mapper
public interface PartMapper {
    String selectCurrentMonth();
    void lockSources();
    List<PartRows.Master> selectParts(@Param("month") String month);
    PartRows.Master selectMaster(@Param("id") String id);
    List<PartRows.Period> selectPeriods(@Param("id") String id);
    List<PartRows.Leader> selectLeaders();
    boolean nameExists(@Param("name") String name, @Param("exceptId") String exceptId);
    int insertPart(@Param("id") String id, @Param("name") String name);
    int insertPeriod(PeriodMutation mutation);
    int updatePeriod(PeriodMutation mutation);
    int endPeriod(@Param("id") String id, @Param("month") String month, @Param("end") String end);
    int deletePeriod(@Param("id") String id, @Param("month") String month);
    int touch(@Param("id") String id, @Param("token") Instant token);
    int rename(@Param("id") String id, @Param("name") String name);
    int stop(@Param("id") String id);
    int resume(@Param("id") String id);
    String selectLastConfirmed(@Param("id") String id, @Param("month") String month);
    /** 시안 확정 안내 문구용: 적용 시작월 이후 마감 안 된 확정 달(오래된 달부터). */
    List<String> selectConfirmedMonths(@Param("id") String id, @Param("month") String month);
    List<UserRows.Change> selectHistory(@Param("id") String id);
    /** SER-006 접근 기록(UI-15): 영업 파트 목록 다운로드. */
    void insertAccess(@Param("userId") String userId, @Param("action") String action, @Param("target") String target);
}
