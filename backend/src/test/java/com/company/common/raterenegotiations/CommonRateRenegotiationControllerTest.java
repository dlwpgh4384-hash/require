package com.company.common.raterenegotiations;

import com.company.common.calculation.mapper.*;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.RateRenegotiationDefinitions;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitMapper;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.raterenegotiations.controller.CommonRateRenegotiationController;
import com.company.common.raterenegotiations.mapper.RateRenegotiationMapper;
import com.company.common.raterenegotiations.mapper.RateRenegotiationRows.AssignmentInfo;
import com.company.common.raterenegotiations.service.*;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.company.common.raterenegotiations.RateRenegotiationCalculationTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonRateRenegotiationController.class)
@ContextConfiguration(classes = {CommonRateRenegotiationController.class, CommonRateRenegotiationService.class,
    RateRenegotiationSource.class, RateRenegotiationExcelService.class, RateRenegotiationDefinitions.class,
    CalculationRuleService.class, UserPreferencePeriodBasis.class, CodeExceptionHandler.class,
    ApiExceptionHandler.class, ExcelExportService.class, CurrentBusinessActor.class})
class CommonRateRenegotiationControllerTest {
    @Autowired MockMvc mvc;
    @MockBean RateRenegotiationMapper mapper;
    @MockBean PersonalCostProfitMapper sources;
    @MockBean CalculationRuleMapper definitions;
    @MockBean UserPreferencePeriodSourceMapper periods;
    @MockBean PlatformTransactionManager transactions;
    @MockBean BusinessCalendar calendar;
    static final String PATH = "/api/common/rate-renegotiations";

    @BeforeEach void setup() throws Exception {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(calendar.today()).thenReturn(TODAY);
        when(calendar.now()).thenReturn(Instant.parse("2026-02-01T00:00:00Z"));
        when(calendar.firstOpenMonth(any(), any())).thenCallRealMethod();
        when(periods.selectCashBaseYm()).thenReturn(n("202602"));
        when(periods.selectClosedMonths()).thenReturn(List.of());
        var standard = new CalculationStandardRow();
        standard.standardCode = "TGT_MRGN_RT"; standard.effectiveMonth = "202602"; standard.value = n("20");
        when(definitions.selectEffectiveStandard(eq("TGT_MRGN_RT"), anyString())).thenReturn(standard);
        var withholding = new CalculationStandardRow();
        withholding.standardCode = "WTHLD_RT"; withholding.effectiveMonth = "202602"; withholding.value = n("3.3");
        when(definitions.selectEffectiveStandard(eq("WTHLD_RT"), anyString())).thenReturn(withholding);
        String seed = new ClassPathResource("db/migration/V8__calculation_rule_seed.sql")
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for (String id : List.of("00", "57", "85", "86", "90")) {
            var match = Pattern.compile("\\('식-" + id + "', (?:NULL|'[^']*'), '[^']*', '((?:[^']++|'')*+)', '((?:[^']++|'')*+)',").matcher(seed);
            assertThat(match.find()).isTrue();
            var row = new CalculationRuleRow();
            row.calcId = "식-" + id; row.formula = match.group(1).replace("''", "'"); row.details = match.group(2).replace("''", "'");
            when(definitions.selectDefinition(row.calcId)).thenReturn(row);
        }
        when(sources.selectCodes()).thenReturn(List.of(new Code("ALWNC", "ALLOW_TEAM", "팀장", n("0")),
            new Code("ALWNC", "ALLOW_GROUP", "그룹장", n("0")), new Code("ALWNC", "SEV_RATE", "퇴직", n("8.33"))));
        when(sources.selectPeople(any())).thenReturn(List.of(new Person("D", "김개발", null, "", null, "", "CT", "계약직",
            LocalDate.of(2026, 1, 1), null, "BE", "백엔드", "MD", "중급", "PART", "DRCT", "M-D", "파트")));
        when(sources.selectPayroll(any())).thenReturn(List.of(new Pay("202602", "D", "CT", n("900000"), n("0"),
            n("0"), n("0"), n("0"), n("0"), n("0"), n("0"), n("0"))));
        when(sources.selectAssignments(any())).thenReturn(List.of(new Assignment("202602", "D", "P", "A", "프로젝트", "백엔드",
            "MO", "NEW", n("1"), n("1000000"), n("0"), n("0"), n("0"), n("0"), null)));
        when(sources.selectLeaves(any())).thenReturn(List.of());
        when(sources.selectActorRoles("admin")).thenReturn(List.of());
        when(mapper.selectAssignmentInfo("202602", false)).thenReturn(List.of(info("A", 30)));
        when(mapper.insertAccess(anyString(), anyString())).thenReturn(1);
    }
    @Test void publicAdminReadsEligibleRowsFourSummaryValuesAndDbHelpWithoutAudit() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.baseYearMonth").value("2026-02"))
            .andExpect(jsonPath("$.data.appliedIncreaseRate").value(5))
            .andExpect(jsonPath("$.data.items[0].monthlyCost").value(900000))
            .andExpect(jsonPath("$.data.items[0].targetUnitPrice").value(1130000))
            .andExpect(jsonPath("$.data.summary.targetCount").value(1))
            .andExpect(jsonPath("$.data.summary.averageProfitRate").value(10))
            .andExpect(jsonPath("$.data.summary.targetProfitRate").value(20))
            .andExpect(jsonPath("$.data.summary.assumedAnnualProfitIncrease").value(600000))
            .andExpect(jsonPath("$.data.summary.targetAnnualProfitIncrease").value(1560000))
            .andExpect(jsonPath("$.data.formulaHelp.targetUnitPrice").isString());
        verify(sources).selectActorRoles("admin");
        verify(mapper, never()).insertAccess(anyString(), anyString());
    }
    @Test void helpUsesRenegotiationDefinitionsRatherThanQuoteApprovalRules() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.formulaHelp.renewalDate").value(org.hamcrest.Matchers.containsString("183일 이하")))
            .andExpect(jsonPath("$.data.formulaHelp.monthlyCost").value(org.hamcrest.Matchers.containsString("기준월 한 달")))
            .andExpect(jsonPath("$.data.formulaHelp.profitRate").value(org.hamcrest.Matchers.containsString("이익률 =")))
            .andExpect(jsonPath("$.data.formulaHelp.targetUnitPrice").value(org.hamcrest.Matchers.containsString("만 원 단위")))
            .andExpect(jsonPath("$.data.formulaHelp.requiredIncreaseRate").value(org.hamcrest.Matchers.containsString("필요 인상률 =")))
            .andExpect(jsonPath("$.data.formulaHelp.requiredIncreaseRate").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("승인 대기"))))
            .andExpect(jsonPath("$.data.formulaHelp.annualProfitIncrease").value(org.hamcrest.Matchers.containsString("계약 MM")));
    }
    @ParameterizedTest @CsvSource({"-1,0,0", "51,50,6000000"})
    void clampsAndUsesTheSameRowSummaryAndTotal(String input, int applied, int amount) throws Exception {
        mvc.perform(get(PATH).param("increaseRate", input)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appliedIncreaseRate").value(applied))
            .andExpect(jsonPath("$.data.items[0].annualProfitIncrease").value(amount))
            .andExpect(jsonPath("$.data.summary.assumedAnnualProfitIncrease").value(amount))
            .andExpect(jsonPath("$.data.totalAnnualProfitIncrease").value(amount));
        verify(mapper, never()).insertAccess(anyString(), anyString());
    }
    @ParameterizedTest @CsvSource({"renewalMonths,5", "profitRateThreshold,12", "increaseRate,abc", "renewalMonths,abc"})
    void invalidQueryIsFieldLocalAndReadsNoBusinessSources(String field, String value) throws Exception {
        mvc.perform(get(PATH).param(field, value)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.errors[0].field").value(field));
        verifyNoInteractions(mapper, sources);
    }
    @ParameterizedTest @ValueSource(strings = {"FREE", "TK", "IN", "CNCL", "DONE", "ENDED", "ZERO", "EQUAL"})
    void excludesEachIneligibleRule(String reason) throws Exception {
        var a = info("A", reason.equals("ENDED") ? -1 : 30);
        when(mapper.selectAssignmentInfo(anyString(), anyBoolean())).thenReturn(List.of(new AssignmentInfo(
            a.assignmentId(), a.developerId(), a.developerName(), a.employeeTypeCode(), a.employeeTypeName(),
            a.projectId(), a.projectName(), a.renewalDate(), a.contractMm(), reason.equals("ZERO") ? n("0") : a.currentUnitPrice(),
            reason.equals("CNCL") ? "CNCL" : "ACT", reason.equals("FREE") ? "FREE" : "NEW",
            List.of("TK", "IN").contains(reason) ? reason : "MO", reason.equals("DONE") ? "DONE" : "ING", "M-1")));
        if (reason.equals("EQUAL")) {
            when(sources.selectPayroll(any())).thenReturn(List.of(new Pay("202602", "D", "CT", n("850000"), n("0"),
                n("0"), n("0"), n("0"), n("0"), n("0"), n("0"), n("0"))));
        }
        mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.summary.averageProfitRate").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.summary.targetProfitRate").value(20))
            .andExpect(jsonPath("$.data.totalAnnualProfitIncrease").value(0));
    }
    @Test void unavailablePeriodIsAnErrorRatherThanAnEmptyResult() throws Exception {
        when(periods.selectCashBaseYm()).thenReturn(null);
        mvc.perform(get(PATH)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("periodBasis"));
        verifyNoInteractions(sources, mapper);
    }
    @Test void workbookPreservesCallerOrderNumericCellsAndAuditsAfterGeneration() throws Exception {
        var bytes = mvc.perform(get(PATH + "/excel").param("columns", "monthlyCost", "developerName", "monthlyMm"))
            .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1"))
            .andExpect(content().contentTypeCompatibleWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("월 원가(기준월)");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("이름");
            assertThat(sheet.getRow(3).getCell(0).getNumericCellValue()).isEqualTo(900000d);
            assertThat(sheet.getRow(3).getCell(2).getNumericCellValue()).isEqualTo(1d);
        }
        verify(mapper).insertAccess(eq("admin"), contains("단가 재협상 대상"));
    }
    @Test void hiddenMonthlyCostIsNotExported() throws Exception {
        var bytes = mvc.perform(get(PATH + "/excel").param("columns", "developerName", "profitRate"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(book.getSheetAt(0).getRow(2).getLastCellNum()).isEqualTo((short) 2);
            assertThat(book.getSheetAt(0).getRow(2).getCell(1).getStringCellValue()).isEqualTo("이익률");
        }
    }
    @Test void publicAdminExportsAllApprovedColumnsIncludingMonthlyCost() throws Exception {
        var columns = RateRenegotiationColumnPolicy.KEYS.toArray(String[]::new);
        var bytes = mvc.perform(get(PATH + "/excel").param("columns", columns))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(2).getLastCellNum()).isEqualTo((short) 11);
            assertThat(sheet.getRow(2).getCell(6).getStringCellValue()).isEqualTo("월 원가(기준월)");
            assertThat(sheet.getRow(3).getCell(6).getNumericCellValue()).isEqualTo(900000d);
        }
        verify(mapper).insertAccess(eq("admin"), anyString());
    }
    @Test void formulaLikeDeveloperNameStaysTextInTheWorkbook() throws Exception {
        var a = info("A", 30);
        when(mapper.selectAssignmentInfo(anyString(), anyBoolean())).thenReturn(List.of(new AssignmentInfo(
            a.assignmentId(), a.developerId(), "=1+1", a.employeeTypeCode(), a.employeeTypeName(), a.projectId(),
            a.projectName(), a.renewalDate(), a.contractMm(), a.currentUnitPrice(), a.assignmentStatus(),
            a.inputTypeCode(), a.contractTypeCode(), a.projectStatus(), a.managementNumber())));
        var bytes = mvc.perform(get(PATH + "/excel").param("columns", "developerName"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var cell = book.getSheetAt(0).getRow(3).getCell(0);
            assertThat(cell.getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(cell.getStringCellValue()).isEqualTo("=1+1");
        }
    }
    @ParameterizedTest @ValueSource(strings = {"unknown", "", "developerName,developerName"})
    void invalidExportColumnsProduceJsonAndNoDownloadLog(String columns) throws Exception {
        mvc.perform(get(PATH + "/excel").param("columns", columns)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("columns"));
        verifyNoInteractions(mapper, sources);
    }
    @Test void missingExportColumnsProducesFieldError() throws Exception {
        mvc.perform(get(PATH + "/excel")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("columns"));
        verifyNoInteractions(mapper, sources);
    }
}
