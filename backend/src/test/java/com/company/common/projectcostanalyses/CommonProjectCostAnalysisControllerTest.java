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
    @Test void missingFutureSalaryLeavesExpectedMarginUnavailableWithReason() throws Exception {
        when(mapper.selectProjects()).thenReturn(List.of(project("P","TK")));
        when(mapper.selectInputs(any())).thenReturn(List.of(input("P","202602","A","P-paid","NEW")));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].expectedFinalMarginRate").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.items[0].missingMonthlyCost").value(true))
            .andExpect(jsonPath("$.data.items[0].missingMonthlyCostReason").isNotEmpty());
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
        mvc.perform(request("/P/causes")).andExpect(jsonPath("$.data.items[?(@.causeName == '투입 초과')]").isEmpty());
    }
    @Test void monthlyDetailUsesStoredCostAndTurnkeyReferenceRevenue() throws Exception {
        when(mapper.selectProjects()).thenReturn(List.of(project("P","TK")));
        mvc.perform(request("/P/monthly-costs")).andExpect(jsonPath("$.data.items[0].cost").value(800))
            .andExpect(jsonPath("$.data.items[0].distributionRevenue").value(1000));
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
        mvc.perform(request("/P/workforce")).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].monthlyMm").value(1))
            .andExpect(jsonPath("$.data.items[0].developerName").value("개발자 A"))
            .andExpect(jsonPath("$.data.items[0].salary").doesNotExist());
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
    @Test void negativeHeadcountAcceptedButNonpositiveRevenueRejected() throws Exception {
        mvc.perform(request("/P/simulation").param("headcountChange","-1")).andExpect(status().isOk());
        mvc.perform(request("/P/simulation").param("headcountChange","-5")).andExpect(status().isBadRequest())
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
            .andExpect(jsonPath("$.data.items[0].totalCost").value(org.hamcrest.Matchers.nullValue()))
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
        }
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
    @Test void independentRolePolicyPermitsUnionOfFinanceExecutiveAdminButNotPmOrSalesLeader() {
        for (String role : List.of("FIN","EXEC","ADMIN"))
            assertThat(ProjectCostAnalysisPolicy.canView(List.of(new Role(role,role,"ALL","Y","Y",true)))).isTrue();
        for (String role : List.of("PM","SALES_LDR"))
            assertThat(ProjectCostAnalysisPolicy.canView(List.of(new Role(role,role,"ALL","Y","Y",true)))).isFalse();
    }
}
