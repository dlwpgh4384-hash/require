package com.company.common.projectissues.service;

import com.company.common.common.ProjectBoardLifecycle;
import com.company.common.projectissues.dto.ProjectIssueContracts.*;
import com.company.common.projectissues.mapper.ProjectIssueMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectIssueService {
    private final ProjectIssueMapper mapper;
    private final BusinessCalendar calendar;
    private final CurrentBusinessActor actor;

    public ProjectIssueService(ProjectIssueMapper mapper, BusinessCalendar calendar, CurrentBusinessActor actor) {
        this.mapper = mapper;
        this.calendar = calendar;
        this.actor = actor;
    }

    public IssueList getCommonProjectBoardIssues(Filter filter) {
        if (filter.statusCode() != null) code("ISSUE_STAT", filter.statusCode(), "statusCode");
        if (filter.importanceCode() != null) code("IMPR", filter.importanceCode(), "importanceCode");
        if (filter.projectId() != null && (filter.projectId().isBlank() || filter.projectId().length() > 20))
            throw ProjectIssueRules.invalid("projectId");
        var rows = mapper.selectIssues(new Query(filter, calendar.today()));
        return new IssueList(rows, rows.stream().filter(i -> !"CLOSE".equals(i.statusCode)).count());
    }

    private void code(String group, String value, String field) {
        if (value == null || value.isBlank() || mapper.codeName(group, value) == null)
            throw ProjectIssueRules.invalid(field);
    }

    private void validate(Input input) {
        if (input.projectId() == null || input.projectId().isBlank() || input.projectId().length() > 20)
            throw ProjectIssueRules.invalid("projectId");
        if (input.title() == null || input.title().isBlank()
                || input.title().codePointCount(0, input.title().length()) > 100)
            throw ProjectIssueRules.invalid("title");
        if (input.dueDate() == null) throw ProjectIssueRules.invalid("dueDate");
        if (input.actionText() != null && input.actionText().codePointCount(0, input.actionText().length()) > 500)
            throw ProjectIssueRules.invalid("actionText");
        code("IMPR", input.importanceCode(), "importanceCode");
        code("ISSUE_STAT", input.statusCode(), "statusCode");
    }

    private ProjectState lockProject(String projectId) {
        var project = mapper.lockProject(projectId);
        if (project == null) throw missing("projectId");
        return project;
    }

    private static CodeFailure missing(String field) {
        return new CodeFailure(404, field, "대상을 찾을 수 없습니다. 다시 조회해 주세요.");
    }

    @Transactional
    public Issue postCommonProjectBoardIssue(Input input) {
        validate(input);
        var project = lockProject(input.projectId());
        var today = calendar.today();
        ProjectBoardLifecycle.requireWritable(
            ProjectBoardLifecycle.effectiveStatus(project.statusCode, project.endDate, today),
            ProjectBoardLifecycle.Command.CREATE_ISSUE);
        var write = new Write(null, input, actor.userId());
        mapper.insertIssue(write);
        return mapper.selectIssue(write.issueId, today);
    }

    @Transactional
    public Issue putCommonProjectBoardIssue(String issueId, Input input) {
        long id = ProjectIssueRules.issueId(issueId);
        validate(input);
        String projectId = mapper.issueProject(id);
        if (projectId == null) throw missing("issueId");
        if (!projectId.equals(input.projectId()))
            throw CodeFailure.invalid("projectId", "이슈의 프로젝트는 변경할 수 없습니다.");
        lockProject(projectId);
        if (mapper.lockIssue(id, projectId) == null) throw missing("issueId");
        if (mapper.updateIssue(new Write(id, input, actor.userId())) != 1) throw missing("issueId");
        return mapper.selectIssue(id, calendar.today());
    }
}
