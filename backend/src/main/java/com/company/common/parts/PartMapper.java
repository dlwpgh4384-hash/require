package com.company.common.parts;

import com.company.common.mapper.UserRows;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.company.common.parts.PartContracts.*;

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
    int touch(@Param("id") String id, @Param("token") Instant token);
    int rename(@Param("id") String id, @Param("name") String name);
    int stop(@Param("id") String id);
    String selectLastConfirmed(@Param("id") String id, @Param("month") String month);
    List<UserRows.Change> selectHistory(@Param("id") String id);
    /** SER-006 접근 기록(UI-15): 영업 파트 목록 다운로드. */
    void insertAccess(@Param("userId") String userId, @Param("action") String action, @Param("target") String target);
}
