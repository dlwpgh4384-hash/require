package com.company.common.projectissues.controller;

import com.company.common.projectissues.dto.ProjectIssueContracts.*;
import com.company.common.projectissues.mapper.ProjectIssueMapper;
import com.company.common.projectissues.service.ProjectIssueService;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.company.common.support.controller.OpenApiResponseContract.response;

@WebMvcTest(ProjectIssueController.class)
@ContextConfiguration(classes = {ProjectIssueController.class, ProjectIssueService.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class, CurrentBusinessActor.class})
class ProjectIssueControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ProjectIssueMapper mapper;
    @MockBean BusinessCalendar calendar;
    @MockBean PlatformTransactionManager transactions;
    Map<Long, Issue> issues;
    ProjectState project;
    final LocalDate today = LocalDate.of(2026, 11, 15);
    final Instant now = Instant.parse("2026-11-15T00:00:00Z");

    @BeforeEach void setup() {
        issues = new LinkedHashMap<>();
        project = new ProjectState();
        project.projectId = "P-owned";
        project.statusCode = "ING";
        project.endDate = today.plusMonths(1);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(calendar.today()).thenReturn(today);
        when(mapper.lockProject("P-owned")).thenReturn(project);
        when(mapper.codeName(eq("IMPR"), anyString())).thenAnswer(c ->
            Set.of("HIGH", "MID", "LOW").contains(c.getArgument(1)) ? c.getArgument(1) : null);
        when(mapper.codeName(eq("ISSUE_STAT"), anyString())).thenAnswer(c ->
            Set.of("OPEN", "ACT", "CLOSE").contains(c.getArgument(1)) ? c.getArgument(1) : null);
        when(mapper.insertIssue(any())).thenAnswer(c -> {
            Write w = c.getArgument(0);
            w.issueId = (long) issues.size() + 1;
            var row = new Issue();
            row.issueId = w.issueId.toString();
            row.projectId = w.input.projectId();
            row.projectName = "담당 프로젝트";
            row.createdBy = w.actor;
            row.assigneeId = w.actor;
            row.assigneeName = w.actor;
            row.createdAt = now;
            apply(row, w);
            issues.put(w.issueId, row);
            return 1;
        });
        when(mapper.updateIssue(any())).thenAnswer(c -> {
            Write w = c.getArgument(0);
            var row = issues.get(w.issueId);
            apply(row, w);
            row.updatedBy = w.actor;
            row.updatedAt = now.plusSeconds(1);
            return 1;
        });
        when(mapper.issueProject(anyLong())).thenAnswer(c -> {
            var row = issues.get(c.getArgument(0));
            return row == null ? null : row.projectId;
        });
        when(mapper.lockIssue(anyLong(), anyString())).thenAnswer(c -> issues.containsKey(c.getArgument(0)) ? c.getArgument(0) : null);
        when(mapper.selectIssue(anyLong(), any())).thenAnswer(c -> issues.get(c.getArgument(0)));
        when(mapper.selectIssues(any())).thenAnswer(c -> {
            Query q = c.getArgument(0);
            return issues.values().stream()
                .filter(i -> q.filter().statusCode() == null ? Set.of("OPEN", "ACT").contains(i.statusCode) : q.filter().statusCode().equals(i.statusCode))
                .filter(i -> q.filter().importanceCode() == null || q.filter().importanceCode().equals(i.importanceCode))
                .filter(i -> q.filter().projectId() == null || q.filter().projectId().equals(i.projectId))
                .filter(i -> !Boolean.TRUE.equals(q.filter().overdue()) || i.dueDate.isBefore(q.today()) && !"CLOSE".equals(i.statusCode))
                .toList();
        });
    }

    void apply(Issue row, Write w) {
        row.title = w.input.title();
        row.dueDate = w.input.dueDate();
        row.importanceCode = w.input.importanceCode();
        row.statusCode = w.input.statusCode();
        row.actionText = w.input.actionText();
        row.overdueDays = "CLOSE".equals(row.statusCode) ? 0 : Math.max(0, (int) java.time.temporal.ChronoUnit.DAYS.between(row.dueDate, today));
    }

    ObjectNode input() {
        return json.createObjectNode().put("projectId", "P-owned").put("title", "기한 확인")
            .put("dueDate", "2026-11-14").put("importanceCode", "HIGH")
            .put("statusCode", "OPEN").put("actionText", "담당 확인");
    }

    void create() throws Exception {
        mvc.perform(post("/api/common/project-issues").contentType("application/json").content(input().toString()))
            .andExpect(status().isOk()).andExpect(response("POST", "/api/common/project-issues"))
            .andExpect(jsonPath("$.data.issueId").value("1"));
    }

    @Test void anonymousCreateAndReadbackFixOriginalActorWithoutAuthentication() throws Exception {
        create();
        mvc.perform(get("/api/common/project-issues"))
            .andExpect(status().isOk()).andExpect(response("GET", "/api/common/project-issues"))
            .andExpect(jsonPath("$.data.items[0].title").value("기한 확인"))
            .andExpect(jsonPath("$.data.items[0].assigneeId").value("admin"))
            .andExpect(jsonPath("$.data.items[0].createdBy").value("admin"))
            .andExpect(jsonPath("$.data.items[0].updatedBy").isEmpty())
            .andExpect(jsonPath("$.data.openCount").value(1));
        verify(mapper).insertIssue(argThat(w -> w.actor.equals("admin")));
    }

    @ParameterizedTest @ValueSource(strings = {"projectId", "title", "dueDate", "importanceCode", "statusCode"})
    void missingRequiredFieldDoesNotInsert(String field) throws Exception {
        var b = input(); b.remove(field);
        mvc.perform(post("/api/common/project-issues").contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
        verify(mapper, never()).insertIssue(any());
        assertThat(issues).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"title", "actionText"})
    void excessiveLengthDoesNotInsert(String field) throws Exception {
        var b = input().put(field, "가".repeat(field.equals("title") ? 101 : 501));
        mvc.perform(post("/api/common/project-issues").contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
        verify(mapper, never()).insertIssue(any());
        assertThat(issues).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"importanceCode", "statusCode"})
    void unregisteredCreateCodesCannotInsert(String field) throws Exception {
        mvc.perform(post("/api/common/project-issues").contentType("application/json").content(input().put(field, "UNKNOWN").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
        verify(mapper, never()).insertIssue(any());
    }

    @ParameterizedTest @ValueSource(strings = {"projectId", "title", "dueDate", "importanceCode", "statusCode"})
    void missingUpdateFieldsLeaveRowUnchanged(String field) throws Exception {
        create();
        String snapshot = json.writeValueAsString(issues);
        var b = input();
        b.remove(field);
        mvc.perform(put("/api/common/project-issues/1").contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
        verify(mapper, never()).updateIssue(any());
        assertThat(json.writeValueAsString(issues)).isEqualTo(snapshot);
    }

    @ParameterizedTest @ValueSource(strings = {"title", "actionText"})
    void longUpdateFieldsLeaveRowUnchanged(String field) throws Exception {
        create();
        String snapshot = json.writeValueAsString(issues);
        var b = input().put(field, "가".repeat(field.equals("title") ? 101 : 501));
        mvc.perform(put("/api/common/project-issues/1").contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
        verify(mapper, never()).updateIssue(any());
        assertThat(json.writeValueAsString(issues)).isEqualTo(snapshot);
    }

    @Test void maximumLengthsAndNullableActionAreAccepted() throws Exception {
        var b = input().put("title", "가".repeat(100)).put("actionText", "나".repeat(500));
        mvc.perform(post("/api/common/project-issues").contentType("application/json").content(b.toString())).andExpect(status().isOk());
        b.putNull("actionText");
        mvc.perform(post("/api/common/project-issues").contentType("application/json").content(b.toString())).andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(strings = {"assigneeId", "createdBy", "issueId", "updatedBy"})
    void callerCannotChooseIdentity(String field) throws Exception {
        mvc.perform(post("/api/common/project-issues").contentType("application/json").content(input().put(field, "other").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
        verify(mapper, never()).insertIssue(any());
    }

    @ParameterizedTest @ValueSource(strings = {"END", "LOSE"})
    void endedAndLostRejectCreateWithoutChangingExistingIssues(String state) throws Exception {
        create();
        String snapshot = json.writeValueAsString(issues);
        project.statusCode = state;
        mvc.perform(post("/api/common/project-issues").contentType("application/json").content(input().toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("projectId"));
        assertThat(json.writeValueAsString(issues)).isEqualTo(snapshot);
        verify(mapper, times(1)).insertIssue(any());
    }

    @Test void effectiveEndedRejectsCreateEvenIfStoredAsInProgress() throws Exception {
        project.endDate = today.minusDays(1);
        mvc.perform(post("/api/common/project-issues").contentType("application/json").content(input().toString())).andExpect(status().isBadRequest());
        verify(mapper, never()).insertIssue(any());
    }

    @Test void preContractProjectCanCreate() throws Exception {
        project.statusCode = "PRE";
        create();
    }

    @Test void unknownProjectIs404() throws Exception {
        mvc.perform(post("/api/common/project-issues").contentType("application/json").content(input().put("projectId", "unknown").toString()))
            .andExpect(status().isNotFound());
        verify(mapper, never()).insertIssue(any());
    }

    @Test void putChangesSameRowAndPreservesOriginalIdentityAndAudit() throws Exception {
        create();
        var b = input().put("title", "수정 제목").put("actionText", "새 조치");
        mvc.perform(put("/api/common/project-issues/1").contentType("application/json").content(b.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.createdAt").value(now.toString()))
            .andExpect(jsonPath("$.data.updatedAt").value(now.plusSeconds(1).toString()));
        mvc.perform(get("/api/common/project-issues"))
            .andExpect(jsonPath("$.data.items[0].issueId").value("1"))
            .andExpect(jsonPath("$.data.items[0].title").value("수정 제목"))
            .andExpect(jsonPath("$.data.items[0].actionText").value("새 조치"))
            .andExpect(jsonPath("$.data.items[0].assigneeId").value("admin"));
        assertThat(issues).hasSize(1);
        var order = inOrder(mapper);
        order.verify(mapper).issueProject(1L);
        order.verify(mapper).lockProject("P-owned");
        order.verify(mapper).lockIssue(1L, "P-owned");
        order.verify(mapper).updateIssue(any());
    }

    @ParameterizedTest @ValueSource(strings = {"importanceCode", "statusCode"})
    void unregisteredCodesPreserveExistingValues(String field) throws Exception {
        create();
        String snapshot = json.writeValueAsString(issues);
        mvc.perform(put("/api/common/project-issues/1").contentType("application/json").content(input().put(field, "NO_CODE").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
        verify(mapper, never()).updateIssue(any());
        assertThat(json.writeValueAsString(issues)).isEqualTo(snapshot);
    }

    @ParameterizedTest @ValueSource(strings = {"END", "LOSE"})
    @DisplayName("business side-effect: 종료·실주 TB_PJ_ISSUE 동일 행 ISSUE_STAT 정정 허용")
    void existingEndedAndLostIssuesCanCloseAndReopen(String state) throws Exception {
        create(); project.statusCode = state; project.endDate = today.minusYears(1);
        mvc.perform(put("/api/common/project-issues/1").contentType("application/json").content(input().put("statusCode", "CLOSE").toString()))
            .andExpect(status().isOk()).andExpect(response("PUT", "/api/common/project-issues/{issueId}"))
            .andExpect(jsonPath("$.data.statusCode").value("CLOSE"));
        assertThat(issues).hasSize(1);
        mvc.perform(get("/api/common/project-issues").param("statusCode", "CLOSE"))
            .andExpect(jsonPath("$.data.items[0].issueId").value("1")).andExpect(jsonPath("$.data.openCount").value(0));
        verify(mapper).updateIssue(argThat(w -> w.issueId == 1L && w.input.statusCode().equals("CLOSE")));
        mvc.perform(put("/api/common/project-issues/1").contentType("application/json").content(input().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.statusCode").value("OPEN"));
    }

    @Test void putCannotMoveProject() throws Exception {
        create();
        mvc.perform(put("/api/common/project-issues/1").contentType("application/json").content(input().put("projectId", "P-other").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("projectId"));
        verify(mapper, never()).updateIssue(any());
    }

    @Test void separatelySuppliedEditorChangesOnlyModificationIdentity() throws Exception {
        create();
        var editor = new CurrentBusinessActor() {
            @Override public String userId() { return "other-editor"; }
        };
        var service = new ProjectIssueService(mapper, calendar, editor);
        var result = service.putCommonProjectBoardIssue("1", new Input("P-owned", "정정", today,
            "MID", "ACT", "처리"));
        assertThat(result.assigneeId).isEqualTo("admin");
        assertThat(result.createdBy).isEqualTo("admin");
        assertThat(result.createdAt).isEqualTo(now);
        assertThat(result.updatedBy).isEqualTo("other-editor");
    }

    @Test void futureAssignedProjectPolicyRemainsSeparateFromPublicHttp() {
        var first = new com.company.common.users.service.UserScopePolicy.Grant("PROJ", Set.of(), Set.of("P-a"));
        var second = new com.company.common.users.service.UserScopePolicy.Grant("PROJ", Set.of(), Set.of("P-b"));
        assertThat(com.company.common.projects.service.ProjectBusinessPolicy.canSaveManpower(
            Set.of("PM"), List.of(first, second), "P-b")).isTrue();
        assertThat(com.company.common.projects.service.ProjectBusinessPolicy.canSaveManpower(
            Set.of("PM"), List.of(first, second), "P-other")).isFalse();
    }

    @Test void missingIssueIs404() throws Exception {
        mvc.perform(put("/api/common/project-issues/123").contentType("application/json").content(input().toString())).andExpect(status().isNotFound());
        verify(mapper, never()).updateIssue(any());
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.0", "10000000000", "abc"})
    void malformedNumericIdentityIs400(String id) throws Exception {
        mvc.perform(put("/api/common/project-issues/" + id).contentType("application/json").content(input().toString())).andExpect(status().isBadRequest());
        verify(mapper, never()).updateIssue(any());
    }

    @Test void defaultQueryIncludesOpenAndActAndFilteredCountUsesSameCollection() throws Exception {
        create();
        mvc.perform(post("/api/common/project-issues").contentType("application/json").content(input().put("statusCode", "ACT").put("dueDate", today.toString()).toString())).andExpect(status().isOk());
        mvc.perform(post("/api/common/project-issues").contentType("application/json").content(input().put("statusCode", "CLOSE").toString())).andExpect(status().isOk());
        mvc.perform(get("/api/common/project-issues")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(2)).andExpect(jsonPath("$.data.openCount").value(2));
        mvc.perform(get("/api/common/project-issues").param("overdue", "true").param("importanceCode", "HIGH").param("projectId", "P-owned"))
            .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.openCount").value(1))
            .andExpect(jsonPath("$.data.items[0].overdueDays").value(1));
        mvc.perform(get("/api/common/project-issues").param("overdue", "false"))
            .andExpect(jsonPath("$.data.openCount").value(2));
    }

    @Test void unknownQueryCodeIs400() throws Exception {
        mvc.perform(get("/api/common/project-issues").param("statusCode", "unknown"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("statusCode"));
    }

    @Test void noDeleteEndpointExists() throws Exception {
        mvc.perform(delete("/api/common/project-issues/1")).andExpect(status().isMethodNotAllowed());
    }
}
