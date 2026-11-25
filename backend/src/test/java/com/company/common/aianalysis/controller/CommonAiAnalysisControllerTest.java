package com.company.common.aianalysis.controller;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.aianalysis.mapper.*;
import com.company.common.aianalysis.mapper.AiAnalysisRows.*;
import com.company.common.aianalysis.service.*;
import com.company.common.commonops.service.CommonBusinessPlanComparisonService;
import com.company.common.partsettlements.service.CommonPartSettlementService;
import com.company.common.projectcostanalyses.service.CommonProjectCostAnalysisService;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService;
import com.company.common.profitstructures.mapper.ProfitStructureRows.Snapshot;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.roles.mapper.*;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonAiAnalysisController.class)
@ContextConfiguration(classes = {CommonAiAnalysisController.class, CommonAiAnalysisService.class,
    AiAnalysisInterpreter.class, AiAnalysisReadExecutor.class, AiAnalysisSnapshotWriter.class,
    AiAnalysisProjection.class, AiAnalysisProgress.class, AiAnalysisArchive.class, AiAnalysisTables.class,
    AiAnalysisFinancialFunctions.class, AiAnalysisPseudonymizer.class, ExcelExportService.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class, CommonAiAnalysisControllerTest.Transactions.class})
class CommonAiAnalysisControllerTest {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @org.springframework.transaction.annotation.EnableTransactionManagement
    static class Transactions {}
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AiAnalysisProgress progress;
    @MockBean AiAnalysisMapper mapper;
    @MockBean RoleMapper roles;
    @MockBean CurrentBusinessActor actor;
    @MockBean BusinessCalendar calendar;
    @MockBean PlatformTransactionManager transactions;
    @MockBean ProfitStructureMonthlyProfitService monthly;
    @MockBean CommonPartSettlementService parts;
    @MockBean CommonProjectCostAnalysisService projects;
    @MockBean CommonBusinessPlanComparisonService plans;
    @MockBean UserPreferencePeriodBasis periods;
    @MockBean AiAnalysisExternalGateway external;
    final List<Write> stored = new ArrayList<>();
    final Instant now = Instant.parse("2026-11-25T00:00:00Z");
    long sequence;
    @BeforeEach void setup() {
        stored.clear();
        when(actor.userId()).thenReturn("admin"); when(actor.roles()).thenReturn(Set.of("ADMIN"));
        var role = new RoleRows.Role(); role.roleId = "ADMIN"; role.useYn = "Y"; role.snstvViewYn = "Y";
        when(roles.selectRole("ADMIN")).thenReturn(role);
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 11)); when(calendar.now()).thenReturn(now);
        when(transactions.getTransaction(any())).thenAnswer(c -> new SimpleTransactionStatus());
        when(periods.metadata()).thenReturn(Map.of("periodBasis", new UserPreferencePeriodBasis.Basis("202611", "202601", List.of("202610"))));
        var snapshot = new Snapshot("202611", new BigDecimal("123456"), BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("23456"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("23456"), "N", "Y", now);
        when(monthly.year(2026, 11, "202601")).thenReturn(Map.of("202611",
            new ProfitStructureMonthlyProfitService.Month(snapshot, List.of(), BigDecimal.ZERO, true, false, false)));
        when(mapper.nextId()).thenAnswer(c -> Long.toString(++sequence));
        when(mapper.insert(any())).thenAnswer(c -> { stored.add(c.getArgument(0)); return 1; });
        when(mapper.saved(anyString(), anyString())).thenAnswer(c -> stored.stream()
            .filter(r -> r.actor().equals(c.getArgument(0)) && r.id().equals(c.getArgument(1))).map(Write::answer).findFirst().orElse(null));
        when(mapper.history(anyString())).thenAnswer(c -> stored.stream().filter(r -> r.actor().equals(c.getArgument(0)))
            .sorted(Comparator.comparing(Write::id).reversed()).map(r -> new HistoryItem(r.id(), r.question(), now, r.status())).toList());
        when(mapper.download(anyString(), anyString())).thenReturn(1);
        when(mapper.identities()).thenReturn(List.of());
    }
    Map<String, Object> input(String question) {
        return new LinkedHashMap<>(Map.of("requestId", UUID.randomUUID().toString(), "question", question, "screenId", "UI-26"));
    }
    Answer postQuestion(String question) throws Exception { return postInput(input(question)); }
    Answer postInput(Map<String, Object> input) throws Exception {
        var response = mvc.perform(post("/api/common/ai-analysis/questions").contentType("application/json").content(json.writeValueAsBytes(input)))
            .andExpect(status().isOk()).andReturn().getResponse();
        return json.readValue(json.readTree(response.getContentAsString(StandardCharsets.UTF_8)).get("data").toString(), Answer.class);
    }
    @Test void currentRevenueUsesF01AndPersistsExactlyTheResponse() throws Exception {
        var answer = postQuestion("이번달 매출");
        assertThat(answer.answerStatus()).isEqualTo("OK");
        assertThat(answer.table().rows().get(0).get(1).toString()).isEqualTo("123456");
        assertThat(answer.usedData()).contains("F-01"); assertThat(answer.provisional()).isFalse();
        assertThat(stored).hasSize(1); assertThat(stored.get(0).baseMonth()).isEqualTo("202611");
        assertThat(json.readTree(stored.get(0).answer())).isEqualTo(json.valueToTree(answer));
        mvc.perform(get("/api/common/ai-analysis/questions")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].questionId").value(answer.questionId())).andExpect(jsonPath("$.data.items[0].answerStatus").value("OK"));
        verify(monthly).year(2026, 11, "202601");
    }
    @Test void blankQuestionIs400WithoutNewRow() throws Exception {
        mvc.perform(post("/api/common/ai-analysis/questions").contentType("application/json").content(json.writeValueAsBytes(input("  "))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("question"));
        assertThat(stored).isEmpty(); verifyNoInteractions(monthly);
    }
    @Test void questionLimitCountsUnicodeCodePointsAndDoesNotTruncate() throws Exception {
        postQuestion("😀".repeat(1000)); assertThat(stored.get(0).question().codePointCount(0, stored.get(0).question().length())).isEqualTo(1000);
        mvc.perform(post("/api/common/ai-analysis/questions").contentType("application/json").content(json.writeValueAsBytes(input("😀".repeat(1001)))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("question"));
        assertThat(stored).hasSize(1);
    }
    @Test void missingRequestIdentityIs400() throws Exception {
        var body = input("매출"); body.remove("requestId");
        mvc.perform(post("/api/common/ai-analysis/questions").contentType("application/json").content(json.writeValueAsBytes(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("requestId"));
        assertThat(stored).isEmpty();
    }
    @Test void invalidOrReversedPeriodIs400() throws Exception {
        for (var period : List.of(Map.of("startMonth", "2026-13", "endMonth", "2026-13"), Map.of("startMonth", "2026-12", "endMonth", "2026-11"))) {
            var body = input("매출"); body.put("period", period);
            mvc.perform(post("/api/common/ai-analysis/questions").contentType("application/json").content(json.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("period"));
        }
        assertThat(stored).isEmpty();
    }
    @Test void salaryDeniedRemains200WithoutAnyAmountTable() throws Exception {
        roles.selectRole("ADMIN").snstvViewYn = "N";
        var answer = postQuestion("이번 달 급여");
        assertThat(answer.answerStatus()).isEqualTo("NO_ANS"); assertThat(answer.conclusion()).isEqualTo("이 내용을 볼 권한이 없습니다.");
        assertThat(answer.table()).isNull(); assertThat(stored).hasSize(1); assertThat(stored.get(0).status()).isEqualTo("NO_ANS");
        verifyNoInteractions(monthly, external);
    }
    @Test void futureOperatingProfitIsNotInvented() throws Exception {
        var answer = postQuestion("다음 달 영업이익 예측");
        assertThat(answer.answerStatus()).isEqualTo("NO_ANS"); assertThat(answer.table()).isNull(); verifyNoInteractions(monthly, projects, parts, plans);
    }
    @Test void unsupportedQuestionHasExactMessageAndSuggestions() throws Exception {
        var answer = postQuestion("오늘 날씨");
        assertThat(answer.conclusion()).isEqualTo("이 질문에는 아직 답을 찾지 못했습니다. 아래처럼 물어보세요.");
        assertThat(answer.suggestedQuestions()).hasSize(3); assertThat(answer.table()).isNull();
    }
    @Test void sourceRollbackPrecedesIndependentErrorSnapshotCommit() throws Exception {
        when(monthly.year(anyInt(), anyInt(), anyString())).thenThrow(new IllegalStateException("source unavailable"));
        var answer = postQuestion("이번 달 매출");
        assertThat(answer.answerStatus()).isEqualTo("ERR"); assertThat(stored).hasSize(1); assertThat(stored.get(0).status()).isEqualTo("ERR");
        var order = inOrder(transactions, mapper); order.verify(transactions).rollback(any()); order.verify(mapper).insert(any()); order.verify(transactions).commit(any());
        mvc.perform(get("/api/common/ai-analysis/questions")).andExpect(jsonPath("$.data.items[0].answerStatus").value("ERR"));
        var definitions = org.mockito.ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactions, atLeast(2)).getTransaction(definitions.capture());
        assertThat(definitions.getAllValues()).anySatisfy(d -> { assertThat(d.isReadOnly()).isTrue(); assertThat(d.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ); });
        assertThat(definitions.getAllValues()).anySatisfy(d -> { assertThat(d.isReadOnly()).isFalse(); assertThat(d.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW); });
    }
    @Test void historyIsOwnEvenForAdministratorAndReplayCreatesAnotherSnapshot() throws Exception {
        postQuestion("이번 달 매출"); when(actor.userId()).thenReturn("other"); postQuestion("이번 달 매출"); when(actor.userId()).thenReturn("admin");
        mvc.perform(get("/api/common/ai-analysis/questions")).andExpect(jsonPath("$.data.items.length()").value(1));
        postQuestion(stored.get(0).question()); assertThat(stored.stream().filter(r -> r.actor().equals("admin"))).hasSize(2);
        verify(monthly, times(3)).year(2026, 11, "202601");
    }
    @Test void savedExcelDoesNotRecomputeAndAuditsOnce() throws Exception {
        var answer = postQuestion("이번 달 매출"); clearInvocations(monthly);
        var bytes = mvc.perform(get("/api/common/ai-analysis/questions/" + answer.questionId() + "/excel"))
            .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheetAt(0).getRow(3).getCell(1).getNumericCellValue()).isEqualTo(123456);
        }
        verifyNoInteractions(monthly); verify(mapper).download("admin", answer.questionId());
    }
    @Test void otherOwnersFileIs404WithoutAudit() throws Exception {
        var answer = postQuestion("이번 달 매출"); when(actor.userId()).thenReturn("other");
        mvc.perform(get("/api/common/ai-analysis/questions/" + answer.questionId() + "/excel")).andExpect(status().isNotFound());
        verify(mapper, never()).download(anyString(), anyString());
    }
    @Test void noAnswerTableAndMalformedSavedJsonAre404() throws Exception {
        var answer = postQuestion("오늘 날씨");
        mvc.perform(get("/api/common/ai-analysis/questions/" + answer.questionId() + "/excel")).andExpect(status().isNotFound());
        when(mapper.saved("admin", "998")).thenReturn("{invalid");
        mvc.perform(get("/api/common/ai-analysis/questions/998/excel")).andExpect(status().isNotFound());
        verify(mapper, never()).download(anyString(), anyString());
    }
    @Test void progressReturnsActualStageAndNeverCrossesOwner() throws Exception {
        String id = UUID.randomUUID().toString(); var claim = progress.claim("admin", id);
        mvc.perform(get("/api/common/ai-analysis/requests/" + id)).andExpect(jsonPath("$.data.stage").value("데이터 찾는 중")).andExpect(jsonPath("$.data.completed").value(false));
        claim.execution().stage("계산 중");
        mvc.perform(get("/api/common/ai-analysis/requests/" + id)).andExpect(jsonPath("$.data.stage").value("계산 중"));
        when(actor.userId()).thenReturn("other"); mvc.perform(get("/api/common/ai-analysis/requests/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/common/ai-analysis/requests/missing")).andExpect(status().isNotFound());
        claim.execution().answer.complete(null);
    }
}
