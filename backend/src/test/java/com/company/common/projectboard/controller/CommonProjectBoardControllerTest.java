package com.company.common.projectboard.controller;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.ProjectBoardSignalRules;
import com.company.common.projectboard.mapper.ProjectBoardMapper;
import com.company.common.projectboard.mapper.ProjectBoardSource;
import com.company.common.projectboard.service.CommonProjectBoardService;
import com.company.common.projects.dto.ProjectContracts.Step;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.projects.service.ProjectProgressRules;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonProjectBoardController.class)
@ContextConfiguration(classes = {CommonProjectBoardController.class, CommonProjectBoardService.class,
    ProjectProgressRules.class, ProjectBoardSignalRules.class, CodeExceptionHandler.class,
    ApiExceptionHandler.class, CurrentBusinessActor.class, ExcelExportService.class})
class CommonProjectBoardControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ProjectBoardMapper mapper;
    @MockBean ProjectMapper projects;
    @MockBean BusinessCalendar calendar;
    @MockBean CalculationRuleService definitions;
    @MockBean PlatformTransactionManager transactions;
    @MockBean com.company.common.projects.service.ProjectStageService stages;
    @MockBean com.company.common.projectcostanalyses.service.CommonProjectCostAnalysisService costAnalyses;
    final LocalDate today = LocalDate.of(2026, 11, 15);
    List<ProjectBoardSource> sources;

    @BeforeEach void setup() throws Exception {
        sources = new ArrayList<>();
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(calendar.today()).thenReturn(today);
        when(calendar.now()).thenReturn(Instant.parse("2026-11-15T00:00:00Z"));
        when(projects.codeName(eq("CTRT_TYPE"), anyString())).thenAnswer(call ->
            List.of("TK", "IN", "MO", "DP").contains(call.getArgument(1)) ? call.getArgument(1) : null);
        when(mapper.partExists("sales")).thenReturn(true);
        when(mapper.sources(any(), eq(today), eq("202611"))).thenAnswer(call -> sources);
        String seed = new ClassPathResource("db/migration/V8__calculation_rule_seed.sql")
            .getContentAsString(StandardCharsets.UTF_8);
        when(definitions.definition(anyString())).thenAnswer(call -> {
            String id = call.getArgument(0);
            // 식-00 has no SFR_ID; SQL NULL is distinct from a quoted string.
            var row = Pattern.compile("\\('" + Pattern.quote(id)
                + "', (?:NULL|'[^']*'), '[^']*', '((?:[^']|'')*)',").matcher(seed);
            assertThat(row.find()).as(id).isTrue();
            return new Definition(id, row.group(1).replace("''", "'"), "DB 계산 설명");
        });
    }

    ProjectBoardSource source(String id, String type) {
        var source = new ProjectBoardSource();
        source.projectId = id;
        source.projectName = "현황 " + id;
        source.contractTypeCode = type;
        source.statusCode = "ING";
        source.startDate = today.minusDays(5);
        source.endDate = today.plusDays(30);
        source.latestMemoDate = today;
        source.salesPartId = "sales";
        source.budgetAmount = new BigDecimal("1000");
        source.costAmount = new BigDecimal("100");
        source.actualProgress = new BigDecimal("50");
        sources.add(source);
        return source;
    }

    @org.junit.jupiter.api.Test void turnkeyCostUsageIsTheCostAnalysisBudgetUsageRate() throws Exception {
        // 식-75: 원가 사용률은 원가 분석(UI-04)의 예산 소진율과 같은 값(마감 전 달 원가 포함, 2026-11-24 사용자 결정).
        source("shared", "TK");
        org.mockito.Mockito.when(costAnalyses.budgetUsageRates(2026, 11)).thenReturn(java.util.Map.of("shared", new BigDecimal("23.46")));
        var row = board().path("items").get(0);
        assertThat(row.path("costUsageRate").decimalValue()).isEqualByComparingTo("23.5");
        assertThat(row.path("costProgressGap").decimalValue()).isEqualByComparingTo("-26.5");
    }

    com.fasterxml.jackson.databind.JsonNode board(String... params) throws Exception {
        var request = get("/api/common/project-board");
        for (int i = 0; i < params.length; i += 2) request.param(params[i], params[i + 1]);
        return json.readTree(mvc.perform(request).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    @Test void anonymousReadHasExactMoneyFreeWireShapeIncludingNulls() throws Exception {
        source("visible", "MO");
        var result = board();
        var row = result.path("items").get(0);
        assertThat(row.properties()).hasSize(22);
        assertThat(row.path("signalReasonLevels").isArray()).isTrue();
        assertThat(row.path("salesPartId").asText()).isEqualTo("sales");
        assertThat(row.path("pmUserId").isNull()).isTrue();
        assertThat(row.path("pmName").isNull()).isTrue();
        assertThat(row.path("costUsageRate").isNull()).isTrue();
        assertThat(row.path("costProgressGap").isNull()).isTrue();
        assertThat(row.toString()).doesNotContain("budgetAmount", "costAmount", "contractAmount");
        assertThat(result.path("calculationMethod").asText()).contains("식-00", "식-19", "식-91", "UI-04");
        assertThat(result.path("summary").path("progress").path("total").asInt()).isEqualTo(1);
    }

    @Test void lifecycleExcludesStoredEndedLostAndEffectiveEndedButIncludesPre() throws Exception {
        source("pre", "MO").statusCode = "PRE";
        source("ended", "MO").statusCode = "END";
        source("lost", "MO").statusCode = "LOSE";
        source("expired", "MO").endDate = today.minusDays(1);
        assertThat(board().path("items").findValuesAsText("projectId")).containsExactly("pre");
    }

    @Test void computedSignalFilterAlsoFiltersAllFiveSummaryCellsAndStaleBanner() throws Exception {
        var danger = source("danger", "MO");
        danger.latestMemoDate = today.minusDays(15);
        source("normal", "MO");
        when(mapper.issues("danger")).thenReturn(List.of(
            new ProjectBoardSignalRules.Issue("1", "HIGH", "OPEN", today.minusDays(1)),
            new ProjectBoardSignalRules.Issue("2", "HIGH", "OPEN", today),
            new ProjectBoardSignalRules.Issue("3", "HIGH", "CLOSE", today.minusDays(1))));
        var result = board("signal", "위험", "contractTypeCode", "MO", "salesPartId", "sales");
        assertThat(result.path("items")).hasSize(1);
        assertThat(result.path("summary").path("riskCount").asInt()).isEqualTo(1);
        assertThat(result.path("summary").path("progress").path("danger").asInt()).isEqualTo(1);
        assertThat(result.path("summary").path("openIssues").path("total").asInt()).isEqualTo(2);
        assertThat(result.path("summary").path("openIssues").path("high").asInt()).isEqualTo(2);
        assertThat(result.path("summary").path("overdueIssueCount").asInt()).isEqualTo(1);
        assertThat(result.path("summary").path("staleProjectCount").asInt()).isEqualTo(1);
        assertThat(result.path("staleProjects").get(0).path("daysWithoutMemo").asInt()).isEqualTo(15);
        assertThat(result.path("items").get(0).path("reasonSummary").asText())
            .isEqualTo("기한 지난 중요(높음) 이슈 1건 · 15일째 상태 업데이트 없음")
            .doesNotContain("UI-04", "1차 경보");
        assertThat(result.path("items").get(0).path("signalReasonLevels").toString()).isEqualTo("[\"위험\",\"주의\"]");
    }

    @Test void typeAndPartFiltersRemoveOtherSourcesBeforeSummary() throws Exception {
        source("match", "TK");
        source("other-type", "MO");
        source("other-part", "TK").salesPartId = "other";
        var result = board("contractTypeCode", "TK", "salesPartId", "sales");
        assertThat(result.path("items").findValuesAsText("projectId")).containsExactly("match");
        assertThat(result.path("summary").path("progress").path("total").asInt()).isEqualTo(1);
    }

    @Test void ordersWorstFirstThenProjectNameInKoreanOrder() throws Exception {
        source("normal", "MO");
        source("caution", "MO").latestMemoDate = today.minusDays(15);
        source("b", "MO");
        source("a", "MO");
        source("newest", "MO").startDate = today;
        for (String id : List.of("a", "b", "newest")) when(mapper.issues(id)).thenReturn(List.of(
            new ProjectBoardSignalRules.Issue(id, "HIGH", "OPEN", today.minusDays(1))));
        assertThat(board().path("items").findValuesAsText("projectId"))
            .containsExactly("a", "b", "newest", "caution", "normal");
    }

    @ParameterizedTest @ValueSource(strings = {"MO", "DP", "IN"})
    void nonTurnkeyNeverExposesCostRatio(String type) throws Exception {
        source("non-turnkey", type).costAmount = new BigDecimal("99999");
        var row = board().path("items").get(0);
        assertThat(row.path("costUsageRate").isNull()).isTrue();
        assertThat(row.path("costProgressGap").isNull()).isTrue();
    }

    @Test void missingBudgetDoesNotHideIssueRiskOrNoMemoAge() throws Exception {
        var source = source("missing", "TK");
        source.budgetAmount = null;
        source.actualProgress = null;
        source.latestMemoDate = null;
        source.stagesCreatedDate = today.minusDays(15);
        when(mapper.issues("missing")).thenReturn(List.of(
            new ProjectBoardSignalRules.Issue("99", "HIGH", "OPEN", today.minusDays(1))));
        var row = board().path("items").get(0);
        assertThat(row.path("signal").asText()).isEqualTo("위험");
        assertThat(row.path("actualProgress").decimalValue()).isEqualByComparingTo("0");
        assertThat(row.path("costUsageRate").isNull()).isTrue();
        assertThat(row.path("latestMemoDate").isNull()).isTrue();
        assertThat(row.path("daysWithoutMemo").asInt()).isEqualTo(15);
        assertThat(row.path("lastUpdateDate").asText()).isEqualTo(today.minusDays(15).toString());
        assertThat(row.path("reasonSummary").asText()).contains("기한 지난 중요(높음) 이슈 1건", "15일째 상태 업데이트 없음");
    }

    @Test void zeroBudgetIsMissingCalculationNotDivisionError() throws Exception {
        source("zero", "TK").budgetAmount = BigDecimal.ZERO;
        var row = board().path("items").get(0);
        assertThat(row.path("costUsageRate").isNull()).isTrue();
        assertThat(row.path("signal").asText()).isEqualTo("정상");
    }

    @Test void headquartersSalesPartIsAValidFilter() throws Exception {
        source("hq", "TK").salesPartId = "HQ";
        source("other", "TK");
        assertThat(board("salesPartId", "HQ").path("items").findValuesAsText("projectId")).containsExactly("hq");
    }

    @Test void boardFirstCreatesMissingStagesThenReads() throws Exception {
        source("visible", "MO");
        board();
        var order = inOrder(stages, mapper);
        order.verify(stages).ensureMissingStages();
        order.verify(mapper).sources(any(), eq(today), eq("202611"));
    }

    @Test void fourteenDaysIsNotStaleAndFutureNoMemoAgeIsZero() throws Exception {
        source("boundary", "MO").latestMemoDate = today.minusDays(14);
        var future = source("future", "MO");
        future.latestMemoDate = null;
        future.stagesCreatedDate = today.plusDays(1);
        var result = board();
        assertThat(result.path("staleProjects")).isEmpty();
        assertThat(result.path("items").get(0).path("daysWithoutMemo").asInt()).isEqualTo(14);
        assertThat(result.path("items").get(1).path("daysWithoutMemo").asInt()).isZero();
    }

    @Test void allIndependentReasonsAndRoundedCostPercentagesSurvive() throws Exception {
        var source = source("all", "TK");
        source.latestMemoDate = today.minusDays(15);
        source.costAmount = new BigDecimal("650.49");
        Step step = new Step();
        step.stepName = "수행";
        step.weightRate = new BigDecimal("100");
        step.plannedStartDate = today.minusDays(5);
        step.plannedEndDate = today.plusDays(4);
        when(projects.steps("all")).thenReturn(List.of(step));
        when(mapper.issues("all")).thenReturn(List.of(
            new ProjectBoardSignalRules.Issue("7", "HIGH", "OPEN", today.minusDays(1))));
        var row = board().path("items").get(0);
        assertThat(row.path("plannedProgress").decimalValue()).isEqualByComparingTo("60.0");
        assertThat(row.path("costUsageRate").decimalValue()).isEqualByComparingTo("65.0");
        assertThat(row.path("costProgressGap").decimalValue()).isEqualByComparingTo("15.0");
        assertThat(row.path("signalReasons")).hasSize(4);
        assertThat(row.path("currentStepName").asText()).isEqualTo("수행");
    }

    @Test void repeatedReadRecalculatesWithoutProjectWrites() throws Exception {
        var source = source("changed", "MO");
        source.latestMemoDate = today.minusDays(15);
        assertThat(board().path("items").get(0).path("signal").asText()).isEqualTo("주의");
        source.latestMemoDate = today;
        assertThat(board().path("items").get(0).path("signal").asText()).isEqualTo("정상");
        verify(projects, never()).lockSources();
        verify(projects, never()).lockProject(anyString());
        verify(projects, never()).insertAccess(anyString(), anyString(), anyString());
        verify(stages, times(2)).ensureMissingStages();
    }

    @ParameterizedTest @ValueSource(strings = {"/api/common/project-board", "/api/common/project-board/excel"})
    void invalidSignalReturnsField400ForBothOperations(String path) throws Exception {
        mvc.perform(get(path).param("signal", "unknown")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("signal"));
        verifyNoInteractions(mapper);
        verifyNoInteractions(stages);

    }

    @ParameterizedTest @ValueSource(strings = {"/api/common/project-board", "/api/common/project-board/excel"})
    void invalidContractTypeReturnsField400(String path) throws Exception {
        mvc.perform(get(path).param("contractTypeCode", "unknown")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("contractTypeCode"));
    }

    @ParameterizedTest @ValueSource(strings = {"/api/common/project-board", "/api/common/project-board/excel"})
    void missingSalesPartReturnsField400(String path) throws Exception {
        mvc.perform(get(path).param("salesPartId", "missing")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("salesPartId"));
    }

    @Test void excelIsRealXlsxWithSameFiltersOrderNoAmountsAndNoFormulaCells() throws Exception {
        source("excluded", "MO");
        var source = source("selected", "TK");
        source.projectName = "=HYPERLINK(\"https://invalid.example\",\"unsafe\")";
        var bytes = mvc.perform(get("/api/common/project-board/excel").param("contractTypeCode", "TK"))
            .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1"))
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("selected");
            assertThat(sheet.getRow(3).getCell(2).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(3).getCell(2).getStringCellValue()).startsWith("=HYPERLINK");
            var headers = new ArrayList<String>();
            sheet.getRow(2).forEach(cell -> headers.add(cell.getStringCellValue()));
            assertThat(headers).contains("원가 사용률(%)", "진척 대비 원가(%p)")
                .noneMatch(header -> header.contains("금액") || header.contains("예산"));
        }
    }

    @Test void emptyBoardAndEmptyExcelRetainTheirContracts() throws Exception {
        assertThat(board().path("items")).isEmpty();
        var bytes = mvc.perform(get("/api/common/project-board/excel"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat((Object) book.getSheetAt(0).getRow(2)).isNotNull();
            assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(2);
        }
    }
}
