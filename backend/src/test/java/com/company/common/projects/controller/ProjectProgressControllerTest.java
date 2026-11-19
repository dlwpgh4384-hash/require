package com.company.common.projects.controller;

import com.company.common.projects.dto.ProjectProgressContracts.*;
import com.company.common.projects.mapper.ProjectProgressMapper;
import com.company.common.projects.mapper.ProjectProgressMapper.*;
import com.company.common.projects.service.*;
import com.company.common.common.ProjectBoardStageCalendar.Definition;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
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

@WebMvcTest(ProjectProgressController.class)
@ContextConfiguration(classes = {ProjectProgressController.class, ProjectProgressService.class,
    ProjectStageService.class, CodeExceptionHandler.class, ApiExceptionHandler.class, CurrentBusinessActor.class})
class ProjectProgressControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ProjectProgressMapper mapper;
    @MockBean BusinessCalendar calendar;
    @MockBean PlatformTransactionManager transactions;
    final String root = "/api/common/projects/P-0001";
    final LocalDate today = LocalDate.of(2026,11,15);
    final Instant now = Instant.parse("2026-11-15T01:00:00Z");
    Owner owner;
    List<Step> steps;
    List<Memo> memos;
    @BeforeEach void setup() {
        owner = new Owner("P-0001","TK","ING",today.minusDays(10),today.plusDays(59));
        steps = new ArrayList<>(); memos = new ArrayList<>();
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(calendar.today()).thenReturn(today); when(calendar.now()).thenReturn(now);
        when(mapper.owner(anyString())).thenAnswer(c -> "P-0001".equals(c.getArgument(0)) ? owner : null);
        when(mapper.lockOwner(anyString())).thenAnswer(c -> "P-0001".equals(c.getArgument(0)) ? owner : null);
        when(mapper.steps(anyString())).thenAnswer(c -> List.copyOf(steps));
        when(mapper.memos(anyString())).thenAnswer(c -> List.copyOf(memos));
        when(mapper.nextMemoSequence(anyString())).thenAnswer(c -> memos.size()+1);
        when(mapper.stageDefinitions()).thenAnswer(c -> {
            int count = Set.of("TK","IN").contains(owner.contractTypeCode()) ? 7 : 5;
            var definitions = new ArrayList<Definition>();
            for (int i=0;i<count;i++) definitions.add(new Definition("S"+i,"단계"+i,
                BigDecimal.valueOf(i==count-1 ? 100-(count-1)*10 : 10),owner.contractTypeCode(),i));
            return definitions;
        });
        when(mapper.insertStage(any())).thenAnswer(c -> {
            StageWrite w=c.getArgument(0); var p=w.plan();
            steps.add(new Step(""+w.sequence(),w.projectId(),p.name(),p.weight(),p.plannedStartDate(),p.plannedEndDate(),null)); return 1;
        });
        when(mapper.complete(any())).thenAnswer(c -> {
            Completion w=c.getArgument(0); for(int i=0;i<steps.size();i++) { var s=steps.get(i);
                if(s.stepId().equals(""+w.sequence())) steps.set(i,new Step(s.stepId(),s.projectId(),s.stepName(),s.weight(),s.plannedStartDate(),s.plannedEndDate(),w.today())); }
            return 1;
        });
        when(mapper.updateDates(any())).thenAnswer(c -> {
            DatesWrite w=c.getArgument(0); for(int i=0;i<steps.size();i++) { var s=steps.get(i);
                if(s.stepId().equals(w.dates().stepId())) steps.set(i,new Step(s.stepId(),s.projectId(),s.stepName(),s.weight(),w.dates().plannedStartDate(),w.dates().plannedEndDate(),s.completedDate())); }
            return 1;
        });
        when(mapper.insertMemo(any())).thenAnswer(c -> { MemoWrite w=c.getArgument(0); var b=w.input();
            memos.add(new Memo(""+w.sequence(),w.projectId(),b.workDone(),b.nextWork(),b.requests(),b.actualProgress(),w.now(),w.actor(),null,null));return 1; });
        when(mapper.updateMemo(any())).thenAnswer(c -> { MemoWrite w=c.getArgument(0); var b=w.input();
            for(int i=0;i<memos.size();i++) { var m=memos.get(i); if(m.memoId().equals(""+w.sequence()))
                memos.set(i,new Memo(m.memoId(),m.projectId(),b.workDone(),b.nextWork(),b.requests(),b.actualProgress(),m.createdAt(),m.createdBy(),w.now(),w.actor())); } return 1; });
    }
    void ensure() throws Exception { mvc.perform(post("/api/common/projects/P-0001/steps/ensure")).andExpect(status().isOk())
        .andExpect(response("POST", "/api/common/projects/{projectId}/steps/ensure")); }
    ObjectNode dates() {
        var body=json.createObjectNode(); var items=body.putArray("items");
        for(var s:steps) items.addObject().put("stepId",s.stepId()).put("plannedStartDate",s.plannedStartDate().toString()).put("plannedEndDate",s.plannedEndDate().toString());
        return body;
    }
    void memo() { memos.add(new Memo("1","P-0001","원문",null,null,10,now.minusSeconds(30*86400),"other",null,null)); }
    void closed(String status) { owner=new Owner(owner.projectId(),owner.contractTypeCode(),status,owner.startDate(),owner.endDate()); }

    @ParameterizedTest @ValueSource(strings={"TK","IN","MO","DP"})
    @DisplayName("happy auth side-effect: TB_PJ_STEP 기본 단계 생성 및 무인증 admin 조회")
    void publicEnsureCreatesContractStageCountAndReadback(String type) throws Exception {
        owner=new Owner("P-0001",type,"PRE",owner.startDate(),owner.endDate()); ensure();
        int count=Set.of("TK","IN").contains(type)?7:5;
        mvc.perform(get(root+"/steps")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(count));
        assertThat(steps.get(0).plannedStartDate()).isEqualTo(owner.startDate());
        assertThat(steps.get(count-1).plannedEndDate()).isEqualTo(owner.endDate());
        assertThat(steps).allMatch(s -> s.completedDate()==null);
        verify(mapper, times(count)).insertStage(argThat(w -> w.actor().equals("admin")));
    }
    @Test @DisplayName("business side-effect: TB_PJ_STEP 보완 재시도는 기존 행 보존")
    void ensureRetryPreservesRowsAndDates() throws Exception { ensure();var before=List.copyOf(steps);ensure();assertThat(steps).isEqualTo(before);verify(mapper,times(7)).insertStage(any()); }
    @ParameterizedTest @ValueSource(strings={"END","LOSE"})
    void ensureClosedIsNoop(String status) throws Exception { closed(status);ensure();assertThat(steps).isEmpty();verify(mapper,never()).insertStage(any()); }
    @Test void effectiveEndedEnsureIsNoop() throws Exception { owner=new Owner("P-0001","TK","ING",today.minusDays(30),today.minusDays(1));ensure();verify(mapper,never()).insertStage(any()); }
    @Test void invalidProjectIdDoesNotLockOrWrite() throws Exception { mvc.perform(post("/api/common/projects/bad!id/steps/ensure")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("projectId"));verify(mapper,never()).lockOwner(any()); }
    @Test void unknownProjectReadReturns404() throws Exception { mvc.perform(get("/api/common/projects/unknown/steps")).andExpect(status().isNotFound()); }
    @Test void getMemosDoesNotEnsure() throws Exception { memo();mvc.perform(get("/api/common/projects/P-0001/weekly-memos")).andExpect(status().isOk())
        .andExpect(response("GET", "/api/common/projects/{projectId}/weekly-memos"))
        .andExpect(jsonPath("$.data.items[0].memoId").value("1"));verify(mapper,never()).insertStage(any()); }
    @Test @DisplayName("happy auth side-effect: TB_PJ_STEP completedDate 완료 전이와 admin 기록")
    void publicCompleteAndRetryPreserveOriginalDateAndAudit() throws Exception {
        ensure();mvc.perform(post("/api/common/projects/P-0001/steps/1/complete")).andExpect(status().isOk())
            .andExpect(response("POST", "/api/common/projects/{projectId}/steps/{stepId}/complete"))
            .andExpect(jsonPath("$.data.completedDate").value(today.toString()));
        when(calendar.today()).thenReturn(today.plusDays(1));mvc.perform(post(root+"/steps/1/complete")).andExpect(status().isOk()).andExpect(jsonPath("$.data.completedDate").value(today.toString()));
        verify(mapper,times(1)).complete(argThat(w->w.actor().equals("admin")));
        mvc.perform(get(root+"/steps")).andExpect(jsonPath("$.data.items[0].completedDate").value(today.toString()));
    }
    @Test @DisplayName("business: 미시작 TB_PJ_STEP completedDate 불변")
    void unstartedStageCannotComplete() throws Exception { ensure();var before=List.copyOf(steps);mvc.perform(post("/api/common/projects/P-0001/steps/7/complete")).andExpect(status().isBadRequest())
        .andExpect(response("POST", "/api/common/projects/{projectId}/steps/{stepId}/complete"));assertThat(steps).isEqualTo(before); }
    @ParameterizedTest @ValueSource(strings={"END","LOSE"})
    void closedStageCannotComplete(String status) throws Exception { ensure();closed(status);mvc.perform(post(root+"/steps/1/complete")).andExpect(status().isBadRequest());verify(mapper,never()).complete(any()); }
    @ParameterizedTest @ValueSource(strings={"0","-1","abc","100"})
    void invalidStepIdentifiersDoNotWrite(String id) throws Exception { mvc.perform(post(root+"/steps/"+id+"/complete")).andExpect(status().isBadRequest());verify(mapper,never()).complete(any()); }
    @Test void foreignStepReturns404() throws Exception { mvc.perform(post("/api/common/projects/P-0001/steps/9/complete")).andExpect(status().isNotFound())
        .andExpect(response("POST", "/api/common/projects/{projectId}/steps/{stepId}/complete")); }
    @Test @DisplayName("happy auth side-effect: TB_PJ_WEEK_MEMO 등록과 latestMemoDate 원천 갱신")
    void publicCreateMemoGetsNewIdentityAndAdminWithOptionalTextAndProgress() throws Exception {
        mvc.perform(post("/api/common/projects/P-0001/weekly-memos").contentType("application/json").content("{}"))
            .andExpect(response("POST", "/api/common/projects/{projectId}/weekly-memos"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.memoId").value("1")).andExpect(jsonPath("$.data.createdBy").value("admin"));
        mvc.perform(get(root+"/weekly-memos")).andExpect(jsonPath("$.data.items.length()").value(1));
        verify(mapper).insertMemo(argThat(w -> w.today().equals(today) && w.now().equals(now)));
    }
    @ParameterizedTest @ValueSource(strings={"MO","DP"})
    void operationalBlankTextRejected(String type) throws Exception { owner=new Owner("P-0001",type,"ING",owner.startDate(),owner.endDate());mvc.perform(post(root+"/weekly-memos").contentType("application/json").content("{\"requests\":\"  \"}" )).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("workDone"));verify(mapper,never()).insertMemo(any()); }
    @Test void operationalProgressForbidden() throws Exception { owner=new Owner("P-0001","MO","ING",owner.startDate(),owner.endDate());mvc.perform(post(root+"/weekly-memos").contentType("application/json").content("{\"requests\":\"요청\",\"actualProgress\":0}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("actualProgress")); }
    @Test @DisplayName("validation: 주간 메모 201자 거부 및 TB_PJ_WEEK_MEMO 무변경")
    void longTextRejected() throws Exception {
        mvc.perform(post("/api/common/projects/P-0001/weekly-memos").contentType("application/json")
                .content(json.createObjectNode().put("nextWork", "가".repeat(201)).toString()))
            .andExpect(status().isBadRequest())
            .andExpect(response("POST", "/api/common/projects/{projectId}/weekly-memos"))
            .andExpect(jsonPath("$.errors[0].field").value("nextWork"));
        verify(mapper, never()).insertMemo(any());
    }
    @ParameterizedTest @ValueSource(strings={"END","LOSE"})
    @DisplayName("business: 종료·실주 주간 메모 등록 거부 및 latestMemoDate 원천 보존")
    void closedMemoCreateRejected(String status) throws Exception {
        memo(); closed(status); var before = List.copyOf(memos);
        mvc.perform(post("/api/common/projects/P-0001/weekly-memos").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(response("POST", "/api/common/projects/{projectId}/weekly-memos"));
        verify(mapper, never()).insertMemo(any()); assertThat(memos).isEqualTo(before);
    }
    @Test @DisplayName("happy auth side-effect: TB_PJ_WEEK_MEMO 동일 memoId 정정과 작성일 보존")
    void publicMemoEditPreservesCreationAndOtherRows() throws Exception { memo();var original=memos.get(0);memos.add(new Memo("2","P-0001",null,null,"다른 행",null,now,"admin",null,null));var other=memos.get(1);
        mvc.perform(put("/api/common/projects/P-0001/weekly-memos/1").contentType("application/json").content("{\"requests\":\"새 요청\"}" )).andExpect(status().isOk())
            .andExpect(response("PUT", "/api/common/projects/{projectId}/weekly-memos/{memoId}"))
            .andExpect(jsonPath("$.data.memoId").value("1")).andExpect(jsonPath("$.data.updatedBy").value("admin"));
        assertThat(memos.get(0).createdAt()).isEqualTo(original.createdAt());assertThat(memos.get(0).createdBy()).isEqualTo("other");assertThat(memos.get(0).updatedAt()).isEqualTo(now);assertThat(memos.get(1)).isEqualTo(other);
        mvc.perform(get(root+"/weekly-memos")).andExpect(jsonPath("$.data.items[0].requests").value("새 요청"));
    }
    @ParameterizedTest @ValueSource(strings={"1.5","-1","101"})
    @DisplayName("validation: 진척률 오류는 TB_PJ_WEEK_MEMO 동일 memoId 보존")
    void invalidProgressLeavesMemoUntouched(String value) throws Exception {
        memo(); var before = List.copyOf(memos);
        mvc.perform(put("/api/common/projects/P-0001/weekly-memos/1").contentType("application/json").content("{\"actualProgress\":" + value + "}"))
            .andExpect(status().isBadRequest()).andExpect(response("PUT", "/api/common/projects/{projectId}/weekly-memos/{memoId}"))
            .andExpect(jsonPath("$.errors[0].field").value("actualProgress"));
        assertThat(memos).isEqualTo(before); verify(mapper, never()).updateMemo(any());
    }
    @ParameterizedTest @ValueSource(strings={"END","LOSE"})
    @DisplayName("business: 종료·실주 TB_PJ_WEEK_MEMO 수정 거부와 작성일 보존")
    void closedMemoEditPreservesAuditAndText(String status) throws Exception {
        memo(); closed(status); var before = List.copyOf(memos);
        mvc.perform(put("/api/common/projects/P-0001/weekly-memos/1").contentType("application/json").content("{\"requests\":\"변경\"}"))
            .andExpect(status().isBadRequest()).andExpect(response("PUT", "/api/common/projects/{projectId}/weekly-memos/{memoId}"));
        assertThat(memos).isEqualTo(before); verify(mapper, never()).updateMemo(any());
    }
    @Test void missingMemoIs404NotUpsert() throws Exception { mvc.perform(put(root+"/weekly-memos/1").contentType("application/json").content("{}")).andExpect(status().isNotFound());verify(mapper,never()).insertMemo(any()); }
    @Test @DisplayName("happy auth side-effect: TB_PJ_STEP 날짜만 변경, 완료일·비중 보존")
    void validDateSaveChangesOnlyDates() throws Exception { ensure();var before=List.copyOf(steps);var body=dates();((ObjectNode)body.withArray("items").get(0)).put("plannedEndDate",steps.get(0).plannedEndDate().plusDays(1).toString());((ObjectNode)body.withArray("items").get(1)).put("plannedStartDate",steps.get(1).plannedStartDate().plusDays(1).toString());
        mvc.perform(put("/api/common/projects/P-0001/steps").contentType("application/json").content(body.toString())).andExpect(status().isOk())
            .andExpect(response("PUT", "/api/common/projects/{projectId}/steps"))
            .andExpect(jsonPath("$.data.items[0].plannedEndDate").value(steps.get(0).plannedEndDate().toString()));
        assertThat(steps).hasSize(before.size());for(int i=0;i<steps.size();i++) { assertThat(steps.get(i).weight()).isEqualTo(before.get(i).weight());assertThat(steps.get(i).stepName()).isEqualTo(before.get(i).stepName());assertThat(steps.get(i).completedDate()).isNull(); }
        verify(mapper,times(2)).updateDates(argThat(w->w.actor().equals("admin")));
    }
    @Test @DisplayName("validation: TB_PJ_STEP 역전 날짜는 전체 무변경")
    void reversedDateRejectedBeforeAnyWrite() throws Exception {
        ensure(); var before = List.copyOf(steps); var body = dates();
        ((ObjectNode)body.withArray("items").get(0)).put("plannedEndDate", owner.startDate().minusDays(1).toString());
        mvc.perform(put("/api/common/projects/P-0001/steps").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(response("PUT", "/api/common/projects/{projectId}/steps"))
            .andExpect(jsonPath("$.errors[0].field").value("plannedEndDate"));
        assertThat(steps).isEqualTo(before); verify(mapper, never()).updateDates(any());
    }
    @Test @DisplayName("business: TB_PJ_STEP 겹침은 원자적으로 거부")
    void overlapRejectedAtomically() throws Exception {
        ensure(); var before = List.copyOf(steps); var body = dates();
        ((ObjectNode)body.withArray("items").get(1)).put("plannedStartDate", steps.get(0).plannedEndDate().toString());
        mvc.perform(put("/api/common/projects/P-0001/steps").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(response("PUT", "/api/common/projects/{projectId}/steps"));
        assertThat(steps).isEqualTo(before); verify(mapper, never()).updateDates(any());
    }
    @Test void completedDatesMayBeEchoedButNeverChanged() throws Exception { ensure();mvc.perform(post(root+"/steps/1/complete")).andExpect(status().isOk());mvc.perform(put(root+"/steps").contentType("application/json").content(dates().toString())).andExpect(status().isOk());var body=dates();((ObjectNode)body.withArray("items").get(0)).put("plannedEndDate",steps.get(0).plannedEndDate().plusDays(1).toString());mvc.perform(put(root+"/steps").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());verify(mapper,never()).updateDates(any()); }
    @Test void duplicateStageIdsRejected() throws Exception { ensure();var body=dates();((ObjectNode)body.withArray("items").get(1)).put("stepId","1");mvc.perform(put(root+"/steps").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());verify(mapper,never()).updateDates(any()); }
    @Test void omittedStageRejected() throws Exception { ensure();var body=dates();body.withArray("items").remove(0);mvc.perform(put(root+"/steps").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());verify(mapper,never()).updateDates(any()); }
    @ParameterizedTest @ValueSource(strings={"END","LOSE"})
    void closedDateSaveRejected(String status) throws Exception { ensure();closed(status);mvc.perform(put(root+"/steps").contentType("application/json").content(dates().toString())).andExpect(status().isBadRequest());verify(mapper,never()).updateDates(any()); }
    @Test @DisplayName("validation: 잘못된 stepId 완료 요청은 TB_PJ_STEP completedDate 불변")
    void malformedCompletionHasFieldErrorAndNoWrite() throws Exception {
        mvc.perform(post("/api/common/projects/P-0001/steps/0/complete"))
            .andExpect(status().isBadRequest())
            .andExpect(response("POST", "/api/common/projects/{projectId}/steps/{stepId}/complete"))
            .andExpect(jsonPath("$.errors[0].field").value("stepId"));
        verify(mapper, never()).complete(any());
    }
}
