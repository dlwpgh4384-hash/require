package com.company.common.netprofitstatements;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.NetProfitStatementDefinitions;
import com.company.common.netprofitstatements.controller.CommonNetProfitStatementController;
import com.company.common.netprofitstatements.dto.NetProfitStatementContracts.Filter;
import com.company.common.netprofitstatements.mapper.NetProfitStatementMapper;
import com.company.common.netprofitstatements.service.*;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Code;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.profitstructures.mapper.ProfitStructureRows.*;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonNetProfitStatementController.class)
@ContextConfiguration(classes = {CommonNetProfitStatementController.class, CommonNetProfitStatementService.class,
    ProfitStructureMonthlyProfitService.class, NetProfitStatementDefinitions.class, NetProfitStatementExcelService.class,
    UserPreferencePeriodBasis.class, CodeExceptionHandler.class, ApiExceptionHandler.class,
    ExcelExportService.class, BusinessCalendar.class, CurrentBusinessActor.class})
class NetProfitStatementControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ProfitStructureMonthlyProfitService monthly;
    @Autowired CommonNetProfitStatementService reports;
    @SpyBean ExcelExportService writer;
    @MockBean ProfitStructureMapper source;
    @MockBean NetProfitStatementMapper audit;
    @MockBean CalculationRuleService calculation;
    @MockBean UserPreferencePeriodSourceMapper periods;
    @MockBean PlatformTransactionManager transactions;
    static final String PATH = "/api/common/net-profit-statements";
    static BigDecimal n(String value) { return new BigDecimal(value); }
    static Snapshot saved(String month, boolean imported, BigDecimal net) {
        return new Snapshot(month, n("1000000"), n("200000"), null, n("0"), n("400000"),
            n("600000"), n("100000"), n("-100000"), n("400000"), n("70000"), n("20000"),
            n("0"), net, imported ? "Y" : "N", "Y", null, n("50000"), n("30000"), n("20000"));
    }
    @BeforeEach void setup() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(periods.selectCashBaseYm()).thenReturn(n("202601"));
        when(periods.selectClosedMonths()).thenReturn(List.of("202601"));
        when(source.codes()).thenReturn(List.of(new Code("CTRT_TYPE", "TK", "턴키", null),
            new Code("CTRT_TYPE", "MO", "월단위", null), new Code("CTRT_TYPE", "DP", "파견", null),
            new Code("CTRT_TYPE", "IN", "내부", null)));
        when(source.snapshots(any())).thenAnswer(call -> ((Query) call.getArgument(0)).year() == 2026
            ? List.of(saved("202601", false, n("450000")))
            : List.of(saved("202501", true, n("450000")), saved("202502", true, null)));
        when(source.amounts(any())).thenAnswer(call -> ((Query) call.getArgument(0)).year() == 2026
            ? List.of(new Amount("202602", "ADJUSTMENT", n("-100000"))) : List.of());
        when(audit.insertAccess(anyString(), anyString())).thenReturn(1);
    }
    MockHttpServletRequestBuilder query(String suffix, String month) {
        var request = get(PATH + suffix).param("year", month.substring(0, 4)).param("baseMonth", month);
        return suffix.isEmpty() ? request : request.param("amountUnit", "원");
    }
    @Test void durableContractContainsBothOperations() throws Exception {
        assertThat(new ClassPathResource("contracts/openapi.yaml")
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
            .contains("operationId: getCommonNetProfitStatements", "operationId: getCommonNetProfitStatementsExcel");
    }
    @Test void publicReportUsesSharedYearForTwoCardsAndNetProfitRow() throws Exception {
        var live = monthly.year(2026, 2, "202601").get("202602").values().netProfit();
        mvc.perform(get("/api/common/net-profit-statements").param("year", "2026").param("baseMonth", "202602")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.cumulativeNetProfit").value(n("450000").add(live).intValueExact()))
            .andExpect(jsonPath("$.data.summary.baseMonthNetProfit").value(live.intValueExact()))
            .andExpect(jsonPath("$.data.numericTable.rows[16].label").value("당기순이익"))
            .andExpect(jsonPath("$.data.numericTable.rows[16].style").value("bold"))
            .andExpect(jsonPath("$.data.numericTable.rows[16].values[1].value").value(live.intValueExact()))
            .andExpect(jsonPath("$.data.numericTable.rows.length()").value(18));
        verifyNoInteractions(audit);
        verify(calculation, atLeastOnce()).definition("식-32");
        verify(calculation, atLeastOnce()).definition("식-34");
    }
    @ParameterizedTest @ValueSource(strings = {"202613", "202512", "202600", "20261"})
    void invalidBaseMonthHasFieldErrorAndNoSourceAccess(String month) throws Exception {
        mvc.perform(get(PATH).param("year", "2026").param("baseMonth", month))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("baseMonth"));
        verifyNoInteractions(source, audit);
    }
    @Test void futureMonthIsRejectedBeforeSourceReads() throws Exception {
        mvc.perform(query("", "202603")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("baseMonth"));
        verifyNoInteractions(source, audit);
    }
    @Test void missingPriorCellOnlySuppressesItsOwnComparison() throws Exception {
        mvc.perform(query("", "202602")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.numericTable.rows[0].values[13].value").value(2000000))
            .andExpect(jsonPath("$.data.numericTable.rows[16].values[13].value").isEmpty())
            .andExpect(jsonPath("$.data.numericTable.rows[16].values[14].value").isEmpty());
        verifyNoInteractions(audit);
    }
    @Test void firstYearHidesBothComparisonsAndContributions() throws Exception {
        mvc.perform(query("", "202501")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.numericTable.columns.length()").value(13))
            .andExpect(jsonPath("$.data.contributionVisible").value(false))
            .andExpect(jsonPath("$.data.contributions").isEmpty());
        mvc.perform(query("", "202601")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.numericTable.columns.length()").value(15))
            .andExpect(jsonPath("$.data.contributionVisible").value(true))
            .andExpect(jsonPath("$.data.contributions.length()").value(3));
    }
    @Test void publicReportContributionsAreProjectGrossProfitsNotNetProfit() throws Exception {
        when(source.projects(any())).thenReturn(List.of(
            new ProjectMonth("202601", "TK1", "TK", "턴키", null, null, null, null, n("100"), null, n("100"), null, null),
            new ProjectMonth("202601", "MO1", "MO", "월단위", null, null, null, null, n("100"), null, null, null, null),
            new ProjectMonth("202601", "DP1", "DP", "파견", null, null, null, null, n("10"), null, null, null, null)));
        when(source.costs(any())).thenReturn(List.of(
            new StoredCost("202601", "TK1", "ALLOC", n("100"), n("70"), n("0"), n("0"), n("0")),
            new StoredCost("202601", "MO1", "ALLOC", n("100"), n("30"), n("0"), n("0"), n("0")),
            new StoredCost("202601", "DP1", "ALLOC", n("10"), n("20"), n("0"), n("0"), n("0"))));
        mvc.perform(query("", "202601")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contributions[0].amount").value(30))
            .andExpect(jsonPath("$.data.contributions[0].sharePercent").value(30.0))
            .andExpect(jsonPath("$.data.contributions[1].amount").value(70))
            .andExpect(jsonPath("$.data.contributions[1].sharePercent").value(70.0))
            .andExpect(jsonPath("$.data.contributions[2].amount").value(-10))
            .andExpect(jsonPath("$.data.contributions[2].sharePercent").isEmpty());
        verifyNoInteractions(audit);
    }
    @Test void closedAndLiveReportReadsPreserveRawValuesAndDoNotAudit() throws Exception {
        var before = source.snapshots(new Query(2025, 2));
        mvc.perform(query("", "202601")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.provisional").value(false))
            .andExpect(jsonPath("$.data.provisionalNotice").isEmpty());
        mvc.perform(query("", "202602")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.provisional").value(true))
            .andExpect(jsonPath("$.data.provisionalNotice").value("2월은 아직 마감 전이라 요약 칸과 누계는 잠정 숫자입니다. 마감하면 확정됩니다."));
        assertThat(source.snapshots(new Query(2025, 2))).isEqualTo(before);
        verifyNoInteractions(audit);
        verify(source, never()).insertAccess(anyString(), anyString());
    }
    @Test void januaryComparesPreviousDecemberAndZeroDenominatorsStayNull() throws Exception {
        var result = reports.getCommonNetProfitStatements(new Filter(2026, "202601")).data();
        assertThat(result.summary().previousMonthChangePercent()).isNull();
        verify(source).snapshots(new Query(2025, 12));
        var live = reports.getCommonNetProfitStatements(new Filter(2026, "202602")).data();
        assertThat(live.numericTable().rows().get(17).values().get(1).value()).isNull();
    }
    @Test void publicWorkbookMatchesEveryProjectionCellAndHeading() throws Exception {
        var result = reports.getCommonNetProfitStatements(new Filter(2026, "202602")).data();
        var bytes = mvc.perform(get("/api/common/net-profit-statements/excel").param("year", "2026").param("baseMonth", "202602").param("amountUnit", "원")).andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
            .andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(result.numericTable().rows().size() + 2);
            for (int c = 0; c < result.numericTable().columns().size(); c++) {
                assertThat(sheet.getRow(2).getCell(c + 1).getStringCellValue())
                    .isEqualTo(result.numericTable().columns().get(c).heading());
            }
            for (int r = 0; r < result.numericTable().rows().size(); r++) {
                var line = result.numericTable().rows().get(r);
                assertThat(sheet.getRow(r + 3).getCell(0).getStringCellValue()).isEqualTo(line.label());
                for (int c = 0; c < line.values().size(); c++) {
                    var expected = line.values().get(c);
                    var cell = sheet.getRow(r + 3).getCell(c + 1);
                    if (expected.value() == null) assertThat(cell.getStringCellValue()).isEqualTo("–");
                    else {
                        assertThat(cell.getCellType()).isEqualTo(CellType.NUMERIC);
                        assertThat(cell.getNumericCellValue()).isEqualTo(expected.value().doubleValue());
                    }
                    if (expected.format().equals("percent")) {
                        assertThat(cell.getCellStyle().getDataFormatString()).isEqualTo("0.0\"%\"");
                    }
                }
            }
        }
        verify(audit).insertAccess(eq("admin"), contains("202602"));
    }
    @ParameterizedTest @CsvSource({"원,1000000", "천 원,1000", "백만 원,1"})
    void workbookUnitScalesOnlyMoney(String unit, double expected) throws Exception {
        var bytes = mvc.perform(get(PATH + "/excel").param("year", "2026")
            .param("baseMonth", "202601").param("amountUnit", unit))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(book.getSheetAt(0).getRow(3).getCell(1).getNumericCellValue()).isEqualTo(expected);
            assertThat(book.getSheetAt(0).getRow(20).getCell(1).getNumericCellValue()).isEqualTo(45.0);
        }
    }
    @Test void firstYearWorkbookHasNoPreviousYearColumns() throws Exception {
        var bytes = mvc.perform(query("/excel", "202501")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(book.getSheetAt(0).getRow(2).getPhysicalNumberOfCells()).isEqualTo(14);
            assertThat(book.getSheetAt(0).getRow(2).getCell(13).getStringCellValue()).isEqualTo("누계");
        }
    }
    @ParameterizedTest @ValueSource(strings = {"202613", "202600"})
    void invalidExportHasNoAudit(String month) throws Exception {
        mvc.perform(query("/excel", month)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("baseMonth"));
        verifyNoInteractions(source, audit);
    }
    @Test void invalidUnitHasNoFileOrAudit() throws Exception {
        mvc.perform(get(PATH + "/excel").param("year", "2026").param("baseMonth", "202602").param("amountUnit", "달러"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("amountUnit"));
        verifyNoInteractions(source, audit);
    }
    @Test void workbookGenerationFailureDoesNotAuditOrReturnFile() throws Exception {
        doThrow(new IllegalStateException("private generation detail"))
            .when(writer).write(anyString(), anyList(), anyList(), anyString(), any());
        mvc.perform(query("/excel", "202602")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private generation detail"))));
        verifyNoInteractions(audit);
    }
    @Test void auditFailureDoesNotReturnSuccessfulWorkbook() throws Exception {
        when(audit.insertAccess(anyString(), anyString())).thenReturn(0);
        mvc.perform(query("/excel", "202602")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false));
        verify(audit).insertAccess(eq("admin"), contains("202602"));
    }
}
