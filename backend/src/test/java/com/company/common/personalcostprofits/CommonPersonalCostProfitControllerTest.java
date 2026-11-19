package com.company.common.personalcostprofits;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.mapper.CalculationStandardRow;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.PersonalCostProfitDefinitions;
import com.company.common.personalcostprofits.controller.CommonPersonalCostProfitController;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitMapper;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import com.company.common.personalcostprofits.service.*;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
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
import static com.company.common.personalcostprofits.PersonalCostProfitTestSources.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonPersonalCostProfitController.class)
@ContextConfiguration(classes = {CommonPersonalCostProfitController.class, CommonPersonalCostProfitService.class,
    PersonalCostProfitExcelService.class, PersonalCostProfitDefinitions.class, CalculationRuleService.class,
    UserPreferencePeriodBasis.class, CodeExceptionHandler.class, ApiExceptionHandler.class,
    ExcelExportService.class, BusinessCalendar.class, CurrentBusinessActor.class})
class CommonPersonalCostProfitControllerTest {
    @Autowired MockMvc mvc;
    @MockBean PersonalCostProfitMapper mapper;
    @MockBean CalculationRuleMapper definitions;
    @MockBean UserPreferencePeriodSourceMapper periods;
    @MockBean PlatformTransactionManager transactions;
    static final String PATH = "/api/common/personal-cost-profits";

    @BeforeEach void setup() throws Exception {
        PersonalCostProfitTestSources.definitions(definitions);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(periods.selectCashBaseYm()).thenReturn(n("202601"));
        when(periods.selectClosedMonths()).thenReturn(List.of("202601"));
        var withholding = new CalculationStandardRow();
        withholding.standardCode = "WTHLD_RT"; withholding.effectiveMonth = "202601"; withholding.value = n("3.3");
        when(definitions.selectEffectiveStandard(eq("WTHLD_RT"), anyString())).thenReturn(withholding);
        when(mapper.selectCodes()).thenReturn(codes());
        when(mapper.selectActorRoles("admin")).thenReturn(List.of(role("ADMIN", "Y")));
        when(mapper.selectPeople(any())).thenReturn(List.of(person("A")));
        when(mapper.selectPayroll(any())).thenReturn(List.of(pay("A", "202601", "1000"), pay("A", "202602", "1000")));
        when(mapper.selectCosts(any())).thenReturn(List.of(cost("PAID", "0.5", "900", "400"), cost("FREE", "0.5", "0", "600")));
        when(mapper.selectAssignments(any())).thenReturn(List.of(assignment("LIVE", "202602", "0.5")));
        when(mapper.selectLeaves(any())).thenReturn(List.of());
        when(mapper.insertAccess(anyString(), anyString())).thenReturn(1);
    }
    MockHttpServletRequestBuilder request(String suffix) {
        return get(PATH + suffix).param("year", "2026").param("baseMonth", "2").param("displayMode", "연누계");
    }
    @Test void aggregatesOneParentAndPreservesSameProjectPaidFreeAndIdleChildren() throws Exception {
        mvc.perform(request("")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].allocations.length()").value(4))
            .andExpect(jsonPath("$.data.items[0].allocations[0].assignmentId").value("PAID"))
            .andExpect(jsonPath("$.data.items[0].allocations[1].assignmentId").value("FREE"))
            .andExpect(jsonPath("$.data.items[0].teamExpenses").value(25))
            .andExpect(jsonPath("$.data.items[0].projectExpenses").value(50))
            .andExpect(jsonPath("$.data.items[0].personalCost").value(2100))
            .andExpect(jsonPath("$.data.totals.totalCost").value(2175))
            .andExpect(jsonPath("$.data.summary.totalPeople").value(1))
            .andExpect(jsonPath("$.data.contributions[0].people").value(1));
        verify(mapper, never()).insertAccess(anyString(), anyString());
    }
    @Test void developmentActorIsPublicAndSensitiveProjectionComesFromAssignedRoleFlags() throws Exception {
        mvc.perform(request("")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].monthlySalary").value(2000))
            .andExpect(jsonPath("$.data.items[0].personalCost").isNumber());
        verify(mapper).selectActorRoles("admin");
    }
    @ParameterizedTest @ValueSource(strings = {"0", "13"})
    void rejectsInvalidMonthBeforeSourcesAreRead(String month) throws Exception {
        mvc.perform(get(PATH).param("year", "2026").param("baseMonth", month).param("displayMode", "연누계"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.errors[0].field").value("baseMonth"));
        verify(mapper, never()).selectCosts(any()); verify(mapper, never()).insertAccess(anyString(), anyString());
    }
    @Test void rejectsUnknownDisplayMode() throws Exception {
        mvc.perform(get(PATH).param("year", "2026").param("baseMonth", "2").param("displayMode", "unknown"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("displayMode"));
    }
    @ParameterizedTest @ValueSource(strings = {"year", "baseMonth", "includeTurnkey"})
    void malformedQueryTypeIsAFieldLocal400(String field) throws Exception {
        var req = get(PATH).param("displayMode", "연누계");
        req.param("year", field.equals("year") ? "invalid" : "2026");
        req.param("baseMonth", field.equals("baseMonth") ? "invalid" : "2");
        if (field.equals("includeTurnkey")) req.param(field, "invalid");
        mvc.perform(req).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
    }
    @ParameterizedTest @ValueSource(strings = {"year", "baseMonth", "displayMode"})
    void requiresEachPeriodField(String omitted) throws Exception {
        var req = get(PATH);
        if (!omitted.equals("year")) req.param("year", "2026");
        if (!omitted.equals("baseMonth")) req.param("baseMonth", "2");
        if (!omitted.equals("displayMode")) req.param("displayMode", "연누계");
        mvc.perform(req).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(omitted));
    }
    @Test void rejectsUnsupportedYear() throws Exception {
        mvc.perform(get(PATH).param("year", "2025").param("baseMonth", "12").param("displayMode", "연누계"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("year"));
    }
    @Test void priorYearRequiresDecember() throws Exception {
        when(periods.selectClosedMonths()).thenReturn(List.of("202701"));
        mvc.perform(get(PATH).param("year", "2026").param("baseMonth", "2").param("displayMode", "연누계"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("baseMonth"));
    }
    @Test void rejectsUnknownOptionalCode() throws Exception {
        mvc.perform(request("").param("jobCode", "UNKNOWN")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("jobCode"));
    }
    @Test void annualMmDoesNotTriggerMonthlyOverOne() throws Exception {
        mvc.perform(request("")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].mm").value(1.5))
            .andExpect(jsonPath("$.data.items[0].mmOverOne").value(false));
    }
    @Test void overOneTriggersOnlyForAMonthAndZeroPriceHasNullRate() throws Exception {
        when(mapper.selectCosts(any())).thenReturn(List.of(cost("ONE", "1.01", "0", "1000")));
        when(mapper.selectAssignments(any())).thenReturn(List.of());
        mvc.perform(request("")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].mmOverOne").value(true))
            .andExpect(jsonPath("$.data.items[0].profitRate").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.items[0].operatingProfit").value(-2130));
    }
    @Test void freshLivePayrollChangesButClosedSnapshotRemainsUnchanged() throws Exception {
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].totalCost").value(2175));
        when(mapper.selectPayroll(any())).thenReturn(List.of(pay("A", "202601", "1000"), pay("A", "202602", "2000")));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].totalCost").value(3175))
            .andExpect(jsonPath("$.data.items[0].allocations[0].allocatedCost").value(430));
    }
    @Test void leavePersonHiddenButUploadedIdleCostRemainsInTotalAndNotVisibleAverages() throws Exception {
        when(mapper.selectPeople(any())).thenReturn(List.of(person("A"), person("LEAVE")));
        when(mapper.selectPayroll(any())).thenReturn(List.of(pay("A", "202601", "1000"), pay("A", "202602", "1000"), pay("LEAVE", "202602", "200")));
        when(mapper.selectLeaves(any())).thenReturn(List.of(new Leave("LEAVE", LocalDate.of(2026,2,1), null)));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.summary.totalPeople").value(2))
            .andExpect(jsonPath("$.data.totals.totalCost").value(2475))
            .andExpect(jsonPath("$.data.summary.averageCost").value(2175));
    }
    @Test void unpaidLeaveDoesNotCreateMoney() throws Exception {
        when(mapper.selectPeople(any())).thenReturn(List.of(person("UNPAID")));
        when(mapper.selectPayroll(any())).thenReturn(List.of());
        when(mapper.selectLeaves(any())).thenReturn(List.of(new Leave("UNPAID", LocalDate.of(2026,1,1), null)));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.summary.totalPeople").value(1)).andExpect(jsonPath("$.data.totals.totalCost").value(0));
    }
    @Test void displayModeRoundTripOnlyReadsSourcesAndNeverCreatesDownloadLog() throws Exception {
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].unitPrice").value(1700));
        mvc.perform(get(PATH).param("year", "2026").param("baseMonth", "2").param("displayMode", "월평균"))
            .andExpect(jsonPath("$.data.items[0].unitPrice").value(850));
        mvc.perform(request("")).andExpect(jsonPath("$.data.items[0].unitPrice").value(1700));
        verify(mapper, never()).insertAccess(anyString(), anyString());
    }
    @Test void policyRemovesSensitiveKeysFromBothRowsAndTotalsWithoutRejectingHttp() throws Exception {
        when(mapper.selectActorRoles("admin")).thenReturn(List.of(role("ADMIN", "N")));
        mvc.perform(request("")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].monthlySalary").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].personalCost").doesNotExist())
            .andExpect(jsonPath("$.data.totals.monthlySalary").doesNotExist())
            .andExpect(jsonPath("$.data.totals.personalCost").doesNotExist());
    }
    @Test void emptyResponseHasNumericSummaryAndNoInventedContributionGroups() throws Exception {
        when(mapper.selectPeople(any())).thenReturn(List.of());
        mvc.perform(request("")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.contributions").isEmpty()).andExpect(jsonPath("$.data.summary.operatingRate").value(0));
    }
    @Test void excelIsRealXlsxInCanonicalVisibleColumnOrderWithOneAuditAfterGeneration() throws Exception {
        var bytes = mvc.perform(request("/excel").param("columns", "totalCost,developerName,monthlySalary"))
            .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1"))
            .andExpect(content().contentTypeCompatibleWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = book.getSheetAt(0);
            assertThat(book.getNumberOfSheets()).isEqualTo(1);
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("이름");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("월 급여");
            assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("원가합계");
            assertThat(sheet.getRow(3).getCell(2).getStringCellValue()).isEqualTo("2175");
        }
        verify(mapper).insertAccess(eq("admin"), contains("개인 원가·수익표"));
    }
    @Test void excelUnknownColumnIsJsonValidationAndDoesNotAudit() throws Exception {
        mvc.perform(request("/excel").param("columns", "bad-key")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("columns"));
        verify(mapper, never()).insertAccess(anyString(), anyString());
    }
    @Test void excelExcludesSensitiveAndDefaultReferenceColumnsWithoutAddingAuth() throws Exception {
        when(mapper.selectActorRoles("admin")).thenReturn(List.of(role("PM", "N")));
        byte[] bytes = mvc.perform(request("/excel")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var names = new java.util.ArrayList<String>();
            book.getSheetAt(0).getRow(2).forEach(cell -> names.add(cell.getStringCellValue()));
            assertThat(names).doesNotContain("월 급여", "개인별 원가", "4대보험(본인)", "3.3% 원천세", "근로소득세", "실지급액");
        }
    }
    @Test void workbookTurnkeyExclusionMatchesListAndRetainsCosts() throws Exception {
        when(mapper.selectCosts(any())).thenReturn(List.of(new Cost("202601", "A", "P", "TK", "턴키", "백엔드", "TK", n("1"), n("900"), n("1000"), n("10"), n("20"))));
        when(mapper.selectAssignments(any())).thenReturn(List.of());
        mvc.perform(request("").param("includeTurnkey", "false")).andExpect(jsonPath("$.data.items[0].unitPrice").value(0));
        var bytes = mvc.perform(request("/excel").param("includeTurnkey", "false").param("columns", "developerName,unitPrice,totalCost"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(book.getSheetAt(0).getSheetName()).isEqualTo("턴키단가미포함");
            assertThat(book.getSheetAt(0).getRow(3).getCell(1).getStringCellValue()).isEqualTo("0");
            assertThat(book.getSheetAt(0).getRow(3).getCell(2).getStringCellValue()).isEqualTo("2130");
        }
    }
}
