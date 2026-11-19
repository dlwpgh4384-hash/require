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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectStageService {
    public record ProjectActivated(String projectId) {}
    private final ProjectProgressMapper mapper;
    private final BusinessCalendar calendar;
    private final CurrentBusinessActor actor;

    public ProjectStageService(ProjectProgressMapper mapper, BusinessCalendar calendar, CurrentBusinessActor actor) {
        this.mapper = mapper;
        this.calendar = calendar;
        this.actor = actor;
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
        return new StepList(mapper.steps(id));
    }
    @EventListener
    @Transactional
    public void projectActivated(ProjectActivated event) {
        postCommonProjectBoardStepsEnsure(event.projectId());
    }
    @Transactional
    public StepList postCommonProjectBoardStepsEnsure(String id) {
        var p = owner(id, true);
        var existing = mapper.steps(id);
        if (ProjectBoardLifecycle.shouldEnsure(status(p, calendar.today()), existing.size())) {
            var plans = ProjectBoardStageCalendar.allocate(p.contractTypeCode(), p.startDate(), p.endDate(),
                mapper.stageDefinitions());
            for (int i = 0; i < plans.size(); i++)
                mapper.insertStage(new StageWrite(id, i + 1, plans.get(i), actor.userId()));
        }
        return new StepList(mapper.steps(id));
    }
    @Transactional
    public StepList putCommonProjectBoardSteps(String id, StepDatesInput input) {
        var today = calendar.today();
        var p = owner(id, true);
        ProjectBoardLifecycle.requireWritable(status(p, today), ProjectBoardLifecycle.Command.UPDATE_DATES);
        var rows = mapper.steps(id);
        if (input == null || input.items() == null || input.items().isEmpty()
            || input.items().size() != rows.size()) throw ProjectRules.invalid("items");
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
                throw missing("stepId");
        }
        return new StepList(mapper.steps(id));
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
