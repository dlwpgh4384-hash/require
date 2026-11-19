package com.company.common.projectissues.service;

import com.company.common.common.ProjectBoardLifecycle;
import com.company.common.projectissues.dto.ProjectIssueContracts.*;
import com.company.common.projectissues.mapper.ProjectIssueMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.support.service.ExcelExportService;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectIssueService {
    static final String SCREEN = "프로젝트 이슈";
    private final ProjectIssueMapper mapper;
    private final BusinessCalendar calendar;
    private final CurrentBusinessActor actor;
    private final ExcelExportService excel;

    public ProjectIssueService(ProjectIssueMapper mapper, BusinessCalendar calendar, CurrentBusinessActor actor) {
        this(mapper, calendar, actor, new ExcelExportService());
    }

    @Autowired
    public ProjectIssueService(ProjectIssueMapper mapper, BusinessCalendar calendar, CurrentBusinessActor actor,
            ExcelExportService excel) {
        this.mapper = mapper;
        this.calendar = calendar;
        this.actor = actor;
        this.excel = excel;
    }

    public IssueList getCommonProjectBoardIssues(Filter filter) {
        if (filter.statusCode() != null) code("ISSUE_STAT", filter.statusCode(), "statusCode");
        if (filter.importanceCode() != null) code("IMPR", filter.importanceCode(), "importanceCode");
        if (filter.projectId() != null && (filter.projectId().isBlank() || filter.projectId().length() > 20))
            throw ProjectIssueRules.invalid("projectId");
        var rows = mapper.selectIssues(new Query(filter, calendar.today()));
        return new IssueList(rows, rows.stream().filter(i -> !"CLOSE".equals(i.statusCode)).count());
    }

    public record ExcelFile(byte[] bytes, String fileName, int count) {}

    /**
     * 이슈 모아 보기 엑셀(SFR-011): 목록과 같은 조건의 전체 결과를 화면 열 순서(중요도·제목·프로젝트·담당자·기한·상태·조치 내용·등록일)로.
     * 파일 1행에 받은 사람·한국 시각, 접근 기록 DNLD(SER-006).
     */
    @Transactional
    public ExcelFile getCommonProjectBoardIssuesExcel(Filter filter) {
        var items = getCommonProjectBoardIssues(filter).items();
        var korean = DateTimeFormatter.ofPattern("uuuu-MM-dd").withZone(java.time.ZoneId.of("Asia/Seoul"));
        List<List<String>> rows = items.stream().map(i -> List.of(
            name("IMPR", i.importanceCode), text(i.title), text(i.projectName), text(i.assigneeName),
            i.dueDate == null ? "–" : i.dueDate + (i.overdueDays > 0 && !"CLOSE".equals(i.statusCode)
                ? " (" + i.overdueDays + "일 지남)" : ""),
            name("ISSUE_STAT", i.statusCode), text(i.actionText),
            i.createdAt == null ? "–" : korean.format(i.createdAt))).toList();
        var now = calendar.now();
        byte[] bytes = excel.write(SCREEN, List.of("중요도", "제목", "프로젝트", "담당자", "기한", "상태", "조치 내용", "등록일"),
            rows, actor.userId(), now);
        mapper.insertAccess(actor.userId(), SCREEN + " " + items.size() + "건");
        return new ExcelFile(bytes, excel.fileName(SCREEN, null, now), items.size());
    }

    private String name(String group, String code) {
        if (code == null) return "–";
        String found = mapper.codeName(group, code);
        return found == null ? code : found;
    }

    private static String text(String value) { return value == null || value.isBlank() ? "–" : value; }

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

    /** 이슈 수정. 받은 modifiedAt이 지금 값과 다르면(그 사이 다른 사람이 고침) 409(공통 문구). */
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
        var today = calendar.today();
        var current = mapper.selectIssue(id, today);
        if (current == null) throw missing("issueId");
        if (!Objects.equals(current.modifiedAt, input.modifiedAt())) throw CodeFailure.conflict();
        if (mapper.updateIssue(new Write(id, input, actor.userId())) != 1) throw CodeFailure.conflict();
        return mapper.selectIssue(id, today);
    }
}
