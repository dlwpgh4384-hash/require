package com.company.common.salespipeline.controller;

import com.company.common.calculation.dto.CalculationRuleContracts.*;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.*;
import com.company.common.codegroups.dto.CodeContracts.Item;
import com.company.common.salesopportunities.mapper.SalesOpportunityMapper;
import com.company.common.salespipeline.mapper.*;
import com.company.common.salespipeline.mapper.SalesPipelineRows.*;
import com.company.common.salespipeline.dto.SalesPipelineContracts.*;
import com.company.common.salespipeline.service.*;
import com.company.common.support.controller.*;
import com.company.common.support.service.BusinessCalendar;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

@WebMvcTest(CommonSalesPipelineController.class)
@ContextConfiguration(classes = {CommonSalesPipelineController.class, CommonSalesPipelineService.class,
    SalesPipelineCalculationRules.class, SalesPipelineDefinitions.class, SalesPipelineApprovalStandards.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class})
class CommonSalesPipelineControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean SalesPipelineMapper mapper;
    @MockBean SalesOpportunityMapper costs;
    @MockBean SalesPipelineCodes codes;
    @MockBean CalculationRuleService metadata;
    @MockBean BusinessCalendar calendar;
    @MockBean SalesPipelineForecastService forecast;
    @MockBean SalesPipelineExcelService excel;
    @MockBean PlatformTransactionManager transactions;
    final Instant now = Instant.parse("2026-11-15T00:00:00Z");

    @BeforeEach void setup() throws Exception {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(calendar.now()).thenReturn(now);
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 11));
        String seed = new ClassPathResource("db/migration/V8__calculation_rule_seed.sql")
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        when(metadata.definition(anyString())).thenAnswer(call -> definition(seed, call.getArgument(0)));
        when(metadata.effectiveStandard(anyString(), any())).thenAnswer(call -> new Standard(call.getArgument(0),
            YearMonth.of(2026, 1), new BigDecimal(switch ((String)call.getArgument(0)) {
                case "QUOTE_APRV_RT" -> "15"; case "QUOTE_REAPRV_GAP" -> "0.5"; case "TGT_MRGN_RT" -> "20";
                case "STEP_PRBL_1" -> "10"; case "STEP_PRBL_2" -> "30"; case "STEP_PRBL_3" -> "60"; case "STEP_PRBL_4" -> "90";
                default -> throw new AssertionError(call.getArgument(0).toString());
            })));
        when(codes.selectable(anyString())).thenAnswer(call -> {
            var values = switch ((String)call.getArgument(0)) {
                case "JOB" -> List.of("BE", "PM"); case "GRADE" -> List.of("MD");
                case "SALES_STEP" -> List.of("FIND", "PROP", "NEGO", "PRE"); default -> List.<String>of();
            };
            return values.stream().map(code -> { var item = new Item(); item.code = code; item.codeName = code; return item; }).toList();
        });
        when(costs.monthlyCost("BE", "MD")).thenReturn(new BigDecimal("6800000"));
        when(costs.defaultPrice("BE")).thenReturn(new BigDecimal("8000000"));
    }

    static Definition definition(String seed, String id) {
        var pattern = Pattern.compile("\\('" + Pattern.quote(id) + "', (?:NULL|'[^']*'), '[^']*', '((?:[^']|'')*)', '((?:[^']|'')*)'", Pattern.DOTALL);
        var match = pattern.matcher(seed);
        if (!match.find()) throw new AssertionError("Missing seed definition " + id);
        return new Definition(id, match.group(1).replace("''", "'"), match.group(2).replace("''", "'"));
    }
    ObjectNode input() {
        var input = json.createObjectNode();
        input.putArray("needs").addObject().put("jobCode", "BE").put("gradeCode", "MD")
            .put("requiredCount", 2).put("startMonth", "2026-12").put("months", 6).put("monthlyMm", 1)
            .put("estimatedUnitPrice", 8000000);
        return input;
    }
    @Test void publicQuoteUsesDbDefinitionsAndDoesNotWriteBusinessData() throws Exception {
        mvc.perform(post("/api/common/sales-pipeline/quote").contentType("application/json").content(input().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.expectedRevenue").value(96000000))
            .andExpect(jsonPath("$.data.expectedCost").value(81600000))
            .andExpect(jsonPath("$.data.profitRate").value(15))
            .andExpect(jsonPath("$.data.minimumUnitPrice").value(6800000))
            .andExpect(jsonPath("$.data.targetUnitPrice").value(8500000))
            .andExpect(jsonPath("$.data.breakEvenAmount").value(81600000))
            .andExpect(jsonPath("$.data.targetAmount").value(102000000))
            .andExpect(jsonPath("$.data.needQuotes[0].rowIndex").value(0));
        verifyNoInteractions(mapper);
        verify(costs).monthlyCost("BE", "MD");
        verifyNoMoreInteractions(costs);
        verify(metadata).effectiveStandard("QUOTE_APRV_RT", YearMonth.of(2026, 11));
    }
    @ParameterizedTest @CsvSource({"6800000,8000000,목표 미달", "6400000,8000000,기준 충족", "8000000,8000000,승인 필요", "8100000,8000000,손해 예상"})
    void quoteBandUsesStrictBoundaries(String cost, int price, String band) throws Exception {
        when(costs.monthlyCost("BE", "MD")).thenReturn(new BigDecimal(cost));
        var body = input(); ((ObjectNode)body.path("needs").get(0)).put("estimatedUnitPrice", price);
        mvc.perform(post("/api/common/sales-pipeline/quote").contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.profitBand").value(band));
    }
    @ParameterizedTest @CsvSource({"requiredCount,0", "monthlyMm,0", "monthlyMm,1.01", "months,0", "estimatedUnitPrice,-1"})
    void invalidNeedPointsToExactRow(String field, BigDecimal value) throws Exception {
        var body = input(); ((ObjectNode)body.path("needs").get(0)).put(field, value);
        mvc.perform(post("/api/common/sales-pipeline/quote").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("needs[0]." + field));
        verifyNoInteractions(costs, mapper);
    }
    @Test void missingCostIsDomainErrorNotZeroQuote() throws Exception {
        when(costs.monthlyCost("BE", "MD")).thenReturn(null);
        mvc.perform(post("/api/common/sales-pipeline/quote").contentType("application/json").content(input().toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("needs[0].jobCode"));
    }
    @Test void heterogeneousNeedsKeepTheirOwnUnitPrices() throws Exception {
        when(costs.monthlyCost("PM", "MD")).thenReturn(new BigDecimal("4000000"));
        var body = input();
        ((com.fasterxml.jackson.databind.node.ArrayNode)body.path("needs")).addObject()
            .put("jobCode", "PM").put("gradeCode", "MD").put("requiredCount", 1)
            .put("startMonth", "2026-12").put("months", 6).put("monthlyMm", 1).put("estimatedUnitPrice", 6000000);
        mvc.perform(post("/api/common/sales-pipeline/quote").contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.needQuotes[0].minimumUnitPrice").value(6800000))
            .andExpect(jsonPath("$.data.needQuotes[1].rowIndex").value(1))
            .andExpect(jsonPath("$.data.needQuotes[1].minimumUnitPrice").value(4000000))
            .andExpect(jsonPath("$.data.needQuotes[1].targetUnitPrice").value(5000000));
    }
    @Test void unknownJobIsRejectedBeforeCostLookup() throws Exception {
        var body = input(); ((ObjectNode)body.path("needs").get(0)).put("jobCode", "UNKNOWN");
        mvc.perform(post("/api/common/sales-pipeline/quote").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("needs[0].jobCode"));
        verifyNoInteractions(costs, mapper);
    }
    @Test void missingGradeUsesJobWideAverageAndEmptyPriceUsesJobSource() throws Exception {
        var body = input(); var need = (ObjectNode)body.path("needs").get(0); need.putNull("gradeCode"); need.putNull("estimatedUnitPrice");
        when(costs.monthlyCost("BE", null)).thenReturn(new BigDecimal("6800000"));
        mvc.perform(post("/api/common/sales-pipeline/quote").contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.expectedRevenue").value(96000000));
        verify(costs).monthlyCost("BE", null); verify(costs).defaultPrice("BE");
    }
    @Test void unsupportedFormulaFailsInsteadOfFallback() throws Exception {
        when(metadata.definition("식-83")).thenReturn(new Definition("식-83", "unsupported", "unsupported"));
        mvc.perform(post("/api/common/sales-pipeline/quote").contentType("application/json").content(input().toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("calcId"));
        verifyNoInteractions(costs, mapper);
    }
    Opportunity opportunity(String id, String stage, Instant modified) {
        return new Opportunity(id, id, "customer", "고객사", stage, new BigDecimal("30"), new BigDecimal("100000000"),
            "2026-12", "제안서", LocalDate.of(2026, 11, 1), "WAIT", modified, new BigDecimal("2"));
    }
    @Test void pipelineHasFourStagesAndExactlyFourteenDaysIsNotStale() throws Exception {
        when(mapper.ongoing()).thenReturn(List.of(opportunity("exact", "FIND", now.minus(Duration.ofDays(14))),
            opportunity("old", "PROP", now.minus(Duration.ofDays(14)).minusSeconds(1))));
        when(mapper.requiredJobs()).thenReturn(List.of(new RequiredJob("exact", "BE", "백엔드 개발", 2),
            new RequiredJob("exact", "PM", "PM", 1), new RequiredJob("old", "BE", "백엔드 개발", 1)));
        mvc.perform(get("/api/common/sales-pipeline"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.ongoingCount").value(2))
            .andExpect(jsonPath("$.data.summary.staleCount").value(1))
            .andExpect(jsonPath("$.data.summary.weightedAmount").value(60000000))
            .andExpect(jsonPath("$.data.stages.length()").value(4))
            .andExpect(jsonPath("$.data.stages[0].items[0].stale").value(false))
            .andExpect(jsonPath("$.data.stages[1].items[0].stale").value(true))
            .andExpect(jsonPath("$.data.stages[0].items[0].requiredJobs.length()").value(2))
            .andExpect(jsonPath("$.data.stages[0].items[0].requiredJobs[0]").value("BE"))
            .andExpect(jsonPath("$.data.stages[0].items[0].requiredJobHeadcounts[0].jobName").value("백엔드 개발"))
            .andExpect(jsonPath("$.data.stages[0].items[0].requiredJobHeadcounts[0].headcount").value(2))
            .andExpect(jsonPath("$.data.stages[0].items[0].requiredJobHeadcounts[1].jobCode").value("PM"))
            .andExpect(jsonPath("$.data.stages[1].items[0].requiredJobHeadcounts[0].headcount").value(1));
        // 카드마다 따로 읽지 않고 필요 직무는 한 번에 읽는다.
        verify(mapper, times(1)).requiredJobs();
    }
    @Test void quoteRoundsProfitRateToTwoDecimalsAndMinimumUnitPriceToWholeWon() throws Exception {
        when(costs.monthlyCost("BE", "MD")).thenReturn(new BigDecimal("6800000"));
        when(costs.monthlyCost("PM", "MD")).thenReturn(new BigDecimal("4000001"));
        var body = input(); ((ObjectNode)body.path("needs").get(0)).put("estimatedUnitPrice", 7777777);
        ((com.fasterxml.jackson.databind.node.ArrayNode)body.path("needs")).addObject()
            .put("jobCode", "PM").put("gradeCode", "MD").put("requiredCount", 1)
            .put("startMonth", "2026-12").put("months", 1).put("monthlyMm", 0.33).put("estimatedUnitPrice", 6000000);
        var response = mvc.perform(post("/api/common/sales-pipeline/quote").contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var data = json.readTree(response).path("data");
        assertThat(data.path("profitRate").decimalValue().scale()).isLessThanOrEqualTo(2);
        assertThat(data.path("profitRate").decimalValue()).isEqualByComparingTo("13.00");
        assertThat(data.path("minimumUnitPrice").decimalValue().stripTrailingZeros().scale()).isLessThanOrEqualTo(0);
        assertThat(data.path("minimumUnitPrice").decimalValue()).isEqualByComparingTo("6725061");
    }
    @Test void closedFiltersArePassedTogetherWithoutScope() throws Exception {
        when(mapper.closed(new ClosedFilter("WIN", false))).thenReturn(List.of(new Closed("won", "수주", "고객", "WIN",
            BigDecimal.TEN, LocalDate.of(2026, 11, 1), "project", null)));
        mvc.perform(get("/api/common/sales-pipeline/closed").param("result", "WIN"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].projectId").value("project"));
        verify(mapper).closed(new ClosedFilter("WIN", false));
    }
    @Test void stoppedInclusionIsExplicit() throws Exception {
        when(mapper.closed(new ClosedFilter(null, true))).thenReturn(List.of());
        mvc.perform(get("/api/common/sales-pipeline/closed").param("includeStopped", "true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isArray());
        verify(mapper).closed(new ClosedFilter(null, true));
    }
    @Test void invalidClosedResultIs400() throws Exception {
        mvc.perform(get("/api/common/sales-pipeline/closed").param("result", "ING"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("result"));
        verifyNoInteractions(mapper);
    }
    @Test void stoppedResultWithoutStoppedInclusionIs400NotSilentlyEmpty() throws Exception {
        mvc.perform(get("/api/common/sales-pipeline/closed").param("result", "STOP"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("includeStopped"))
            .andExpect(jsonPath("$.message").value("사용 중지한 기회는 [사용 중지 포함]을 켠 뒤 볼 수 있습니다."));
        verifyNoInteractions(mapper);
        when(mapper.closed(new ClosedFilter("STOP", true))).thenReturn(List.of());
        mvc.perform(get("/api/common/sales-pipeline/closed").param("result", "STOP").param("includeStopped", "true"))
            .andExpect(status().isOk());
    }
    @Test void pipelineSummaryCountsClosedTabWithoutTheClosedList() throws Exception {
        when(mapper.ongoing()).thenReturn(List.of());
        when(mapper.closedCount()).thenReturn(3);
        mvc.perform(get("/api/common/sales-pipeline")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.closedCount").value(3));
        verify(mapper, never()).closed(any());
    }
    @Test void forecastPublicHttpEnvelope() throws Exception {
        when(forecast.getCommonSalesPipelineForecast(null)).thenReturn(new Forecast(List.of(), List.of(), List.of(new ForecastOpportunity("O-1", "고객 포털",
            new java.math.BigDecimal("10"), List.of(new ForecastOpportunityMonth("2026-12", new java.math.BigDecimal("1000000")))))));
        mvc.perform(get("/api/common/sales-pipeline/forecast")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.months").isArray()).andExpect(jsonPath("$.data.hiringReviews").isArray())
            .andExpect(jsonPath("$.data.opportunityRevenues[0].opportunityName").value("고객 포털"))
            .andExpect(jsonPath("$.data.opportunityRevenues[0].winningProbability").value(10))
            .andExpect(jsonPath("$.data.opportunityRevenues[0].months[0].month").value("2026-12"))
            .andExpect(jsonPath("$.data.opportunityRevenues[0].months[0].weightedRevenue").value(1000000));
    }
    @Test void forecastPassesStartMonthAndInvalidStartMonthIs400() throws Exception {
        when(forecast.getCommonSalesPipelineForecast("2026-11")).thenReturn(new Forecast(List.of(), List.of(), List.of()));
        mvc.perform(get("/api/common/sales-pipeline/forecast").param("startMonth", "2026-11")).andExpect(status().isOk());
        verify(forecast).getCommonSalesPipelineForecast("2026-11");
        when(forecast.getCommonSalesPipelineForecast("2026-1")).thenThrow(
            com.company.common.support.service.CodeFailure.invalid("startMonth", "시작 달은 yyyy-MM 모양으로 넣어 주세요."));
        mvc.perform(get("/api/common/sales-pipeline/forecast").param("startMonth", "2026-1")).andExpect(status().isBadRequest());
    }
    @Test void workbookHeadersUseActualMonthRowCount() throws Exception {
        byte[] bytes = new com.company.common.support.service.ExcelExportService().write("예측", List.of("년월"),
            java.util.stream.IntStream.range(0, 6).mapToObj(i -> List.of(YearMonth.of(2026, 12).plusMonths(i).toString())).toList(), "admin", now);
        when(excel.getCommonSalesPipelineForecastExcel()).thenReturn(new Workbook(bytes, "예측_2026-12~2027-05_20261115_0900.xlsx", 6));
        mvc.perform(get("/api/common/sales-pipeline/forecast/excel")).andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "6"))
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}
