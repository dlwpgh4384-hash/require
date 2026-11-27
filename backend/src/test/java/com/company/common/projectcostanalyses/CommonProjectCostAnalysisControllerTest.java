package com.company.common.projectcostanalyses;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.*;
import com.company.common.projectcostanalyses.controller.CommonProjectCostAnalysisController;
import com.company.common.projectcostanalyses.mapper.ProjectCostAnalysisMapper;
import com.company.common.projectcostanalyses.mapper.ProjectCostAnalysisRows.*;
import com.company.common.projectcostanalyses.service.*;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.roles.dto.RoleContracts.Role;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.company.common.projectcostanalyses.ProjectCostAnalysisTestSources.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonProjectCostAnalysisController.class)
@ContextConfiguration(classes = {CommonProjectCostAnalysisController.class, CommonProjectCostAnalysisService.class,
    ProjectCostAnalysisExcelService.class, ProjectCostAnalysisDefinitions.class, CalculationRuleService.class,
    UserPreferencePeriodBasis.class, CodeExceptionHandler.class, ApiExceptionHandler.class,
    ExcelExportService.class, BusinessCalendar.class, CurrentBusinessActor.class})
class CommonProjectCostAnalysisControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ProjectCostAnalysisMapper mapper;
    @MockBean CalculationRuleMapper definitions;
    @MockBean UserPreferencePeriodSourceMapper periods;
    @MockBean PlatformTransactionManager transactions;
    static final String PATH = "/api/common/project-cost-analyses";
    @Test void monthlyCostsExposeDurableResponseContract() throws Exception {
        mvc.perform(get("/api/common/project-cost-analyses/P/monthly-costs").param("year","2026").param("baseMonth","1"))
            .andExpect(status().isOk()).andExpect(com.company.common.support.controller.OpenApiResponseContract
                .response("GET", "/api/common/project-cost-analyses/{projectId}/monthly-costs"));
        verify(mapper,never()).insertAccess(anyString(),anyString());
    }
    @Test void workforceExposesDurableResponseContract() throws Exception {
        mvc.perform(get("/api/common/project-cost-analyses/P/workforce").param("year","2026").param("baseMonth","1"))
            .andExpect(status().isOk()).andExpect(com.company.common.support.controller.OpenApiResponseContract
                .response("GET", "/api/common/project-cost-analyses/{projectId}/workforce"));
        verify(mapper,never()).insertAccess(anyString(),anyString());
    }
    @Test void causesExposeDurableResponseContract() throws Exception {
        mvc.perform(get("/api/common/project-cost-analyses/P/causes").param("year","2026").param("baseMonth","1"))
            .andExpect(status().isOk()).andExpect(com.company.common.support.controller.OpenApiResponseContract
                .response("GET", "/api/common/project-cost-analyses/{projectId}/causes"));
        verify(mapper,never()).insertAccess(anyString(),anyString());
    }
    @Test void simulationExposesDurableResponseContractWithoutWrites() throws Exception {
        mvc.perform(get("/api/common/project-cost-analyses/P/simulation").param("year","2026").param("baseMonth","1")
                .param("headcountChange","1").param("monthlyCostPerPerson","100").param("months","1").param("monthlyPricePerPerson","200"))
            .andExpect(status().isOk()).andExpect(com.company.common.support.controller.OpenApiResponseContract
                .response("GET", "/api/common/project-cost-analyses/{projectId}/simulation"));
        verify(mapper,never()).insertAccess(anyString(),anyString());
    }
    @Test void excelRejectsInvalidMonthWithoutDownloadAudit() throws Exception {
        mvc.perform(get("/api/common/project-cost-analyses/excel").param("year","2026").param("baseMonth","13"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("baseMonth"));
        verify(mapper,never()).selectSnapshots(any());
        verify(mapper,never()).insertAccess(anyString(),anyString());
    }
    @BeforeEach void setup() throws Exception {
        ProjectCostAnalysisTestSources.definitions(definitions);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(periods.selectCashBaseYm()).thenReturn(n("202501"));
        when(periods.selectClosedMonths()).thenReturn(List.of("202512", "202601"));
        when(mapper.selectProjects()).thenReturn(List.of(project("P", "MO")));
        when(mapper.selectHistory()).thenReturn(List.of());
        when(mapper.selectSnapshots(any())).thenReturn(List.of(cost("P", "202601", "1000", "770")));
        when(mapper.selectInputs(any())).thenReturn(List.of(input("P","202601","A","P-paid","NEW")));
        when(mapper.selectPeople()).thenReturn(List.of(person("A")));
        when(mapper.selectPayroll(any())).thenReturn(List.of());
        when(mapper.selectExpenses(any())).thenReturn(List.of());
        when(mapper.selectDistributions(any())).thenReturn(List.of());
        when(mapper.selectSalaries()).thenReturn(List.of());
        when(mapper.selectProgress(any())).thenReturn(List.of());
        when(mapper.selectLeaves(any())).thenReturn(List.of());
        when(mapper.selectCodes()).thenReturn(codes());
        when(mapper.insertAccess(anyString(),anyString())).thenReturn(1);
    }
    MockHttpServletRequestBuilder request(String suffix) {
        return get(PATH + suffix).with(req -> {
            java.util.Map.of("year","2026","baseMonth","1","headcountChange","1",
                "monthlyCostPerPerson","100","months","1","monthlyPricePerPerson","200")
                .forEach((key,value) -> { if (req.getParameter(key) == null) req.setParameter(key,value); });
            return req;
        });
    }
    @ParameterizedTest @ValueSource(strings={"", "/P/monthly-costs", "/P/workforce", "/P/causes", "/P/simulation"})
    void publicGetUsesFixedDevelopmentActorAndDoesNotWrite(String suffix) throws Exception {
        mvc.perform(request(suffix)).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data").isMap());
        verify(mapper,never()).insertAccess(anyString(),anyString());
    }
    @ParameterizedTest @ValueSource(strings={"", "/excel", "/P/monthly-costs", "/P/workforce", "/P/causes", "/P/simulation"})
    void everyOperationRejectsInvalidMonthBeforeReadingOrWritingSources(String suffix) throws Exception {
        mvc.perform(request(suffix).param("baseMonth","13")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.errors[0].field").value("baseMonth"));
        verify(mapper,never()).selectSnapshots(any());
        verify(mapper,never()).insertAccess(anyString(),anyString());
    }
    @ParameterizedTest @ValueSource(strings={"year","baseMonth"})
    void malformedPeriodIsAFieldError(String field) throws Exception {
        mvc.perform(get(PATH).param("year",field.equals("year") ? "bad" : "2026")
            .param("baseMonth",field.equals("baseMonth") ? "bad" : "1"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
    }
    @Test void requiredNullablePublicFieldsArePresent() throws Exception {
        var body = json.readTree(mvc.perform(request("")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var item = body.path("data").path("items").get(0);
        for (var field : com.company.common.projectcostanalyses.dto.ProjectCostAnalysisContracts.CommonProjectCostAnalysisRow.class.getRecordComponents())
            assertThat(item.has(field.getName())).as(field.getName()).isTrue();
        assertThat(body.path("data").path("summary").size()).isEqualTo(5);
    }
    @Test void filtersDoNotChangeSummaryOrOptionsAndCountMatchesFilteredItems() throws Exception {
        when(mapper.selectProjects()).thenReturn(List.of(project("P","MO"),project("Q","TK")));
        var all = json.readTree(mvc.perform(request("")).andReturn().getResponse().getContentAsString()).path("data");
        var filtered = json.readTree(mvc.perform(request("").param("projectId","P").param("contractTypeCode","MO")
            .param("projectStatus","진행").param("alertOnly","true")).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(filtered.path("summary")).isEqualTo(all.path("summary"));
        assertThat(filtered.path("projectOptions")).isEqualTo(all.path("projectOptions"));
        assertThat(filtered.path("totalCount").intValue()).isEqualTo(filtered.path("items").size());
    }
    @Test void cumulativeFiguresIncludePriorYearWhileMonthlyMarginUsesOnlySelectedMonth() throws Exception {
        when(mapper.selectSnapshots(any())).thenReturn(List.of(cost("P","202512","2000","1000"),cost("P","202601","1000","770")));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].cumulativeRevenue").value(3000))
            .andExpect(jsonPath("$.data.items[0].totalCost").value(1830))
            .andExpect(jsonPath("$.data.items[0].monthlyMarginRate").value(20));
    }
    @Test void eligiblePopulationExcludesFutureAndUnawardedAndLostProjects() throws Exception {
        var p = project("P","MO");
        var future = new Project("F","미래","MO","월단위","ING",java.time.LocalDate.of(2026,2,1),p.endDate(),p.originalAmount(),p.contractAmount(),p.budget(),p.team(),p.expense(),p.plannedMm(),null);
        var lost = new Project("L","실주","MO","월단위","LOSE",p.startDate(),p.endDate(),p.originalAmount(),p.contractAmount(),p.budget(),p.team(),p.expense(),p.plannedMm(),null);
        var scheduled = new Project("S","수주예정","MO","월단위","EXP",p.startDate(),p.endDate(),p.originalAmount(),p.contractAmount(),p.budget(),p.team(),p.expense(),p.plannedMm(),null);
        when(mapper.selectProjects()).thenReturn(List.of(p,future,lost,scheduled));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].projectId").value("P"));
    }
    @Test void exactlyFivePointsBelowTargetIsNotAnAlertAndEightyIsOrange() throws Exception {
        when(mapper.selectSnapshots(any())).thenReturn(List.of(cost("P","202601","1000","820")));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].monthlyMarginRate").value(15))
            .andExpect(jsonPath("$.data.items[0].lowMargin").value(false));
        when(mapper.selectSnapshots(any())).thenReturn(List.of(cost("P","202601","1000","770")));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].budgetColor").value("주황"));
    }
    @Test void hundredPercentIsRedAndCountedInSummary() throws Exception {
        when(mapper.selectSnapshots(any())).thenReturn(List.of(cost("P","202601","1000","970")));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].budgetColor").value("빨강"))
            .andExpect(jsonPath("$.data.items[0].budgetExceeded").value(true))
            .andExpect(jsonPath("$.data.summary.budgetExceededCount").value(1));
    }
    @Test void missingFutureSalaryCountsAsZeroAndKeepsMarginWithReason() throws Exception {
        // 식-20 ④: 월 인건비가 다 없으면 0으로 세고 「단가 없음」. 예상 최종 마진율·목표 대비는 그대로 계산한다.
        when(mapper.selectProjects()).thenReturn(List.of(project("P","TK")));
        when(mapper.selectInputs(any())).thenReturn(List.of(input("P","202602","A","P-paid","NEW")));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].expectedFinalMarginRate").value(36))
            .andExpect(jsonPath("$.data.items[0].targetDifference").value(16))
            .andExpect(jsonPath("$.data.items[0].remainingCost").value(0))
            .andExpect(jsonPath("$.data.items[0].missingMonthlyCost").value(true))
            .andExpect(jsonPath("$.data.items[0].missingMonthlyCostReason").value("단가 없음: 개발자 A"))
            .andExpect(jsonPath("$.data.items[0].remainingCostBasis").value("투입 배정의 남은 달 MM × 월 인건비 (1명, 단가 없음 1명은 0으로 셈)"));
    }
    @Test void distributionSuppliesForecastWhenNoPayOrPoolSalary() throws Exception {
        // 식-20 ③: 배분표 남은 달 금액 ÷ MM × 예산 ÷ 계약금액 = 600 ÷ 0.5 × 1000 ÷ 1250 = 960, 0.5MM → 480
        when(mapper.selectProjects()).thenReturn(List.of(project("P","TK")));
        when(mapper.selectInputs(any())).thenReturn(List.of(input("P","202602","A","P-paid","NEW")));
        when(mapper.selectPersonDistributions()).thenReturn(List.of(new PersonDistribution("P","A","202602",n("600"))));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].remainingCost").value(480))
            .andExpect(jsonPath("$.data.items[0].missingMonthlyCost").value(false))
            .andExpect(jsonPath("$.data.items[0].expectedFinalMarginRate").value(-2.4));
    }
    @Test void latestPayWinsOverPoolSalaryAndIsTruncatedPerPerson() throws Exception {
        when(mapper.selectProjects()).thenReturn(List.of(project("P","TK")));
        when(mapper.selectInputs(any())).thenReturn(List.of(new Input("202602","A","P","P-paid","개발","NEW",n("0.33"),n("0"),null)));
        when(mapper.selectSalaries()).thenReturn(List.of(new Salary("A","202501",null,n("100"))));
        when(mapper.selectPayroll(any())).thenReturn(List.of(new com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Pay(
            "202601","A","CT",n("101"),n("0"),n("0"),n("0"),null,null,null,null,null)));
        // 0.33 × 101 = 33.33 → 33(원 단위 버림)
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].remainingCost").value(33));
    }
    @Test void turnkeyRevenueIsTruncatedToWon() throws Exception {
        when(mapper.selectProjects()).thenReturn(List.of(project("P","TK")));
        when(mapper.selectSnapshots(any())).thenReturn(List.of(cost("P","202601","1000","771")));
        // 1250 × 801 ÷ 1000 = 1001.25 → 1001
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].cumulativeRevenue").value(1001))
            .andExpect(jsonPath("$.data.items[0].cumulativeProfit").value(200));
    }
    @Test void projectEndedBeforeBaseMonthIsShownAsEndedEvenIfStatusIsOpen() throws Exception {
        var p = project("P","TK");
        when(mapper.selectProjects()).thenReturn(List.of(new Project("P",p.projectName(),"TK","TK","ING",p.startDate(),
            java.time.LocalDate.of(2026,1,31),p.originalAmount(),p.contractAmount(),p.budget(),p.team(),p.expense(),p.plannedMm(),null)));
        // 2026-01-31에 끝났고 기준월은 2026-02 → 종료(상태 코드가 ING여도). 종료 턴키도 예상 최종 마진율은 보인다(남은 원가 0).
        mvc.perform(request("").param("baseMonth","2")).andExpect(jsonPath("$.data.items[0].projectStatus").value("종료"))
            .andExpect(jsonPath("$.data.items[0].remainingCost").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.items[0].expectedFinalMarginRate").value(36));
        mvc.perform(request("").param("baseMonth","2").param("projectStatus","종료")).andExpect(jsonPath("$.data.totalCount").value(1));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].projectStatus").value("진행"));
    }
    @Test void openMonthWithoutPayrollSkipsMonthlyMarginAndItsAlert() throws Exception {
        when(periods.selectClosedMonths()).thenReturn(List.of("202512"));
        mvc.perform(request("")).andExpect(jsonPath("$.data.monthlyMarginNotice").value("1월 급여가 아직 반영되지 않아 월간 마진을 계산하지 않습니다"))
            .andExpect(jsonPath("$.data.items[0].monthlyMarginRate").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.items[0].lowMargin").value(false))
            .andExpect(jsonPath("$.data.items[0].alerts").isEmpty());
        mvc.perform(request("/P/simulation")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("baseMonth"));
        when(periods.selectClosedMonths()).thenReturn(List.of("202512", "202601"));
        mvc.perform(request("")).andExpect(jsonPath("$.data.monthlyMarginNotice").value(org.hamcrest.Matchers.nullValue()));
    }
    @Test void openMonthWithoutPayrollLeavesThatMonthMarginEmptyInTheMonthlyDetail() throws Exception {
        // 상세 월별 원가 표도 목록 월간 마진과 같이: 급여 없는 작업중 달(202601)의 마진율은 비운다
        when(periods.selectClosedMonths()).thenReturn(List.of("202512"));
        mvc.perform(request("/P/monthly-costs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[1].yearMonth").value("202601"))
            .andExpect(jsonPath("$.data.items[1].revenue").isNumber())
            .andExpect(jsonPath("$.data.items[1].marginRate").value(org.hamcrest.Matchers.nullValue()));
    }
    @Test void projectEditedAfterTheBaseMonthKeepsItsAmountsWithoutAmountHistory() throws Exception {
        // 이름만 고쳐 수정 시각이 기준월 뒤여도 금액 이력이 없으면 지금 금액이 그때 금액이다
        var before = json.readTree(mvc.perform(request("")).andReturn().getResponse().getContentAsString()).path("data");
        var p = project("P","MO");
        when(mapper.selectProjects()).thenReturn(List.of(new Project(p.projectId(),p.projectName(),p.contractTypeCode(),p.contractTypeName(),
            p.statusCode(),p.startDate(),p.endDate(),p.originalAmount(),p.contractAmount(),p.budget(),p.team(),p.expense(),p.plannedMm(),
            java.time.LocalDate.of(2026,2,10))));
        when(mapper.selectHistory()).thenReturn(List.of(new History("P","ETC","프로젝트명","옛 이름","프로젝트 P",java.time.LocalDate.of(2026,2,10))));
        var after = json.readTree(mvc.perform(request("")).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(after.path("items").get(0).path("contractAmount").decimalValue()).isEqualByComparingTo("1250");
        assertThat(after).isEqualTo(before);
    }
    @Test void periodMustBeBetweenCashBaseAndLatestMonth() throws Exception {
        // 마감 202512·202601 → 가장 최근 달 202602
        mvc.perform(request("").param("baseMonth","2")).andExpect(status().isOk());
        mvc.perform(request("").param("baseMonth","3")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("baseMonth"))
            .andExpect(jsonPath("$.errors[0].message").value("2026년 2월까지 선택할 수 있습니다."));
        mvc.perform(request("").param("year","2024").param("baseMonth","12")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("year"))
            .andExpect(jsonPath("$.errors[0].message").value("2025년 1월부터 선택할 수 있습니다."));
    }
    @Test void unknownProjectFilterIsAFieldError() throws Exception {
        mvc.perform(request("").param("projectId","NOPE")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("projectId"));
    }
    @Test void rowsAndOptionsAreSortedByAlertsThenKoreanName() throws Exception {
        var p = project("P","MO");
        java.util.function.BiFunction<String,String,Project> named = (id, name) -> new Project(id,name,"MO","MO","ING",
            p.startDate(),p.endDate(),p.originalAmount(),p.contractAmount(),p.budget(),p.team(),p.expense(),p.plannedMm(),null);
        when(mapper.selectProjects()).thenReturn(List.of(named.apply("A1","하늘"),named.apply("A2","가람"),named.apply("A3","나래")));
        when(mapper.selectSnapshots(any())).thenReturn(List.of(cost("A1","202601","1000","990")));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].projectName").value("하늘"))
            .andExpect(jsonPath("$.data.items[1].projectName").value("가람"))
            .andExpect(jsonPath("$.data.items[2].projectName").value("나래"))
            .andExpect(jsonPath("$.data.projectOptions[0].projectName").value("가람"))
            .andExpect(jsonPath("$.data.projectOptions[2].projectName").value("하늘"));
    }
    @Test void alertsUseMockupWordsAndCarryPrimaryCause() throws Exception {
        when(mapper.selectSnapshots(any())).thenReturn(List.of(cost("P","202601","1000","970")));
        // 예산 소진 100%, 단가 대비 원가 = 970 − 1000 × 80% = 170
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].alerts").value(org.hamcrest.Matchers.contains("저마진","예산 초과")))
            .andExpect(jsonPath("$.data.items[0].primaryCauseName").value("원가가 단가의 80%를 넘는 인력"))
            .andExpect(jsonPath("$.data.items[0].primaryCauseAmount").value(170));
        when(mapper.selectSnapshots(any())).thenReturn(List.of(cost("P","202601","1000","950")));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].alerts").value(org.hamcrest.Matchers.contains("저마진")));
    }
    @Test void priceCauseUsesOperatingStandardAndFreeCauseHasNoButton() throws Exception {
        when(mapper.selectSnapshots(any())).thenReturn(List.of(cost("P","202601","1000","900")));
        mvc.perform(request("/P/causes")).andExpect(jsonPath("$.data.items[?(@.causeKey=='pay')].amount").value(100))
            .andExpect(jsonPath("$.data.items[?(@.causeKey=='pay')].owner").value("영업 파트장"))
            .andExpect(jsonPath("$.data.items[?(@.causeKey=='pay')].targetScreenId").value("UI-32"))
            .andExpect(jsonPath("$.data.items[?(@.causeKey=='pay')].targetLabel").value("단가 재협상 대상"))
            .andExpect(jsonPath("$.data.items[?(@.causeKey=='free')].causeName").value("무상 지원"))
            .andExpect(jsonPath("$.data.items[?(@.causeKey=='free')].owner").value("영업 파트장"))
            .andExpect(jsonPath("$.data.items[?(@.causeKey=='free')].task").value("고객과 유상 전환이나 추가 계약을 협의합니다"))
            .andExpect(jsonPath("$.data.items[?(@.causeKey=='free')].targetScreenId").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
            .andExpect(jsonPath("$.data.items[?(@.causeKey=='mm')].targetScreenId").value("UI-11"));
        var standard = new com.company.common.calculation.mapper.CalculationStandardRow();
        standard.standardCode = "COST_OVER_UPRC_RT"; standard.effectiveMonth = "202501"; standard.value = n("70");
        when(definitions.selectEffectiveStandard(eq("COST_OVER_UPRC_RT"), anyString())).thenReturn(standard);
        mvc.perform(request("/P/causes")).andExpect(jsonPath("$.data.items[?(@.causeKey=='pay')].amount").value(200))
            .andExpect(jsonPath("$.data.costOverRate").value(70))
            .andExpect(jsonPath("$.data.items[?(@.causeKey=='pay')].causeName").value("원가가 단가의 70%를 넘는 인력"));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].startDate").value("2025-12-01"))
            .andExpect(jsonPath("$.data.items[0].endDate").value("2026-12-31"));
    }
    @Test void turnkeyPriceCauseUsesPersonRevenueNotBillPrice() throws Exception {
        // 식-55: 턴키 사람 월 단가 = 그 달 사람 매출(배분표, 없으면 배분 원가 × 계약 ÷ 예산). 매출 1000, 원가 770 → 원인 0
        when(mapper.selectProjects()).thenReturn(List.of(project("P","TK")));
        mvc.perform(request("/P/causes")).andExpect(jsonPath("$.data.items[?(@.causeKey=='pay')].amount").value(0));
    }
    @Test void turnkeyProgressExcessAndRemainder() throws Exception {
        when(mapper.selectProjects()).thenReturn(List.of(project("P","TK")));
        when(mapper.selectProgress(any())).thenReturn(List.of(new Progress("P",n("50"))));
        // 진척 50% → 예산 1000 × 50% = 500, 실제 800 → 300 초과. 세 원인 합 0 → 나머지 300
        mvc.perform(request("/P/causes")).andExpect(jsonPath("$.data.expectedCostAtProgress").value(500))
            .andExpect(jsonPath("$.data.actualProgressRate").value(50))
            .andExpect(jsonPath("$.data.cumulativeCost").value(800))
            .andExpect(jsonPath("$.data.progressExcessAmount").value(300))
            .andExpect(jsonPath("$.data.remainderAmount").value(300))
            .andExpect(jsonPath("$.data.primaryBarColor").value("빨강"));
    }
    @Test void effectivePoolSalarySuppliesForecastWithoutAnAllocationTable() throws Exception {
        when(mapper.selectProjects()).thenReturn(List.of(project("P","TK")));
        when(mapper.selectInputs(any())).thenReturn(List.of(input("P","202602","A","P-paid","NEW")));
        when(mapper.selectSalaries()).thenReturn(List.of(new Salary("A","202501",null,n("100"))));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].expectedFinalMarginRate").value(32))
            .andExpect(jsonPath("$.data.items[0].missingMonthlyCost").value(false));
    }
    @Test void missingPlanRemainsNullAndDoesNotGenerateExcessInputCause() throws Exception {
        var p = project("P","MO");
        when(mapper.selectProjects()).thenReturn(List.of(new Project(p.projectId(),p.projectName(),p.contractTypeCode(),p.contractTypeName(),
            p.statusCode(),p.startDate(),p.endDate(),p.originalAmount(),p.contractAmount(),p.budget(),p.team(),p.expense(),null,null)));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].plannedMm").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(request("/P/causes")).andExpect(jsonPath("$.data.items[?(@.causeKey == 'mm')]").isEmpty());
    }
    @Test void monthlyDetailUsesStoredCostAndTurnkeyReferenceRevenue() throws Exception {
        // 식-25: 배분표 없음 → 그 달 원가 × 계약 ÷ 예산(800 × 1.25 = 1000). 경과한 달(2025-12, 2026-01)을 모두 보인다.
        when(mapper.selectProjects()).thenReturn(List.of(project("P","TK")));
        mvc.perform(request("/P/monthly-costs")).andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].yearMonth").value("202512"))
            .andExpect(jsonPath("$.data.items[0].cost").value(0))
            .andExpect(jsonPath("$.data.items[1].cost").value(800))
            .andExpect(jsonPath("$.data.items[1].mm").value(1))
            .andExpect(jsonPath("$.data.items[1].distributionRevenue").value(1000))
            .andExpect(jsonPath("$.data.items[1].revenue").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.items[1].marginRate").value(20));
    }
    @Test void turnkeyMonthlyReferenceSumsToCumulativeRevenue() throws Exception {
        when(mapper.selectProjects()).thenReturn(List.of(project("P","TK")));
        when(mapper.selectSnapshots(any())).thenReturn(List.of(cost("P","202512","0","3"),cost("P","202601","1000","4")));
        // 원가 33·34 → 누적 매출 버림(1250 × 67 ÷ 1000) = 83. 달별 41(버림 41.25) + 끝자리 보정 42 = 83
        var body = json.readTree(mvc.perform(request("/P/monthly-costs")).andReturn().getResponse().getContentAsString());
        var revenue = json.readTree(mvc.perform(request("")).andReturn().getResponse().getContentAsString())
            .at("/data/items/0/cumulativeRevenue").decimalValue();
        assertThat(revenue).isEqualByComparingTo("83");
        assertThat(body.at("/data/items/0/distributionRevenue").decimalValue()).isEqualByComparingTo("41");
        assertThat(body.at("/data/items/0/distributionRevenue").decimalValue()
            .add(body.at("/data/items/1/distributionRevenue").decimalValue())).isEqualByComparingTo(revenue);
    }
    @Test void distributionTableWinsForTurnkeyReference() throws Exception {
        when(mapper.selectProjects()).thenReturn(List.of(project("P","TK")));
        when(mapper.selectDistributions(any())).thenReturn(List.of(new Expense("P","202601",n("900"))));
        mvc.perform(request("/P/monthly-costs")).andExpect(jsonPath("$.data.items[1].distributionRevenue").value(900));
    }
    @Test void monthlyDetailForMonthlyContractShowsRevenue() throws Exception {
        mvc.perform(request("/P/monthly-costs")).andExpect(jsonPath("$.data.items[1].revenue").value(1000))
            .andExpect(jsonPath("$.data.items[1].distributionRevenue").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.items[1].marginRate").value(20));
    }
    @Test void extraExpensesAreAddedOnceNotPerPerson() throws Exception {
        when(mapper.selectExpenses(any())).thenReturn(List.of(new Expense("P","202601",n("50"))));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].totalCost").value(850));
    }
    @Test void closedHistoricalContractIsReconstructedWhileStoredCostsStayFixed() throws Exception {
        var before = json.readTree(mvc.perform(request("")).andReturn().getResponse().getContentAsString()).path("data");
        var p = project("P","MO");
        when(mapper.selectProjects()).thenReturn(List.of(new Project(p.projectId(),p.projectName(),p.contractTypeCode(),p.contractTypeName(),
            p.statusCode(),p.startDate(),p.endDate(),p.originalAmount(),n("2000"),p.budget(),p.team(),p.expense(),p.plannedMm(),null)));
        when(mapper.selectHistory()).thenReturn(List.of(new History("P","AMT","계약금액","1250","2000",java.time.LocalDate.of(2026,2,1))));
        var after = json.readTree(mvc.perform(request("")).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(after).isEqualTo(before);
    }
    @Test void causesAreGreyWithoutAlertAndCarrySafeNavigationContexts() throws Exception {
        mvc.perform(request("/P/causes")).andExpect(jsonPath("$.data.primaryBarColor").value("회색"))
            .andExpect(jsonPath("$.data.referenceNotice").value("경보가 없는 프로젝트라 참고용"))
            .andExpect(jsonPath("$.data.items[0].navigationContext").isString());
    }
    @Test void workforceGroupsDeveloperAndJobForOnlySelectedProjectAndMonth() throws Exception {
        when(mapper.selectInputs(any())).thenReturn(List.of(input("P","202601","A","one","NEW"),
            input("P","202601","A","two","FREE"),input("Q","202601","A","other","NEW"),input("P","202602","A","future","NEW")));
        // 시안: 사람·직무·투입 구분(무상지원 등)별 한 줄, 그 달 배분 원가·매출을 붙인다
        mvc.perform(request("/P/workforce")).andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[?(@.inputTypeCode=='NEW')].monthlyMm").value(0.5))
            .andExpect(jsonPath("$.data.items[?(@.inputTypeCode=='NEW')].inputTypeName").value("신규"))
            .andExpect(jsonPath("$.data.items[?(@.inputTypeCode=='NEW')].allocatedCost").value(0))
            .andExpect(jsonPath("$.data.items[?(@.inputTypeCode=='FREE')].inputTypeName").value("무상지원"))
            .andExpect(jsonPath("$.data.items[0].developerName").value("개발자 A"))
            .andExpect(jsonPath("$.data.items[0].salary").doesNotExist());
        when(mapper.selectInputs(any())).thenReturn(List.of(input("P","202601","A","P-paid","NEW")));
        mvc.perform(request("/P/workforce")).andExpect(jsonPath("$.data.items[0].allocatedCost").value(770))
            .andExpect(jsonPath("$.data.items[0].revenue").value(1000));
    }
    @ParameterizedTest @ValueSource(strings={"MO","DP"})
    void monthlySimulationIgnoresMonthsAndIsVirtual(String type) throws Exception {
        when(mapper.selectProjects()).thenReturn(List.of(project("P",type)));
        var first = json.readTree(mvc.perform(request("/P/simulation")).andReturn().getResponse().getContentAsString()).path("data");
        var later = json.readTree(mvc.perform(request("/P/simulation").param("months","12")).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(later).isEqualTo(first);
        assertThat(first.path("virtual").booleanValue()).isTrue();
        assertThat(first.path("marginRateAfter").decimalValue()).isEqualByComparingTo(n("25"));
    }
    @Test void turnkeySimulationAddsHeadcountCostOverMonthsToRemainingCost() throws Exception {
        when(mapper.selectProjects()).thenReturn(List.of(project("P","TK")));
        // 전: (1250 − 800) ÷ 1250 = 36%, 후: (1250 − 800 − 1 × 100 × 3) ÷ 1250 = 12%
        mvc.perform(request("/P/simulation").param("months","3")).andExpect(jsonPath("$.data.marginRateBefore").value(36))
            .andExpect(jsonPath("$.data.marginRateAfter").value(12))
            .andExpect(jsonPath("$.data.addedCost").value(300))
            .andExpect(jsonPath("$.data.addedRevenue").value(0))
            .andExpect(jsonPath("$.data.targetMarginRate").value(20));
    }
    @Test void negativeHeadcountAcceptedButNonpositiveRevenueRejected() throws Exception {
        mvc.perform(request("/P/simulation").param("headcountChange","-1")).andExpect(status().isOk());
        mvc.perform(request("/P/simulation").param("headcountChange","-5")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("headcountChange"));
    }
    /** 범위(2026-11-20 사용자 결정): 월단위는 12개월까지, 지금 투입 인원보다 많이 못 뺌. */
    @Test void simulationRejectsMonthsAndHeadcountOutsideSensibleRange() throws Exception {
        when(mapper.selectProjects()).thenReturn(List.of(project("P","MO")));
        mvc.perform(request("/P/simulation").param("months","13")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("months")).andExpect(jsonPath("$.errors[0].message").value("12개월 이하로 입력해 주세요."));
        mvc.perform(request("/P/simulation").param("headcountChange","-999")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("headcountChange"));
    }
    @ParameterizedTest @ValueSource(strings={"monthlyCostPerPerson","monthlyPricePerPerson","months"})
    void simulationValidatesItsOwnFields(String field) throws Exception {
        mvc.perform(request("/P/simulation").param(field,"-1")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value(field));
    }
    @Test void internalCommercialCellsAreNullAndSimulationIsNeutral() throws Exception {
        when(mapper.selectProjects()).thenReturn(List.of(project("P","IN")));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].contractAmount").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.items[0].totalCost").value(800))
            .andExpect(jsonPath("$.data.items[0].cumulativeRevenue").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.items[0].researchCost").value(800))
            .andExpect(jsonPath("$.data.items[0].alertCount").value(0));
        mvc.perform(request("/P/simulation")).andExpect(jsonPath("$.data.marginRateAfter").value(0))
            .andExpect(jsonPath("$.data.virtual").value(true));
        mvc.perform(request("/P/causes")).andExpect(jsonPath("$.data.items").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"monthly-costs","workforce","causes","simulation"})
    void unknownProjectIsNotFound(String operation) throws Exception {
        mvc.perform(request("/missing/" + operation)).andExpect(status().isNotFound());
    }
    @Test void exportContainsRealXlsxFilteredRowsAndWritesOneDownloadOnlyAfterGeneration() throws Exception {
        when(mapper.selectProjects()).thenReturn(List.of(project("P","MO"),project("Q","MO")));
        var response = mvc.perform(request("/excel").param("projectId","P")).andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count","1")).andReturn().getResponse();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = book.getSheetAt(0);
            var text = new StringBuilder();
            sheet.forEach(row -> row.forEach(cell -> text.append(cell.toString()).append('\n')));
            assertThat(text).contains("프로젝트 P").doesNotContain("프로젝트 Q");
            // 엑셀 제목 줄은 화면 열 이름(시안 19열)과 같다
            assertThat(text).contains("원 계약금액", "예상 최종·월간 마진율", "무상지원 MM·원가", "이익")
                .doesNotContain("처음 계약금액", "연구개발비 누계");
            assertThat(text).contains("20.0% 월간").doesNotContain("0000000");
        }
        assertThat(response.getHeader("Content-Disposition")).contains(
            java.net.URLEncoder.encode("1월", java.nio.charset.StandardCharsets.UTF_8));
        verify(mapper,times(1)).insertAccess(eq("admin"),contains("프로젝트 원가 분석"));
    }
    @Test void exportFailureToRecordAccessFailsRatherThanReturningAFile() throws Exception {
        when(mapper.insertAccess(anyString(),anyString())).thenReturn(0);
        mvc.perform(request("/excel")).andExpect(status().is5xxServerError());
    }

    @Test void allocationReferencePresenceCannotChangeFuturePayrollEstimates() throws Exception {
        when(mapper.selectProjects()).thenReturn(List.of(project("P","TK")));
        when(mapper.selectInputs(any())).thenReturn(List.of(input("P","202602","A","P-paid","NEW")));
        when(mapper.selectSalaries()).thenReturn(List.of(new Salary("A","202501",null,n("100"))));
        var without=json.readTree(mvc.perform(request("")).andReturn().getResponse().getContentAsString()).at("/data/items/0/expectedFinalMarginRate");
        when(mapper.selectDistributions(any())).thenReturn(List.of(new Expense("P","202601",n("90000"))));
        var with=json.readTree(mvc.perform(request("")).andReturn().getResponse().getContentAsString()).at("/data/items/0/expectedFinalMarginRate");
        assertThat(with).isEqualTo(without);
        assertThat(with.isNumber()).isTrue();
    }
}
