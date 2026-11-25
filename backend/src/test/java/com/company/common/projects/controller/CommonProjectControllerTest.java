package com.company.common.projects.controller;

import com.company.common.projects.dto.ProjectContracts.*;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.projects.mapper.ProjectRows.*;
import com.company.common.projects.service.CommonProjectService;
import com.company.common.projects.service.ProjectAmountRules;
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

@WebMvcTest(CommonProjectController.class)
@ContextConfiguration(classes = {CommonProjectController.class, CommonProjectService.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class, CurrentBusinessActor.class, ExcelExportService.class,
    com.company.common.projects.service.ProjectStageService.class})
class CommonProjectControllerTest {
    @org.springframework.boot.test.mock.mockito.MockBean com.company.common.projects.service.ProjectProgressRules progressRules;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ProjectMapper mapper;
    @MockBean com.company.common.projects.mapper.ProjectProgressMapper progressMapper;
    @MockBean BusinessCalendar calendar;
    @MockBean ProjectAmountRules amounts;
    @MockBean PlatformTransactionManager transactions;
    Map<String, Project> projects;
    List<Audit> history;
    List<Expense> expenses;
    List<SalesPart> parts;
    List<Manpower> plans;
    final LocalDate today = LocalDate.of(2026, 11, 15);

    @BeforeEach void setup() throws Exception {
        String contract = new org.springframework.core.io.ClassPathResource("contracts/openapi.yaml")
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(contract).contains("operationId: getCommonProjects", "operationId: postCommonProject",
            "operationId: getCommonProjectChangeHistory", "operationId: getCommonProjectSalesParts",
            "operationId: getCommonProjectManpowerPlan", "operationId: postCommonProjectDirectExpense");
        projects = new LinkedHashMap<>(); history = new ArrayList<>(); expenses = new ArrayList<>();
        parts = new ArrayList<>(); plans = new ArrayList<>();
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(calendar.today()).thenReturn(today);
        when(calendar.currentMonth()).thenReturn(YearMonth.from(today));
        when(calendar.now()).thenReturn(Instant.parse("2026-11-15T00:00:00Z"));
        when(mapper.selectProject(anyString(), any(), anyString())).thenAnswer(c -> projects.get(c.getArgument(0)));
        when(mapper.selectProjects(any(), anyString())).thenAnswer(c -> new ArrayList<>(projects.values()));
        when(mapper.lockProject(anyString())).thenAnswer(c -> projects.containsKey(c.getArgument(0)) ? c.getArgument(0) : null);
        when(mapper.projectIds()).thenAnswer(c -> new ArrayList<>(projects.keySet()));
        when(mapper.codeName(anyString(), anyString())).thenAnswer(c -> c.getArgument(1));
        when(mapper.eligibleCustomer("customer")).thenReturn(true);
        when(mapper.customerName("customer")).thenReturn("고객사");
        when(mapper.eligiblePart(anyString())).thenReturn(true);
        when(mapper.allowance(anyString())).thenReturn(new BigDecimal("50000"));
        when(amounts.automaticBudget(any(), any())).thenAnswer(c -> ((BigDecimal)c.getArgument(0)).multiply(new BigDecimal("0.8")));
        when(mapper.insertProject(any())).thenAnswer(c -> { Write w = c.getArgument(0); w.project().statusName=w.project().statusCode; projects.put(w.project().projectId, w.project()); return 1; });
        when(mapper.updateProject(any())).thenAnswer(c -> { Write w = c.getArgument(0); w.project().statusName=w.project().statusCode; projects.put(w.project().projectId, w.project()); return 1; });
        doAnswer(c -> { history.add(c.getArgument(0)); return null; }).when(mapper).insertHistory(any());
        when(mapper.outside(anyString(), any(), any(), any())).thenReturn(new Outside());
        when(mapper.expenses(anyString())).thenAnswer(c -> new ArrayList<>(expenses));
        when(mapper.nextExpenseSequence(anyString(), anyString())).thenReturn(1);
        doAnswer(c -> { ExpenseWrite w = c.getArgument(0); var e = new Expense();
            e.expenseId = w.key().month()+":"+w.key().sequence(); e.yearMonth=w.input().yearMonth();
            e.itemCode=w.input().itemCode(); e.itemName=e.itemCode; e.amount=w.input().amount(); e.memo=w.input().memo();
            expenses.add(e); return null; }).when(mapper).insertExpense(any());
        when(mapper.deleteExpense(any())).thenAnswer(c -> { ExpenseKey k=c.getArgument(0);
            return expenses.removeIf(e -> e.expenseId.equals(k.month()+":"+k.sequence())) ? 1 : 0; });
        when(mapper.salesParts(anyString(), anyString())).thenAnswer(c -> new ArrayList<>(parts));
        doAnswer(c -> { PartWrite w=c.getArgument(0); var p=new SalesPart(); p.salesPartId=w.partId(); p.salesPartName=w.partId();
            p.effectiveMonth=w.month().substring(0,4)+"-"+w.month().substring(4); p.reason=w.reason(); parts.add(p); return null;
        }).when(mapper).insertPart(any());
        doAnswer(c -> { if (parts.size()>1) parts.subList(1,parts.size()).clear(); return null; }).when(mapper).deleteOpenParts(anyString(),anyString());
        when(mapper.manpower(anyString())).thenAnswer(c -> new ArrayList<>(plans));
        doAnswer(c -> { PlanWrite w=c.getArgument(0); var p=new Manpower(); p.jobCode=w.job().jobCode(); p.jobName=p.jobCode;
            p.plannedMm=w.job().plannedMm(); p.actualMm=BigDecimal.ONE; p.differenceMm=p.actualMm.subtract(p.plannedMm);
            plans.removeIf(x -> x.jobCode.equals(p.jobCode)); plans.add(p); return null; }).when(mapper).savePlan(any());
        doAnswer(c -> { projects.get((String)c.getArgument(0)).statusCode="ING"; return null; }).when(mapper).start(anyString(), anyString());
        doAnswer(c -> { LossWrite w=c.getArgument(0); var p=projects.get(w.projectId()); p.statusCode="LOSE";
            p.loseDate=w.today(); p.loseMemo=w.input().memo(); p.loseReasonName=w.input().reasonCode(); return null; }).when(mapper).lose(any());
        Project p = new Project(); p.projectId="P-0001"; p.projectName="연속 프로젝트"; p.contractTypeCode="MO";
        p.statusCode="PRE"; p.statusName="수주예정"; p.customerId="customer"; p.customerName="고객사";
        p.startDate=LocalDate.of(2025,1,1); p.endDate=LocalDate.of(2027,12,31);
        p.contractAmount=new BigDecimal("1000000"); p.originalContractAmount=p.contractAmount;
        p.budgetAmount=new BigDecimal("800000"); p.budgetManualYn="N";
        p.teamExpenseAmount=new BigDecimal("50000"); p.projectExpenseAmount=p.teamExpenseAmount;
        projects.put(p.projectId,p);
        when(progressMapper.lockOwner(anyString())).thenAnswer(call -> {
            Project stored = projects.get(call.getArgument(0));
            return stored == null ? null : new com.company.common.projects.mapper.ProjectProgressMapper.Owner(
                stored.projectId, stored.contractTypeCode, stored.statusCode, stored.startDate, stored.endDate);
        });
        var stageDefinitions = new ArrayList<com.company.common.common.ProjectBoardStageCalendar.Definition>();
        for (int i = 1; i <= 7; i++) stageDefinitions.add(new com.company.common.common.ProjectBoardStageCalendar.Definition(
            "T" + i, "단계" + i, BigDecimal.valueOf(i == 7 ? 40 : 10), "TK,IN", i));
        for (int i = 1; i <= 5; i++) stageDefinitions.add(new com.company.common.common.ProjectBoardStageCalendar.Definition(
            "M" + i, "단계" + i, BigDecimal.valueOf(20), "MO,DP", i));
        when(progressMapper.stageDefinitions()).thenReturn(stageDefinitions);
        var first=new SalesPart(); first.salesPartId="first"; first.salesPartName="원래 파트"; first.effectiveMonth="2025-01"; first.changedBy="admin"; parts.add(first);
    }
    ObjectNode createInput() {
        var b=json.createObjectNode(); b.put("projectName","새 프로젝트"); b.put("customerId","customer");
        b.put("contractTypeCode","MO"); b.put("statusCode","PRE"); b.put("salesPartId","first");
        b.put("startDate","2026-11-01"); b.put("endDate","2027-12-31"); b.put("contractAmount",1000000);
        b.put("estimateAmount",1200000); b.put("feature","특징"); return b;
    }
    ObjectNode updateInput() {
        // 동시 수정 확인: 읽은 프로젝트의 modifiedAt(시험 자료는 null)을 그대로 돌려보낸다.
        var b=createInput(); b.remove(List.of("contractTypeCode","statusCode","salesPartId")); b.putNull("modifiedAt"); return b;
    }
    @Test
    @DisplayName("auth happy side-effect: 무인증 admin 프로젝트 등록과 최초 계약·파트 이력 및 TB_PJ_STEP")
    void anonymousCreatePersistsAutoIdOriginalDefaultsAndInitialHistories() throws Exception {
        mvc.perform(post("/api/common/projects").contentType("application/json").content(createInput().toString()))
            .andExpect(status().isOk()).andExpect(response("POST", "/api/common/projects"))
            .andExpect(jsonPath("$.data.projectId").value("P-0002"));
        mvc.perform(get("/api/common/projects/P-0002")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projectName").value("새 프로젝트"))
            .andExpect(jsonPath("$.data.estimateAmount").value(1200000))
            .andExpect(jsonPath("$.data.budgetAmount").value(800000))
            .andExpect(jsonPath("$.data.budgetManualYn").value("N"));
        assertThat(history).anySatisfy(h -> { assertThat(h.code()).isEqualTo("CTRT"); assertThat(h.actor()).isEqualTo("admin");
            // 시안: 「계약」 첫 줄의 변경 후는 계약금액(화면은 「1,000,000 원」으로 보인다)
            assertThat(h.afterValue()).isEqualTo("1000000"); });
        assertThat(parts).anySatisfy(p -> assertThat(p.effectiveMonth).isEqualTo("2026-11"));
        assertThat(projects.get("P-0002").originalContractAmount).isEqualByComparingTo("1000000");
        verify(progressMapper, times(5)).insertStage(argThat(w -> w.projectId().equals("P-0002") && w.actor().equals("admin")));
    }
    // 근무지 지역(2026-11-25): 고른 마지막 단계의 행정구역 코드. 사용 중인 코드만, 비우면 없음, 내부는 저장하지 않음.
    @Test void createStoresAUsableWorkAreaCode() throws Exception {
        when(mapper.usableArea("1114000000")).thenReturn(true);
        mvc.perform(post("/api/common/projects").contentType("application/json").content(createInput().put("workAreaCode","1114000000").toString()))
            .andExpect(status().isOk()).andExpect(response("POST", "/api/common/projects"))
            .andExpect(jsonPath("$.data.workAreaCode").value("1114000000"));
        verify(mapper).insertProject(argThat(w -> "1114000000".equals(w.project().workAreaCode)));
    }
    @Test void createWithoutWorkAreaCodeStoresNull() throws Exception {
        mvc.perform(post("/api/common/projects").contentType("application/json").content(createInput().putNull("workAreaCode").toString()))
            .andExpect(status().isOk());
        assertThat(projects.get("P-0002").workAreaCode).isNull();
        verify(mapper, never()).usableArea(any());
    }
    @ParameterizedTest @ValueSource(strings = {"9999999999", "서울 중구", "12345678901"})
    void unknownOrMalformedWorkAreaCodeIsKoreanFieldErrorWithoutInsert(String code) throws Exception {
        mvc.perform(post("/api/common/projects").contentType("application/json").content(createInput().put("workAreaCode",code).toString()))
            .andExpect(status().isBadRequest()).andExpect(response("POST", "/api/common/projects"))
            .andExpect(jsonPath("$.errors[0].field").value("workAreaCode"))
            .andExpect(jsonPath("$.errors[0].message").value("근무지 지역을 목록에서 다시 골라 주세요."));
        verify(mapper, never()).insertProject(any());
    }
    @Test void internalProjectDropsWorkAreaCode() throws Exception {
        when(mapper.usableArea("1114000000")).thenReturn(true);
        var b=createInput(); b.put("contractTypeCode","IN"); b.remove(List.of("customerId","salesPartId","contractAmount")); b.put("workAreaCode","1114000000");
        mvc.perform(post("/api/common/projects").contentType("application/json").content(b.toString())).andExpect(status().isOk());
        assertThat(projects.get("P-0002").workAreaCode).isNull();
    }
    @Test void updateChangesClearsOrKeepsTheWorkAreaCode() throws Exception {
        when(mapper.usableArea("1114000000")).thenReturn(true);
        mvc.perform(put("/api/common/projects/P-0001").contentType("application/json").content(updateInput().put("workAreaCode","1114000000").toString()))
            .andExpect(status().isOk()).andExpect(response("PUT", "/api/common/projects/{projectId}"));
        assertThat(projects.get("P-0001").workAreaCode).isEqualTo("1114000000");
        // 저장된 구역이 나중에 쓰지 않게 돼도 그대로 두는 저장은 받는다(개발자 거주지와 같음).
        when(mapper.usableArea("1114000000")).thenReturn(false);
        mvc.perform(put("/api/common/projects/P-0001").contentType("application/json").content(updateInput().put("workAreaCode","1114000000").toString()))
            .andExpect(status().isOk());
        mvc.perform(put("/api/common/projects/P-0001").contentType("application/json").content(updateInput().put("workAreaCode","1100000000").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("workAreaCode"));
        assertThat(projects.get("P-0001").workAreaCode).isEqualTo("1114000000");
        mvc.perform(put("/api/common/projects/P-0001").contentType("application/json").content(updateInput().putNull("workAreaCode").toString()))
            .andExpect(status().isOk());
        assertThat(projects.get("P-0001").workAreaCode).isNull();
    }
    @Test void internalBlankContractIsZeroAndHasNoCustomerBudgetOrWorkplace() throws Exception {
        var b=createInput(); b.put("contractTypeCode","IN"); b.remove(List.of("customerId","salesPartId","contractAmount"));
        mvc.perform(post("/api/common/projects").contentType("application/json").content(b.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.contractAmount").value(0));
        var p=projects.get("P-0002"); assertThat(p.contractAmount).isZero(); assertThat(p.customerId).isNull(); assertThat(p.budgetAmount).isNull();
    }
    @Test
    @DisplayName("validation: 프로젝트명 101자 거부, 신규 프로젝트 없음")
    void name101RejectsWithoutInsert() throws Exception {
        var b=createInput(); b.put("projectName","가".repeat(101));
        mvc.perform(post("/api/common/projects").contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(response("POST", "/api/common/projects"))
            .andExpect(jsonPath("$.errors[0].field").value("projectName"));
        verify(mapper,never()).insertProject(any());
    }
    @Test
    @DisplayName("business: 종료 상태 등록 거부, 프로젝트·최초 이력 불변")
    void endedInputRejectedWithoutInsert() throws Exception {
        var b=createInput(); b.put("statusCode","END");
        mvc.perform(post("/api/common/projects").contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("statusCode"));
        verify(mapper,never()).insertProject(any());
        verify(mapper,never()).insertPart(any()); assertThat(history).isEmpty();
    }
    @Test void reversedPeriodRejectedWithoutInsert() throws Exception {
        var b=createInput(); b.put("endDate","2026-10-31");
        mvc.perform(post("/api/common/projects").contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("endDate"));
        verify(mapper,never()).insertProject(any());
    }
    @Test void equalDatesAccepted() throws Exception {
        var b=createInput(); b.put("endDate","2026-11-01");
        mvc.perform(post("/api/common/projects").contentType("application/json").content(b.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.endDate").value("2026-11-01"));
    }
    @Test void listIsPublicAndReturnsPersistedProject() throws Exception {
        mvc.perform(get("/api/common/projects")).andExpect(status().isOk())
            .andExpect(response("GET", "/api/common/projects"))
            .andExpect(jsonPath("$.data.items[0].projectId").value("P-0001"));
        verify(mapper).selectProjects(today, "202611");
    }
    @Test void budgetRuleReturnsTheServerFactor() throws Exception {
        when(amounts.budgetRule(any())).thenReturn(new com.company.common.projects.dto.ProjectContracts.BudgetRule(
            new java.math.BigDecimal("20"), new java.math.BigDecimal("0.80")));
        mvc.perform(get("/api/common/projects/budget-rule")).andExpect(status().isOk())
            .andExpect(response("GET", "/api/common/projects/budget-rule"))
            .andExpect(jsonPath("$.data.budgetFactor").value(0.80))
            .andExpect(jsonPath("$.data.targetMarginRate").value(20));
    }
    @Test void excelDownloadIsAnXlsxWithTheYearInTheFileName() throws Exception {
        mvc.perform(get("/api/common/projects/excel").param("year", "2026"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("2026")));
    }
    @Test void staleModifiedAtIsAConflictAndWritesNothing() throws Exception {
        var b=updateInput(); b.put("modifiedAt","2026-01-01T00:00:00Z");
        mvc.perform(put("/api/common/projects/P-0001").contentType("application/json").content(b.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."));
        verify(mapper, never()).updateProject(any());
    }
    @Test void changeHistoryReturnsStoredValues() throws Exception {
        var h = new History(); h.itemName="계약금액"; h.beforeValue="100"; h.afterValue="200";
        h.changedAt=java.time.Instant.parse("2026-11-15T00:00:00Z"); h.changedBy="admin";
        when(mapper.histories("P-0001")).thenReturn(List.of(h));
        mvc.perform(get("/api/common/projects/P-0001/change-history")).andExpect(status().isOk())
            .andExpect(response("GET", "/api/common/projects/{projectId}/change-history"))
            .andExpect(jsonPath("$.data.items[0].afterValue").value("200"))
            .andExpect(jsonPath("$.data.items[0].changedAt").value("2026-11-15T00:00:00Z"));
        verify(mapper).histories("P-0001");
    }
    @Test void salesPartsReturnsInitialStoredPart() throws Exception {
        mvc.perform(get("/api/common/projects/P-0001/sales-parts")).andExpect(status().isOk())
            .andExpect(response("GET", "/api/common/projects/{projectId}/sales-parts"))
            .andExpect(jsonPath("$.data.items[0].salesPartId").value("first"))
            .andExpect(jsonPath("$.data.items[0].effectiveMonth").value("2025-01"));
        verify(mapper).salesParts("P-0001", "202611");
    }
    @Test void manpowerPlanReturnsEmptyItemsAndAdminCapability() throws Exception {
        mvc.perform(get("/api/common/projects/P-0001/manpower-plan")).andExpect(status().isOk())
            .andExpect(response("GET", "/api/common/projects/{projectId}/manpower-plan"))
            .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.canSave").value(true));
        verify(mapper).manpower("P-0001");
    }
    @Test void unknownProjectIsFieldError() throws Exception {
        mvc.perform(get("/api/common/projects/missing")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("projectId"));
    }
    @Test void unknownProjectHistoryDoesNotQueryHistory() throws Exception {
        mvc.perform(get("/api/common/projects/missing/change-history")).andExpect(status().isBadRequest())
            .andExpect(response("GET", "/api/common/projects/{projectId}/change-history"))
            .andExpect(jsonPath("$.errors[0].field").value("projectId"));
        verify(mapper,never()).histories(anyString());
    }
    @Test void unknownProjectSalesPartsDoesNotQueryParts() throws Exception {
        mvc.perform(get("/api/common/projects/missing/sales-parts")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("projectId"));
        verify(mapper,never()).salesParts(anyString(),anyString());
    }
    @Test void unknownProjectManpowerDoesNotQueryPlans() throws Exception {
        mvc.perform(get("/api/common/projects/missing/manpower-plan")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("projectId"));
        verify(mapper,never()).manpower(anyString());
    }
    @Test
    @DisplayName("business: 실주 프로젝트 TB_PJ_MNPW_PLAN 저장 거부와 audit 불변")
    void lostProjectCannotSaveManpower() throws Exception {
        projects.get("P-0001").statusCode="LOSE";
        mvc.perform(put("/api/common/projects/P-0001/manpower-plan").contentType("application/json")
            .content("{\"jobs\":[{\"jobCode\":\"DEV\",\"plannedMm\":1}]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("projectId"));
        verify(mapper,never()).savePlan(any()); verify(mapper,never()).insertHistory(any());
    }
    @Test
    @DisplayName("auth happy side-effect: admin TB_PJ_DRCT_EXPS 등록·삭제, 별도 audit 없음")
    void expensesAllowNoOwnPersonnelAndDeleteExactCompositeIdentity() throws Exception {
        var b=json.createObjectNode().put("yearMonth","2026-11").put("itemCode","ETC").put("amount",12345).put("memo","외주만 있음");
        mvc.perform(post("/api/common/projects/P-0001/direct-expenses").contentType("application/json").content(b.toString()))
            .andExpect(status().isOk()).andExpect(response("POST", "/api/common/projects/{projectId}/direct-expenses"))
            .andExpect(jsonPath("$.data.amount").value(12345)).andExpect(jsonPath("$.data.memo").value("외주만 있음"));
        verify(mapper).insertExpense(new ExpenseWrite(new ExpenseKey("P-0001","202611",1),
            new ExpenseInput("2026-11","ETC",new BigDecimal("12345"),"외주만 있음"),"admin"));
        mvc.perform(get("/api/common/projects/P-0001/direct-expenses")).andExpect(jsonPath("$.data.items[0].expenseId").value("202611:1"));
        mvc.perform(post("/api/common/projects/P-0001/direct-expenses/202611:1/delete")).andExpect(status().isOk())
            .andExpect(response("POST", "/api/common/projects/{projectId}/direct-expenses/{expenseId}/delete"))
            .andExpect(jsonPath("$.data.deleted").value(true));
        verify(mapper,never()).insertHistory(any());
        assertThat(expenses).isEmpty(); verify(mapper).deleteExpense(new ExpenseKey("P-0001","202611",1));
    }
    @Test
    @DisplayName("validation: itemCode 없는 직접 경비 등록 거부")
    void expenseItemRequired() throws Exception {
        mvc.perform(post("/api/common/projects/P-0001/direct-expenses").contentType("application/json")
            .content("{\"yearMonth\":\"2026-11\",\"amount\":1}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("itemCode"));
        verify(mapper,never()).insertExpense(any());
    }
    @Test
    @DisplayName("business: 마감월 TB_PJ_DRCT_EXPS 추가 거부")
    void closedExpenseCannotBeAdded() throws Exception {
        when(mapper.lastClosedMonth()).thenReturn("202611");
        mvc.perform(post("/api/common/projects/P-0001/direct-expenses").contentType("application/json")
            .content("{\"yearMonth\":\"2026-11\",\"itemCode\":\"ETC\",\"amount\":1}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("yearMonth"));
        verify(mapper,never()).insertExpense(any());
    }
    @Test
    @DisplayName("business: 마감 TB_PJ_DRCT_EXPS 삭제 거부와 원행 보존")
    void closedExpenseCannotBeDeleted() throws Exception {
        var e=new Expense(); e.expenseId="202611:1"; e.amount=BigDecimal.ONE; expenses.add(e);
        when(mapper.lastClosedMonth()).thenReturn("202611");
        mvc.perform(post("/api/common/projects/P-0001/direct-expenses/202611:1/delete")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("expenseId"));
        verify(mapper,never()).deleteExpense(any());
        assertThat(expenses).containsExactly(e);
    }
    @Test void invalidCompositeIdCannotDelete() throws Exception {
        mvc.perform(post("/api/common/projects/P-0001/direct-expenses/1/delete")).andExpect(status().isBadRequest());
        verify(mapper,never()).deleteExpense(any());
    }
    @Test
    @DisplayName("auth happy side-effect: BDGT_MANUAL_YN=Y 및 CHG_ITEM BUDGET 이력, 무인증 admin 저장")
    void budgetManualSurvivesContractChange() throws Exception {
        mvc.perform(put("/api/common/projects/P-0001/budget").contentType("application/json").content("{\"budgetAmount\":700000}"))
            .andExpect(status().isOk()).andExpect(response("PUT", "/api/common/projects/{projectId}/budget"))
            .andExpect(jsonPath("$.data.budgetManualYn").value("Y"));
        var b=updateInput(); b.put("contractAmount",2000000);
        mvc.perform(put("/api/common/projects/P-0001").contentType("application/json").content(b.toString())).andExpect(status().isOk());
        assertThat(projects.get("P-0001").budgetAmount).isEqualByComparingTo("700000");
        assertThat(projects.get("P-0001").budgetManualYn).isEqualTo("Y");
        assertThat(history.stream().filter(h -> h.code().equals("BUDGET"))).hasSize(1);
    }
    @Test void budgetMissingRejected() throws Exception {
        mvc.perform(put("/api/common/projects/P-0001/budget").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        verify(mapper,never()).updateProject(any());
    }
    @Test
    @DisplayName("business: 실주 예산 변경 거부와 BDGT_MANUAL_YN 보존")
    void lostBudgetRejectedAndCapabilitiesAreFalse() throws Exception {
        projects.get("P-0001").statusCode="LOSE";
        mvc.perform(put("/api/common/projects/P-0001/budget").contentType("application/json").content("{\"budgetAmount\":1}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/common/projects/P-0001")).andExpect(jsonPath("$.data.canEdit").value(false)).andExpect(jsonPath("$.data.canLose").value(false));
        verify(mapper,never()).updateProject(any());
    }
    @ParameterizedTest @ValueSource(strings={"contractTypeCode","statusCode","salesPartId","projectId","originalContractAmount","canEdit"})
    void readonlyFieldsAreNeverAcceptedInUpdate(String field) throws Exception {
        var b=updateInput(); b.put(field,"changed");
        mvc.perform(put("/api/common/projects/P-0001").contentType("application/json").content(b.toString())).andExpect(status().isBadRequest());
        verify(mapper,never()).updateProject(any());
    }
    @Test void outsidePeriodReportsAllThreeCountsAndWritesNothing() throws Exception {
        var outside=new Outside(); outside.assignments=2; outside.distributionMonths=3; outside.expenses=4;
        when(mapper.outside(anyString(),any(),any(),any())).thenReturn(outside);
        mvc.perform(put("/api/common/projects/P-0001").contentType("application/json").content(updateInput().toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("투입 2건, 배분표 금액이 있는 달 3개, 추가 직접 경비 4건")));
        verify(mapper,never()).updateProject(any()); assertThat(history).isEmpty();
    }
    @Test
    @DisplayName("auth happy side-effect: CNCL_YN=N 청구가 있으면 ORGNL_CTRT_AMT 보존, TB_PJ_PROJ_CHG 이력")
    void billingPreservesOriginalAndRecordsEachChangedField() throws Exception {
        when(mapper.hasBills("P-0001")).thenReturn(true); var b=updateInput(); b.put("contractAmount",2000000).put("workplace","서울");
        mvc.perform(put("/api/common/projects/P-0001").contentType("application/json").content(b.toString()))
            .andExpect(status().isOk()).andExpect(response("PUT", "/api/common/projects/{projectId}"))
            .andExpect(jsonPath("$.data.originalContractAmount").value(1000000))
            .andExpect(jsonPath("$.data.contractAmount").value(2000000));
        verify(mapper).updateProject(argThat(w -> w.actor().equals("admin")));
        assertThat(projects.get("P-0001").originalContractAmount).isEqualByComparingTo("1000000");
        assertThat(history).anySatisfy(h -> assertThat(h.name()).isEqualTo("근무지"));
        assertThat(history).anySatisfy(h -> assertThat(h.name()).isEqualTo("견적 금액"));
    }
    @Test
    @DisplayName("business: 지난 해 계약 수정 거부, 변경 이력 불변")
    void pastYearRejectedWithoutMutation() throws Exception {
        projects.get("P-0001").endDate=LocalDate.of(2025,12,31);
        mvc.perform(put("/api/common/projects/P-0001").contentType("application/json").content(updateInput().toString())).andExpect(status().isBadRequest());
        verify(mapper,never()).updateProject(any());
    }
    @Test
    @DisplayName("auth happy side-effect: 무인증 admin TB_PJ_MNPW_PLAN 저장, 별도 audit 없음")
    void manpowerBlankNormalizesZeroAndOmittedJobsAreRetained() throws Exception {
        mvc.perform(put("/api/common/projects/P-0001/manpower-plan").contentType("application/json").content("{\"jobs\":[{\"jobCode\":\"DEV\",\"plannedMm\":null}]}"))
            .andExpect(status().isOk()).andExpect(response("PUT", "/api/common/projects/{projectId}/manpower-plan"))
            .andExpect(jsonPath("$.data.items[0].plannedMm").value(0));
        verify(mapper).savePlan(new PlanWrite("P-0001",new JobInput("DEV",BigDecimal.ZERO),"admin"));
        verify(mapper,never()).insertHistory(any());
    }
    @ParameterizedTest @ValueSource(strings={"{\"jobs\":[{\"jobCode\":\"DEV\",\"plannedMm\":0.001}]}","{\"jobs\":[{\"jobCode\":\"DEV\"},{\"jobCode\":\"DEV\"}]}"})
    void invalidManpowerNeverWrites(String body) throws Exception {
        mvc.perform(put("/api/common/projects/P-0001/manpower-plan").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verify(mapper,never()).savePlan(any());
    }
    @ParameterizedTest @ValueSource(strings={"0.001","-1","\"abc\""})
    @DisplayName("시안: 계획 MM이 틀리면 「… 계획 MM은 0 이상 숫자(소수 둘째 자리까지)로 넣어 주세요.」")
    void wrongPlannedMmUsesMockupMessage(String mm) throws Exception {
        mvc.perform(put("/api/common/projects/P-0001/manpower-plan").contentType("application/json").content("{\"jobs\":[{\"jobCode\":\"DEV\",\"plannedMm\":" + mm + "}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].message").value("DEV 계획 MM은 0 이상 숫자(소수 둘째 자리까지)로 넣어 주세요."));
        verify(mapper,never()).savePlan(any());
    }
    @Test
    @DisplayName("business: 마감월 영업 파트 변경은 TB_PJ_PROJ_PART 보존")
    void partClosedMonthRejected() throws Exception {
        when(mapper.lastClosedMonth()).thenReturn("202611");
        mvc.perform(put("/api/common/projects/P-0001/sales-part").contentType("application/json").content("{\"salesPartId\":\"second\",\"effectiveMonth\":\"2026-11\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].message").value("적용 시작 월은 2026-12 이후로 골라 주세요.")); verify(mapper,never()).deleteOpenParts(anyString(),anyString());
    }
    @Test
    @DisplayName("auth happy side-effect: admin TB_PJ_PROJ_PART 변경과 TB_PJ_PROJ_CHG PART 취소 이력")
    void partReplacesPendingAndOriginalCancelsWithDisplayHistory() throws Exception {
        when(mapper.lastClosedMonth()).thenReturn("202611");
        mvc.perform(put("/api/common/projects/P-0001/sales-part").contentType("application/json").content("{\"salesPartId\":\"second\",\"effectiveMonth\":\"2026-12\"}"))
            .andExpect(status().isOk()); assertThat(parts).hasSize(2);
        mvc.perform(put("/api/common/projects/P-0001/sales-part").contentType("application/json").content("{\"salesPartId\":\"first\",\"effectiveMonth\":\"2026-12\"}"))
            .andExpect(status().isOk()); assertThat(parts).hasSize(1);
        assertThat(history.get(history.size()-1).afterValue()).contains("변경 취소");
        assertThat(history).allSatisfy(h -> { assertThat(h.code()).isEqualTo("PART"); assertThat(h.actor()).isEqualTo("admin"); });
        verify(mapper).insertPart(new PartWrite("P-0001","202612","second",null,"admin"));
    }
    @Test void malformedPartMonthRejected() throws Exception {
        mvc.perform(put("/api/common/projects/P-0001/sales-part").contentType("application/json").content("{\"salesPartId\":\"first\",\"effectiveMonth\":\"2026-13\"}"))
            .andExpect(status().isBadRequest());
    }
    @Test
    @DisplayName("auth happy side-effect: 무인증 TB_PJ_PROJ 진행 변경 및 누락 TB_PJ_STEP 생성, 추가 audit 없음")
    void startChangesOnlyStateAndCreatesNoDuplicateContract() throws Exception {
        mvc.perform(post("/api/common/projects/P-0001/start")).andExpect(status().isOk())
            .andExpect(response("POST", "/api/common/projects/{projectId}/start"))
            .andExpect(jsonPath("$.data.statusCode").value("ING"));
        verify(mapper).start("P-0001", "admin"); verify(mapper,never()).insertHistory(any());
        assertThat(history).isEmpty(); verify(mapper,never()).insertPart(any());
        verify(progressMapper, times(5)).insertStage(argThat(w -> w.projectId().equals("P-0001") && w.actor().equals("admin")));
    }
    @ParameterizedTest @ValueSource(strings={"ING","END","LOSE"})
    @DisplayName("business: 진행·종료·실주는 진행전환 거부, PROJ_STAT_CD 보존")
    void startRejectsOtherStates(String status) throws Exception {
        projects.get("P-0001").statusCode=status;
        mvc.perform(post("/api/common/projects/P-0001/start")).andExpect(status().isBadRequest()); verify(mapper,never()).start(anyString(), anyString());
    }
    ActiveAssignment assignment(String id,String dev,int start,int end) {
        var a=new ActiveAssignment(); a.assignmentId=id; a.developerId=dev; a.startDate=today.plusDays(start); a.endDate=today.plusDays(end); return a;
    }
    @Test
    @DisplayName("auth happy side-effect: LOSE_RSN 사유 실주, 미래 CNCL·조기철수 TB_PJ_ASGN_HIST, 완료 투입 보존")
    void lossCountsPeopleButProcessesAllRowsAndPreservesFinished() throws Exception {
        var rows=List.of(assignment("a","d",-1,1),assignment("b","d",-1,2),assignment("c","f",1,3),assignment("old","o",-5,-1));
        when(mapper.lockAssignments("P-0001")).thenReturn(rows); when(mapper.impactAssignments("P-0001")).thenReturn(rows);
        mvc.perform(get("/api/common/projects/P-0001/lose-impact")).andExpect(jsonPath("$.data.activePeopleCount").value(1)).andExpect(jsonPath("$.data.futurePeopleCount").value(1));
        mvc.perform(post("/api/common/projects/P-0001/lose").contentType("application/json")
            .content("{\"reasonCode\":\"ETC\",\"memo\":\"상세 사유\"}"))
            .andExpect(status().isOk()).andExpect(response("POST", "/api/common/projects/{projectId}/lose"))
            .andExpect(jsonPath("$.data.statusCode").value("LOSE"))
            .andExpect(jsonPath("$.data.loseDate").value("2026-11-15"))
            .andExpect(jsonPath("$.data.loseReasonName").value("ETC"));
        assertThat(projects.get("P-0001").loseDate).isEqualTo(today);
        verify(mapper,times(3)).changeAssignment(any()); verify(mapper,times(3)).assignmentHistory(any());
        verify(mapper).assignmentHistory(argThat(w -> w.assignmentId().equals("c") && w.kind().equals("CNCL")
            && w.actor().equals("admin") && w.memo().contains("프로젝트 실주: ETC")));
        verify(mapper,never()).changeAssignment(argThat(w -> w.assignmentId().equals("old")));
    }
    @Test
    @DisplayName("validation: LOSE_RSN 사유 누락은 프로젝트·배정 무변경")
    void missingLossReasonNeverChangesAssignments() throws Exception {
        mvc.perform(post("/api/common/projects/P-0001/lose").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reasonCode"));
        verify(mapper,never()).changeAssignment(any()); verify(mapper,never()).lose(any());
    }
    @Test void lossMemo501Rejected() throws Exception {
        var b=json.createObjectNode().put("reasonCode","ETC").put("memo","가".repeat(501));
        mvc.perform(post("/api/common/projects/P-0001/lose").contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("memo"));
        verify(mapper,never()).lose(any());
    }
    @Test
    @DisplayName("business: 진행 프로젝트 실주 거부, 프로젝트·배정 이력 보존")
    void runningCannotLose() throws Exception {
        projects.get("P-0001").statusCode="ING";
        mvc.perform(post("/api/common/projects/P-0001/lose").contentType("application/json").content("{\"reasonCode\":\"ETC\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("projectId"));
        verify(mapper,never()).lose(any()); verify(mapper,never()).assignmentHistory(any());
    }
    @Test void lossFailureDoesNotReachProjectWrite() throws Exception {
        when(mapper.lockAssignments("P-0001")).thenReturn(List.of(assignment("a","d",-1,1)));
        doThrow(new IllegalStateException("failure")).when(mapper).assignmentHistory(any());
        mvc.perform(post("/api/common/projects/P-0001/lose").contentType("application/json").content("{\"reasonCode\":\"ETC\"}"))
            .andExpect(status().is5xxServerError());
        verify(mapper,never()).lose(any());
    }
    @Test void partChangeCannotOverwriteTheInitialHistoryMonth() throws Exception {
        parts.get(0).effectiveMonth = "2026-11";
        projects.get("P-0001").startDate = LocalDate.of(2026, 11, 1);
        mvc.perform(put("/api/common/projects/P-0001/sales-part")
                .contentType("application/json")
                .content("{\"salesPartId\":\"second\",\"effectiveMonth\":\"2026-11\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("effectiveMonth"))
            .andExpect(jsonPath("$.errors[0].message").value("적용 시작 월은 2026-12 이후로 골라 주세요."));
        verify(mapper, never()).deleteOpenParts(anyString(), anyString());
        verify(mapper, never()).insertPart(any());
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).salesPartId).isEqualTo("first");
        assertThat(history).isEmpty();
    }
    @Test void initialPartSelectionCancelsPendingChangeWithoutReplacingFirstRow() throws Exception {
        parts.get(0).effectiveMonth = "2026-11";
        projects.get("P-0001").startDate = LocalDate.of(2026, 11, 1);
        var scheduled = new SalesPart();
        scheduled.salesPartId = "second";
        scheduled.salesPartName = "새 파트";
        scheduled.effectiveMonth = "2026-12";
        parts.add(scheduled);
        mvc.perform(put("/api/common/projects/P-0001/sales-part")
                .contentType("application/json")
                .content("{\"salesPartId\":\"first\",\"effectiveMonth\":\"2026-11\"}"))
            .andExpect(status().isOk());
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).salesPartId).isEqualTo("first");
        verify(mapper, never()).insertPart(any());
        assertThat(history).singleElement().satisfies(h -> {
            // 시안: 변경 전은 잡혀 있던 변경, 변경 후는 「변경 취소」
            assertThat(h.beforeValue()).contains("2026-12부터");
            assertThat(h.afterValue()).isEqualTo("변경 취소");
        });
    }
    @Test void distributionAndTemplateReturnActualWorkbook() throws Exception {
        projects.get("P-0001").contractTypeCode="TK";
        mvc.perform(get("/api/common/projects/P-0001/distribution")).andExpect(status().isOk()).andExpect(jsonPath("$.data.project.projectId").value("P-0001"));
        byte[] bytes=mvc.perform(get("/api/common/projects/P-0001/distribution/template")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var wb=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            assertThat(wb.getSheetAt(0).getRow(2).getCell(0).getStringCellValue()).isEqualTo("구분");
        }
    }
    /** Hand-added fields and paths live in openapi-manual.yaml because regenerating openapi.yaml drops them. */
    private static final java.util.Set<String> MANUAL_PATHS = java.util.Set.of("/api/common/projects", "/api/common/projects/excel", "/api/common/projects/budget-rule", "/api/common/projects/{projectId}", "/api/common/projects/{projectId}/budget", "/api/common/projects/{projectId}/start", "/api/common/projects/{projectId}/distribution", "/api/common/projects/{projectId}/lose");
    private static org.springframework.test.web.servlet.ResultMatcher response(String method, String path) {
        return MANUAL_PATHS.contains(path)
            ? com.company.common.support.controller.OpenApiResponseContract.response(method, path, "contracts/openapi-manual.yaml")
            : com.company.common.support.controller.OpenApiResponseContract.response(method, path);
    }
}
