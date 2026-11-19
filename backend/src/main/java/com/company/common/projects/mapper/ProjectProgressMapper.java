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
    /** now는 기록용으로만 남긴다. 등록·고친 시각(REG_DTTM·MOD_DTTM)은 DB가 clock_timestamp()로 적는다. token은 수정 때 받은 modifiedAt. */
    record MemoWrite(String projectId, int sequence, MemoInput input, LocalDate today,
        String actor, Instant now, Instant token) {
        public MemoWrite(String projectId, int sequence, MemoInput input, LocalDate today, String actor, Instant now) {
            this(projectId, sequence, input, today, actor, now, null);
        }
    }
    record StageWrite(String projectId, int sequence, Plan plan, String actor) {}
    record DatesWrite(String projectId, StepDate dates, String actor, Instant now) {}
    record Completion(String projectId, int sequence, LocalDate today, String actor, Instant now) {}

    Owner owner(@Param("id") String id);
    Owner lockOwner(@Param("id") String id);
    List<Memo> memos(@Param("id") String id);
    int nextMemoSequence(@Param("id") String id);
    int insertMemo(MemoWrite write);
    /** 받은 modifiedAt(token)이 지금 값과 같을 때만 고친다. 0건이면 그 사이 다른 사람이 고친 것(409). */
    int updateMemo(MemoWrite write);
    List<Step> steps(@Param("id") String id);
    /** 단계 전체의 동시 수정 확인 값: 단계들 중 가장 늦은 고친·등록 시각(없으면 null). */
    Instant stepsVersion(@Param("id") String id);
    /** 현황판에 나오는(종료·실주 아님, 계약 종료일 ≥ 오늘) 프로젝트 중 단계가 하나도 없는 것. */
    List<String> projectsWithoutStages(@Param("today") LocalDate today);
    List<Definition> stageDefinitions();
    int insertStage(StageWrite write);
    int updateDates(DatesWrite write);
    int complete(Completion write);
}
