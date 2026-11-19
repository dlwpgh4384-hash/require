package com.company.common.projects.service;

import com.company.common.common.ProjectBoardLifecycle;
import com.company.common.common.ProjectBoardStageCalendar;
import com.company.common.projects.dto.ProjectProgressContracts.*;
import com.company.common.projects.mapper.ProjectProgressMapper;
import com.company.common.projects.mapper.ProjectProgressMapper.*;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(readOnly = true)
public class ProjectStageService {
    private static final Logger log = LoggerFactory.getLogger(ProjectStageService.class);
    public record ProjectActivated(String projectId) {}
    private final ProjectProgressMapper mapper;
    private final BusinessCalendar calendar;
    private final CurrentBusinessActor actor;
    private final TransactionTemplate separate;

    public ProjectStageService(ProjectProgressMapper mapper, BusinessCalendar calendar, CurrentBusinessActor actor,
        PlatformTransactionManager transactions) {
        this.mapper = mapper;
        this.calendar = calendar;
        this.actor = actor;
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public static CodeFailure missing(String field) {
        return new CodeFailure(404, field, "대상을 찾을 수 없습니다. 다시 불러와 주세요.");
    }
    public Owner owner(String id, boolean lock) {
        ProjectProgressRulesInput.projectId(id);
        var owner = lock ? mapper.lockOwner(id) : mapper.owner(id);
        if (owner == null) throw missing("projectId");
        return owner;
    }
    public static String status(Owner p, LocalDate today) {
        return ProjectBoardLifecycle.effectiveStatus(p.statusCode(), p.endDate(), today);
    }
    public StepList getCommonProjectBoardSteps(String id) {
        owner(id, false);
        return list(id);
    }
    private StepList list(String id) {
        return new StepList(mapper.steps(id), mapper.stepsVersion(id));
    }
    /**
     * 등록·수주 처리 때 기본 단계를 만든다. 저장점(NESTED) 안에서 하므로 실패하면 단계만 되돌리고,
     * 부른 쪽(프로젝트 저장)이 실패를 기록하고 저장을 끝낸다(현황판을 열 때 다시 만든다).
     */
    @EventListener
    @Transactional(propagation = Propagation.NESTED)
    public void projectActivated(ProjectActivated event) {
        ensure(event.projectId());
    }
    @Transactional
    public StepList postCommonProjectBoardStepsEnsure(String id) {
        ensure(id);
        return list(id);
    }
    /** 프로젝트 줄을 잠그고 단계가 0개일 때만 만든다(여러 번 불러도 한 벌). 종료·실주는 만들지 않는다. */
    private void ensure(String id) {
        var p = owner(id, true);
        var existing = mapper.steps(id);
        if (ProjectBoardLifecycle.shouldEnsure(status(p, calendar.today()), existing.size())) {
            var plans = ProjectBoardStageCalendar.allocate(p.contractTypeCode(), p.startDate(), p.endDate(),
                mapper.stageDefinitions());
            for (int i = 0; i < plans.size(); i++)
                mapper.insertStage(new StageWrite(id, i + 1, plans.get(i), actor.userId()));
        }
    }
    /**
     * 프로젝트 현황판(UI-31)을 열 때: 현황판에 나오는데 단계가 없는 프로젝트(이전에 등록한 것 포함)에 기본 단계를 만든다(시안 ensurePMD).
     * 프로젝트마다 따로 저장하고, 한 프로젝트가 실패해도 기록만 남기고 다음으로 넘어간다(화면은 그대로 보인다).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void ensureMissingStages() {
        LocalDate today = calendar.today();
        java.util.List<String> ids;
        try {
            ids = mapper.projectsWithoutStages(today);
        } catch (RuntimeException failure) {
            log.warn("단계가 없는 프로젝트를 찾지 못했습니다.", failure);
            return;
        }
        if (ids == null) return;
        for (String id : ids) {
            try {
                separate.executeWithoutResult(status -> ensure(id));
            } catch (RuntimeException failure) {
                log.warn("프로젝트 {} 기본 단계를 만들지 못했습니다.", id, failure);
            }
        }
    }
    /**
     * 단계 계획 저장. 받은 modifiedAt이 지금 단계 값과 다르면(그 사이 다른 사람이 날짜를 고치거나 완료 처리) 409.
     */
    @Transactional
    public StepList putCommonProjectBoardSteps(String id, StepDatesInput input) {
        var today = calendar.today();
        var p = owner(id, true);
        ProjectBoardLifecycle.requireWritable(status(p, today), ProjectBoardLifecycle.Command.UPDATE_DATES);
        var rows = mapper.steps(id);
        if (input == null || input.items() == null || input.items().isEmpty()
            || input.items().size() != rows.size()) throw ProjectRules.invalid("items");
        if (!Objects.equals(mapper.stepsVersion(id), input.modifiedAt())) throw CodeFailure.conflict();
        var byId = new HashMap<String, StepDate>();
        for (var item : input.items()) {
            ProjectProgressRulesInput.sequence(item.stepId(), "stepId", 2);
            if (byId.put(item.stepId(), item) != null) throw ProjectRules.invalid("stepId");
        }
        for (var item : input.items())
            if (rows.stream().noneMatch(row -> row.stepId().equals(item.stepId()))) throw missing("stepId");
        LocalDate previousEnd = null;
        boolean shortPeriod = ChronoUnit.DAYS.between(p.startDate(), p.endDate()) + 1 < rows.size();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            var dates = byId.get(row.stepId());
            if (dates == null) throw ProjectRules.invalid("items");
            var start = dates.plannedStartDate();
            var end = dates.plannedEndDate();
            if (start == null) throw ProjectRules.invalid("plannedStartDate");
            if (end == null || end.isBefore(start)) throw ProjectRules.invalid("plannedEndDate");
            if (start.isBefore(p.startDate()) || end.isAfter(p.endDate())) throw ProjectRules.invalid("plannedEndDate");
            if (row.completedDate() != null && changed(row, dates))
                throw CodeFailure.invalid("plannedStartDate", "완료한 단계의 날짜는 바꿀 수 없습니다.");
            if (i == 0 && !start.equals(p.startDate())) throw ProjectRules.invalid("plannedStartDate");
            if (previousEnd != null && !start.equals(previousEnd.plusDays(1))
                && !(shortPeriod && start.equals(previousEnd)))
                throw CodeFailure.invalid("plannedStartDate", "단계 기간은 겹침이나 단절 없이 이어져야 합니다.");
            if (i == rows.size() - 1 && !end.equals(p.endDate())) throw ProjectRules.invalid("plannedEndDate");
            previousEnd = end;
        }
        var now = calendar.now();
        for (var row : rows) {
            var dates = byId.get(row.stepId());
            if (changed(row, dates) && mapper.updateDates(new DatesWrite(id, dates, actor.userId(), now)) != 1)
                throw CodeFailure.conflict();
        }
        return list(id);
    }
    private static boolean changed(Step old, StepDate dates) {
        return !Objects.equals(old.plannedStartDate(), dates.plannedStartDate())
            || !Objects.equals(old.plannedEndDate(), dates.plannedEndDate());
    }
    @Transactional
    public Step postCommonProjectBoardStepComplete(String id, String stepId) {
        int sequence = ProjectProgressRulesInput.sequence(stepId, "stepId", 2);
        var today = calendar.today();
        var p = owner(id, true);
        ProjectBoardLifecycle.requireWritable(status(p, today), ProjectBoardLifecycle.Command.COMPLETE_STAGE);
        var row = mapper.steps(id).stream().filter(s -> s.stepId().equals(stepId))
            .findFirst().orElseThrow(() -> missing("stepId"));
        if (row.completedDate() != null) return row;
        if (row.plannedStartDate().isAfter(today))
            throw CodeFailure.invalid("stepId", "아직 시작하지 않은 단계입니다.");
        if (mapper.complete(new Completion(id, sequence, today, actor.userId(), calendar.now())) != 1)
            throw missing("stepId");
        return mapper.steps(id).stream().filter(s -> s.stepId().equals(stepId))
            .findFirst().orElseThrow(() -> missing("stepId"));
    }
}
