package com.company.common.projects.mapper;

import com.company.common.common.ProjectBoardStageCalendar.Definition;
import com.company.common.common.ProjectBoardStageCalendar.Plan;
import com.company.common.projects.dto.ProjectProgressContracts.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectProgressMapper {
    record Owner(String projectId, String contractTypeCode, String statusCode,
        LocalDate startDate, LocalDate endDate) {}
    record MemoWrite(String projectId, int sequence, MemoInput input, LocalDate today,
        String actor, Instant now) {}
    record StageWrite(String projectId, int sequence, Plan plan, String actor) {}
    record DatesWrite(String projectId, StepDate dates, String actor, Instant now) {}
    record Completion(String projectId, int sequence, LocalDate today, String actor, Instant now) {}

    Owner owner(@Param("id") String id);
    Owner lockOwner(@Param("id") String id);
    List<Memo> memos(@Param("id") String id);
    int nextMemoSequence(@Param("id") String id);
    int insertMemo(MemoWrite write);
    int updateMemo(MemoWrite write);
    List<Step> steps(@Param("id") String id);
    List<Definition> stageDefinitions();
    int insertStage(StageWrite write);
    int updateDates(DatesWrite write);
    int complete(Completion write);
}
