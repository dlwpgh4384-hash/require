package com.company.common.aianalysis.controller;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.aianalysis.dto.AiAnalysisContracts.Period;
import com.company.common.aianalysis.mapper.*;
import com.company.common.aianalysis.mapper.AiAnalysisRows.*;
import com.company.common.aianalysis.service.*;
import com.company.common.commonops.service.CommonBusinessPlanComparisonService;
import com.company.common.menus.mapper.MenuMapper;
import com.company.common.menus.service.DevelopedScreenRegistry;
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
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.mock.web.MockHttpServletResponse;
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
    AiAnalysisScreens.class, DevelopedScreenRegistry.class, AiAnalysisProfitFunction.class,
    AiAnalysisFinancialFunctions.class, AiAnalysisPseudonymizer.class, ExcelExportService.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class, CommonAiAnalysisControllerTest.Transactions.class})
class CommonAiAnalysisControllerTest {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @org.springframework.transaction.annotation.EnableTransactionManagement
    static class Transactions {
        @Bean
        MockMvcBuilderCustomizer authenticatedRequests(CurrentBusinessActor actor) {
            return builder -> builder.defaultRequest(get("/").with(request -> {
                // Keep the slice's security filters active and match its business actor.
                var context = SecurityContextHolder.createEmptyContext();
                var authorities = actor.roles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    actor.userId(), null, authorities));
                request.getSession().setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
                var repository = new HttpSessionCsrfTokenRepository();
                var token = repository.generateToken(request);
                repository.saveToken(token, request, null);
                // The slice's default Spring Security handler expects an XOR-masked token.
                new XorCsrfTokenRequestAttributeHandler().handle(request, new MockHttpServletResponse(), () -> token);
                var maskedToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                request.addHeader(maskedToken.getHeaderName(), maskedToken.getToken());
                return request;
            }));
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AiAnalysisProgress progress;
    @MockBean AiAnalysisMapper mapper;
    @MockBean MenuMapper menus;
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
    com.fasterxml.jackson.databind.JsonNode lastData;
    static Snapshot snapshot(String month, String revenue, String operating, String fixed) {
        return new Snapshot(month, new BigDecimal(revenue), BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal(operating), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal(operating), "N", fixed, Instant.parse("2026-11-25T00:00:00Z"));
    }
    @BeforeEach void setup() {
        stored.clear();
        when(actor.userId()).thenReturn("admin"); when(actor.roles()).thenReturn(Set.of("ADMIN"));
        var role = new RoleRows.Role(); role.roleId = "ADMIN"; role.useYn = "Y"; role.snstvViewYn = "Y";
        when(roles.selectRole("ADMIN")).thenReturn(role);
        when(menus.selectCandidates()).thenReturn(List.of());
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 11)); when(calendar.now()).thenReturn(now);
        when(transactions.getTransaction(any())).thenAnswer(c -> new SimpleTransactionStatus());
        when(periods.metadata()).thenReturn(Map.of("periodBasis", new UserPreferencePeriodBasis.Basis("202611", "202601", List.of("202610"))));
        var months = new LinkedHashMap<String, ProfitStructureMonthlyProfitService.Month>();
        months.put("202610", new ProfitStructureMonthlyProfitService.Month(snapshot("202610", "100000", "20000", "Y"), List.of(), BigDecimal.ZERO, true, false, false));
        months.put("202611", new ProfitStructureMonthlyProfitService.Month(snapshot("202611", "123456", "23456", "N"), List.of(), BigDecimal.ZERO, true, false, true));
        when(monthly.year(2026, 11, "202601")).thenReturn(months);
        when(mapper.nextId()).thenAnswer(c -> Long.toString(++sequence));
        when(mapper.insert(any())).thenAnswer(c -> { stored.add(c.getArgument(0)); return 1; });
        when(mapper.saved(anyString(), anyString())).thenAnswer(c -> stored.stream()
            .filter(r -> r.actor().equals(c.getArgument(0)) && r.id().equals(c.getArgument(1))).map(Write::answer).findFirst().orElse(null));
        when(mapper.history(anyString())).thenAnswer(c -> stored.stream().filter(r -> r.actor().equals(c.getArgument(0)))
            .sorted(Comparator.comparing(Write::id).reversed()).map(r -> new HistoryItem(r.id(), r.question(), now, r.status())).toList());
        when(mapper.download(anyString(), anyInt())).thenReturn(1);
        when(mapper.identities()).thenReturn(List.of());
    }
    Map<String, Object> input(String question) {
        return new LinkedHashMap<>(Map.of("requestId", UUID.randomUUID().toString(), "question", question, "screenId", "UI-26"));
    }
    Answer postQuestion(String question) throws Exception { return postInput(input(question)); }
    Answer postInput(Map<String, Object> input) throws Exception {
        var response = mvc.perform(post("/api/common/ai-analysis/questions").contentType("application/json").content(json.writeValueAsBytes(input)))
            .andExpect(status().isOk()).andReturn().getResponse();
        lastData = json.readTree(response.getContentAsString(StandardCharsets.UTF_8)).get("data");
        return json.readValue(lastData.toString(), Answer.class);
    }
    @Test void currentRevenueUsesF01AndPersistsExactlyTheResponse() throws Exception {
        var answer = postQuestion("이번달 매출");
        assertThat(answer.answerStatus()).isEqualTo("OK");
        var table = answer.tables().get(0);
        assertThat(table.columns()).extracting(Column::label).containsExactly("연월", "매출", "영업이익", "영업이익률");
        assertThat(table.columns().get(1).format()).isEqualTo("money"); assertThat(table.columns().get(3).format()).isEqualTo("rate");
        assertThat(table.rows().get(0).get(1).toString()).isEqualTo("123456");
        assertThat(new BigDecimal(table.rows().get(0).get(3).toString())).isEqualByComparingTo("19.0");
        assertThat(answer.conclusion()).isEqualTo("2026-11 매출은 **12만 원**, 영업이익은 **2만 원**(이익률 19.0%)입니다. 전월보다 매출이 ▲ 23.5%.");
        assertThat(answer.chart().type()).isEqualTo("line"); assertThat(answer.chart().labels()).hasSize(11);
        assertThat(answer.chart().series()).extracting(Series::name).containsExactly("매출", "영업이익");
        // 시안 매출 답은 결론+그래프만: 표는 숨김(엑셀에는 씀).
        assertThat(table.hidden()).isTrue();
        assertThat(answer.usedData()).isEqualTo("영업이익표 · 2026-01 ~ 2026-11").doesNotContain("F-01", "UI-");
        assertThat(answer.provisional()).isTrue(); assertThat(answer.provisionalMonth()).isEqualTo("2026-11");
        assertThat(answer.forecast()).isFalse(); assertThat(answer.excel()).isTrue();
        assertThat(answer.actions()).containsExactly(new Action("영업이익표 열기", "UI-08", new Period("2026-11", "2026-11")));
        assertThat(answer.assumptions()).noneMatch(a -> a.matches(".*[A-Z][a-z]+[A-Z].*|.*\\.java.*"));
        assertThat(stored).hasSize(1); assertThat(stored.get(0).baseMonth()).isEqualTo("202611"); assertThat(stored.get(0).provisional()).isEqualTo("Y");
        assertThat(json.readTree(stored.get(0).answer())).isEqualTo(lastData);
        assertThat(json.readTree(stored.get(0).answer()).has("table")).isFalse();
        mvc.perform(get("/api/common/ai-analysis/questions")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].questionId").value(answer.questionId())).andExpect(jsonPath("$.data.items[0].answerStatus").value("OK"));
        verify(monthly).year(2026, 11, "202601");
    }
    @Test void futureOrUnavailableMonthIsNoAnswerInsteadOfZeroValues() throws Exception {
        var future = postQuestion("2026년 12월 매출");
        assertThat(future.answerStatus()).isEqualTo("NO_ANS"); assertThat(future.tables()).isEmpty();
        assertThat(future.suggestedQuestions()).containsExactly("앞으로 6개월 매출은?");
        when(monthly.year(2026, 9, "202601")).thenReturn(Map.of("202609", new ProfitStructureMonthlyProfitService.Month(
            snapshot("202609", "0", "0", "N"), List.of(), BigDecimal.ZERO, false, false, true)));
        var missing = postQuestion("2026년 9월 매출");
        assertThat(missing.answerStatus()).isEqualTo("NO_ANS"); assertThat(missing.tables()).isEmpty();
    }
    @Test void cumulativeQuestionHasTotalsFromTheScreenProjection() throws Exception {
        var answer = postQuestion("2026년 10월~2026년 11월 매출");
        var table = answer.tables().get(0);
        assertThat(table.rows()).hasSize(2);
        assertThat(table.totalRow().get(0)).isEqualTo("합계");
        assertThat(new BigDecimal(table.totalRow().get(1).toString())).isEqualByComparingTo("223456");
        assertThat(answer.conclusion()).startsWith("2026-10 ~ 2026-11 누계 매출은 **22만 원**");
    }
    @Test void unknownScreenIs400WithoutNewRow() throws Exception {
        var body = input("매출"); body.put("screenId", "UI-99");
        mvc.perform(post("/api/common/ai-analysis/questions").contentType("application/json").content(json.writeValueAsBytes(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("screenId"));
        assertThat(stored).isEmpty();
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
        assertThat(answer.usedData()).isEqualTo("급여는 급여를 볼 수 있는 권한(시스템 관리자·경영진·재무 담당)에서만 답합니다.");
        assertThat(answer.tables()).isEmpty(); assertThat(stored).hasSize(1); assertThat(stored.get(0).status()).isEqualTo("NO_ANS");
        verifyNoInteractions(monthly, external);
    }
    @Test void futureOperatingProfitIsNotInvented() throws Exception {
        var answer = postQuestion("다음 달 영업이익 예측");
        assertThat(answer.answerStatus()).isEqualTo("NO_ANS"); assertThat(answer.conclusion()).isEqualTo("영업이익 예측은 계산 식이 없어 답하지 않습니다.");
        assertThat(answer.tables()).isEmpty(); verifyNoInteractions(monthly, projects, parts, plans);
    }
    @Test void unsupportedQuestionHasExactMessageAndSuggestions() throws Exception {
        var answer = postQuestion("오늘 날씨");
        assertThat(answer.conclusion()).isEqualTo("이 질문에는 아직 답을 찾지 못했습니다. 아래처럼 물어보세요.");
        assertThat(answer.suggestedQuestions()).containsExactly("다음 분기 대기 인력은?", "이번 달 적자 인력은?", "이번 달 매출과 영업이익은?");
        assertThat(answer.tables()).isEmpty(); assertThat(answer.excel()).isFalse();
        // 둘째 줄이 없는 답할 수 없음은 「사용한 데이터」 줄이 없다(「사용한 데이터 없음」을 보이지 않음).
        assertThat(answer.usedData()).isNull(); assertThat(lastData.has("usedData")).isFalse();
        assertThat(stored.get(0).description()).isNull();
    }
    @Test void sourceRollbackPrecedesIndependentErrorSnapshotCommit() throws Exception {
        when(monthly.year(anyInt(), anyInt(), anyString())).thenThrow(new IllegalStateException("source unavailable"));
        var answer = postQuestion("이번 달 매출");
        assertThat(answer.answerStatus()).isEqualTo("ERR"); assertThat(answer.conclusion()).isEqualTo("답을 만들지 못했습니다. 잠시 뒤 다시 물어 주세요.");
        assertThat(stored).hasSize(1); assertThat(stored.get(0).status()).isEqualTo("ERR");
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
        var response = mvc.perform(get("/api/common/ai-analysis/questions/" + answer.questionId() + "/excel"))
            .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn().getResponse();
        assertThat(java.net.URLDecoder.decode(response.getHeader("Content-Disposition"), StandardCharsets.UTF_8)).contains("AI 분석 도우미 답_2026-11_");
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertThat(workbook.getSheetAt(0).getRow(3).getCell(1).getNumericCellValue()).isEqualTo(123456);
            assertThat(workbook.getSheetAt(0).getRow(3).getCell(3).getNumericCellValue()).isEqualTo(19.0);
        }
        verifyNoInteractions(monthly); verify(mapper).download("admin", 1);
    }
    @Test void otherOwnersFileIs404WithoutAudit() throws Exception {
        var answer = postQuestion("이번 달 매출"); when(actor.userId()).thenReturn("other");
        mvc.perform(get("/api/common/ai-analysis/questions/" + answer.questionId() + "/excel")).andExpect(status().isNotFound());
        verify(mapper, never()).download(anyString(), anyInt());
    }
    @Test void noAnswerTableAndMalformedSavedJsonAre404() throws Exception {
        var answer = postQuestion("오늘 날씨");
        mvc.perform(get("/api/common/ai-analysis/questions/" + answer.questionId() + "/excel")).andExpect(status().isNotFound());
        when(mapper.saved("admin", "998")).thenReturn("{invalid");
        mvc.perform(get("/api/common/ai-analysis/questions/998/excel")).andExpect(status().isNotFound());
        verify(mapper, never()).download(anyString(), anyInt());
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
