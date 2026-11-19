package com.company.common.projects.service;

import com.company.common.common.ProjectBoardLifecycle;
import com.company.common.projects.dto.ProjectProgressContracts.*;
import com.company.common.projects.mapper.ProjectProgressMapper;
import com.company.common.projects.mapper.ProjectProgressMapper.MemoWrite;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectProgressService {
    private final ProjectProgressMapper mapper;
    private final ProjectStageService stages;
    private final BusinessCalendar calendar;
    private final CurrentBusinessActor actor;

    public ProjectProgressService(ProjectProgressMapper mapper, ProjectStageService stages,
        BusinessCalendar calendar, CurrentBusinessActor actor) {
        this.mapper = mapper;
        this.stages = stages;
        this.calendar = calendar;
        this.actor = actor;
    }
    public MemoList getCommonProjectBoardWeeklyMemos(String id) {
        stages.owner(id, false);
        return new MemoList(mapper.memos(id));
    }
    @Transactional
    public Memo postCommonProjectBoardWeeklyMemo(String id, JsonNode body) {
        var today = calendar.today();
        var p = stages.owner(id, true);
        ProjectBoardLifecycle.requireWritable(ProjectStageService.status(p, today),
            ProjectBoardLifecycle.Command.CREATE_MEMO);
        var input = ProjectProgressRulesInput.memo(body);
        ProjectProgressRulesInput.memoType(input, p.contractTypeCode());
        int sequence = mapper.nextMemoSequence(id);
        if (sequence < 1 || sequence > 99999) throw ProjectRules.invalid("memoId");
        mapper.insertMemo(new MemoWrite(id, sequence, input, today, actor.userId(), calendar.now()));
        return memo(id, Integer.toString(sequence));
    }
    @Transactional
    public Memo putCommonProjectBoardWeeklyMemo(String id, String memoId, JsonNode body) {
        int sequence = ProjectProgressRulesInput.sequence(memoId, "memoId", 5);
        var today = calendar.today();
        var p = stages.owner(id, true);
        var current = memo(id, memoId);
        ProjectBoardLifecycle.requireWritable(ProjectStageService.status(p, today),
            ProjectBoardLifecycle.Command.UPDATE_MEMO);
        var input = ProjectProgressRulesInput.memo(body, true);
        var token = ProjectProgressRulesInput.modifiedAt(body);
        ProjectProgressRulesInput.memoType(input, p.contractTypeCode());
        // 동시 수정(공통 00-1): 받은 modifiedAt이 지금 값과 다르면 409. DB도 같은 조건으로만 고친다.
        if (!java.util.Objects.equals(current.modifiedAt(), token)) throw CodeFailure.conflict();
        if (mapper.updateMemo(new MemoWrite(id, sequence, input, today, actor.userId(), calendar.now(), token)) != 1)
            throw CodeFailure.conflict();
        return memo(id, memoId);
    }
    private Memo memo(String id, String memoId) {
        return mapper.memos(id).stream().filter(m -> m.memoId().equals(memoId))
            .findFirst().orElseThrow(() -> ProjectStageService.missing("memoId"));
    }
}
