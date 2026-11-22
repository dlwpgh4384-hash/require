package com.company.common.operatingprofitstatements;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.operatingprofitstatements.controller.CommonOperatingProfitStatementController;
import com.company.common.operatingprofitstatements.mapper.OperatingProfitStatementMapper;
import com.company.common.operatingprofitstatements.service.*;
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

@WebMvcTest(CommonOperatingProfitStatementController.class)
@ContextConfiguration(classes = {CommonOperatingProfitStatementController.class, CommonOperatingProfitStatementService.class,
    ProfitStructureMonthlyProfitService.class, OperatingProfitStatementExcelService.class, UserPreferencePeriodBasis.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class, ExcelExportService.class, BusinessCalendar.class, CurrentBusinessActor.class})
class OperatingProfitStatementControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ProfitStructureMonthlyProfitService monthly;
    @MockBean ProfitStructureMapper source;
    @MockBean OperatingProfitStatementMapper audit;
    @MockBean CalculationRuleService calculation;
    @MockBean UserPreferencePeriodSourceMapper periods;
    @MockBean PlatformTransactionManager transactions;
    static final String PATH = "/api/common/operating-profit-statements";
    static BigDecimal n(String value) { return new BigDecimal(value); }
    static Snapshot saved(String month, boolean imported, BigDecimal gross) {
        return new Snapshot(month,n("1000000"),n("200000"),null,n("0"),n("400000"),gross,n("100000"),
            n("-100000"),n("400000"),null,null,null,null,imported ? "Y" : "N","Y",null,n("50000"),n("30000"),n("20000"));
    }
    @BeforeEach void setup() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(periods.selectCashBaseYm()).thenReturn(n("202601"));
        when(periods.selectClosedMonths()).thenReturn(List.of("202601"));
        when(source.snapshots(any())).thenAnswer(call -> {
            Query query = call.getArgument(0);
            return query.year() == 2026 ? List.of(saved("202601",false,n("600000")))
                : List.of(saved("202501",true,n("600000")),saved("202502",true,null));
        });
        when(source.amounts(any())).thenAnswer(call -> ((Query)call.getArgument(0)).year() == 2026
            ? List.of(new Amount("202602","ADJUSTMENT",n("-100000"))) : List.of());
        when(audit.insertAccess(anyString(),anyString())).thenReturn(1);
    }
    MockHttpServletRequestBuilder query(String suffix, String month) {
        var request = get(PATH + suffix).param("year",month.substring(0,4)).param("baseMonth",month);
        return suffix.isEmpty() ? request : request.param("amountUnit","원");
    }
    @Test void approvedOperationsAreAvailableInDurableContract() throws Exception {
        assertThat(new ClassPathResource("contracts/openapi.yaml").getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
            .contains("operationId: getCommonOperatingProfitStatements", "operationId: getCommonOperatingProfitStatementsExcel");
    }
    @Test void publicReportUsesSharedFixedAndLiveMonthValuesForAllFourCards() throws Exception {
        var live=monthly.year(2026,2,"202601").get("202602").values().operatingProfit();
        mvc.perform(query("","202602")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.cumulativeRevenue.amount").value(1000000))
            .andExpect(jsonPath("$.data.summary.cumulativeGrossProfit.amount").value(600000))
            .andExpect(jsonPath("$.data.summary.cumulativeOperatingProfit.amount").value(n("400000").add(live).intValueExact()))
            .andExpect(jsonPath("$.data.summary.baseMonthOperatingProfit.amount").value(live.intValueExact()))
            .andExpect(jsonPath("$.data.numericTable.columns.length()").value(15))
            .andExpect(jsonPath("$.data.numericTable.rows.length()").value(13))
            .andExpect(jsonPath("$.data.numericTable.rows[11].values[1].value").value(live.intValueExact()));
        verifyNoInteractions(audit);
        for (var id: CommonOperatingProfitStatementService.CALC_IDS) verify(calculation,atLeastOnce()).definition(id);
    }
    @ParameterizedTest @ValueSource(strings={"202613","202512","202600","20261"})
    void invalidBaseMonthFailsWithoutReadingOrWritingProfit(String month) throws Exception {
        mvc.perform(get(PATH).param("year","2026").param("baseMonth",month)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("baseMonth"));
        verifyNoInteractions(source,audit);
    }
    @Test void missingOnePriorCellSuppressesOnlyThatItemInFirstOperatingYear() throws Exception {
        mvc.perform(query("","202602")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.numericTable.rows[0].values[13].value").value(2000000))
            .andExpect(jsonPath("$.data.numericTable.rows[0].values[14].value").value(-50.0))
            .andExpect(jsonPath("$.data.numericTable.rows[5].values[13].value").isEmpty())
            .andExpect(jsonPath("$.data.numericTable.rows[5].values[14].value").isEmpty());
    }
    @Test void missingPriorMonthIsNotSyntheticZero() throws Exception {
        doReturn(List.of(saved("202502",true,n("600000")))).when(source)
            .snapshots(argThat(q -> q != null && q.year()==2025));
        mvc.perform(query("","202602")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.cumulativeRevenue.previousYearChangePercent").isEmpty());
    }
    @Test void provisionalNoticeAndWorkingHeadersUseBaseSnapshotNotCloseMarker() throws Exception {
        mvc.perform(query("","202602")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.provisional").value(true))
            .andExpect(jsonPath("$.data.provisionalNotice").value("2월은 아직 마감 전이라 요약 칸과 누계는 잠정 숫자입니다. 마감하면 확정됩니다."))
            .andExpect(jsonPath("$.data.numericTable.columns[0].working").value(false))
            .andExpect(jsonPath("$.data.numericTable.columns[1].working").value(true))
            .andExpect(jsonPath("$.data.numericTable.columns[12].provisional").value(true))
            .andExpect(jsonPath("$.data.numericTable.rows[0].values[2].value").isEmpty());
    }
    @Test void fixedBaseHasNoProvisionalMarkerAndNegativeAdjustmentIsUnchanged() throws Exception {
        mvc.perform(query("","202601")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.provisional").value(false)).andExpect(jsonPath("$.data.provisionalNotice").isEmpty())
            .andExpect(jsonPath("$.data.numericTable.rows[10].values[0].value").value(-100000));
        verifyNoInteractions(audit);
    }
    @Test void importedRawNullAndExplicitZeroSurviveRepeatedReadsWithoutWrites() throws Exception {
        var before=source.snapshots(new Query(2025,1));
        for(int i=0;i<2;i++) mvc.perform(query("","202501")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.provisional").value(false))
            .andExpect(jsonPath("$.data.numericTable.rows[3].values[0].value").isEmpty())
            .andExpect(jsonPath("$.data.numericTable.rows[4].values[0].value").value(0))
            // 첫 해(이관 해)는 전년이 없어 전년 누계·전년 대비 열이 없다(시안·SFR-108-05).
            .andExpect(jsonPath("$.data.numericTable.columns.length()").value(13))
            .andExpect(jsonPath("$.data.numericTable.columns[12].label").value("누계"))
            .andExpect(jsonPath("$.data.numericTable.rows[0].values.length()").value(13));
        assertThat(source.snapshots(new Query(2025,1))).isEqualTo(before);
        verifyNoInteractions(audit);
    }
    @Test void publicExcelSharesAllHeadersRowsFutureNullsAndMixedCellFormats() throws Exception {
        var bytes=mvc.perform(query("/excel","202602")).andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("attachment")))
            .andReturn().getResponse().getContentAsByteArray();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet=book.getSheetAt(0);
            assertThat(sheet.getRow(2).getPhysicalNumberOfCells()).isEqualTo(16);
            assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("2월 작업중");
            assertThat(sheet.getRow(2).getCell(13).getStringCellValue()).isEqualTo("누계 잠정");
            assertThat(sheet.getRow(3).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(3).getCell(1).getNumericCellValue()).isEqualTo(1000000);
            assertThat(sheet.getRow(3).getCell(3).getStringCellValue()).isEqualTo("–");
            assertThat(sheet.getRow(8).getCell(14).getStringCellValue()).isEqualTo("–");
            assertThat(sheet.getRow(3).getCell(14).getNumericCellValue()).isEqualTo(2000000);
            assertThat(sheet.getRow(3).getCell(15).getCellStyle().getDataFormatString()).contains("%");
            assertThat(sheet.getRow(15).getCell(15).getCellStyle().getDataFormatString()).contains("%p");
        }
        verify(audit).insertAccess(eq("admin"),contains("202602"));
        verify(source,never()).insertAccess(anyString(),anyString());
    }
    @ParameterizedTest @CsvSource({"원,1000000", "천 원,1000", "백만 원,1"})
    void selectedUnitScalesMoneyButNotPercentCells(String unit, double expected) throws Exception {
        var bytes=mvc.perform(get(PATH+"/excel").param("year","2026").param("baseMonth","202602").param("amountUnit",unit))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(book.getSheetAt(0).getRow(3).getCell(1).getNumericCellValue()).isEqualTo(expected);
            assertThat(book.getSheetAt(0).getRow(15).getCell(1).getNumericCellValue()).isEqualTo(40.0);
        }
    }
    @ParameterizedTest @ValueSource(strings={"202613","202600"})
    void invalidExportNeverGeneratesAccessLog(String month) throws Exception {
        mvc.perform(query("/excel",month)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("baseMonth"));
        verifyNoInteractions(source,audit);
    }
    @Test void invalidUnitNeverGeneratesWorkbookOrAccessLog() throws Exception {
        mvc.perform(get(PATH+"/excel").param("year","2026").param("baseMonth","202602").param("amountUnit","달러"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("amountUnit"));
        verifyNoInteractions(source,audit);
    }
}
