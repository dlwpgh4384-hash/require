package com.company.common.profitstructures;

import com.company.common.calculation.mapper.*;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.profitstructures.controller.CommonProfitStructureController;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.profitstructures.mapper.ProfitStructureRows.*;
import com.company.common.profitstructures.dto.ProfitStructureContracts.*;
import com.company.common.profitstructures.service.*;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
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
import static com.company.common.profitstructures.ProfitStructureTestSources.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonProfitStructureController.class)
@ContextConfiguration(classes = {CommonProfitStructureController.class, CommonProfitStructureService.class,
    ProfitStructureMonthlyProfitService.class, ProfitStructureDefinitions.class, ProfitStructureExcelService.class,
    CalculationRuleService.class, UserPreferencePeriodBasis.class, CodeExceptionHandler.class, ApiExceptionHandler.class,
    ExcelExportService.class, BusinessCalendar.class, CurrentBusinessActor.class})
class CommonProfitStructureControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ProfitStructureMonthlyProfitService monthly;
    @MockBean ProfitStructureMapper mapper;
    @MockBean CalculationRuleMapper definitions;
    @MockBean UserPreferencePeriodSourceMapper periods;
    @MockBean PlatformTransactionManager transactions;
    static final String PATH = "/api/common/profit-structures";
    @BeforeEach void setup() throws Exception {
        definitions(definitions);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(periods.selectCashBaseYm()).thenReturn(n("202601"));
        when(periods.selectClosedMonths()).thenReturn(List.of("202601"));
        var standard = new CalculationStandardRow(); standard.standardCode="WTHLD_RT"; standard.effectiveMonth="202601"; standard.value=n("3.3");
        when(definitions.selectEffectiveStandard(eq("WTHLD_RT"), anyString())).thenReturn(standard);
        when(mapper.snapshots(any())).thenReturn(List.of(fixed("202601")));
        when(mapper.projects(any())).thenReturn(List.of(project("202601","P","MO","C1"),project("202602","P","MO","C1")));
        when(mapper.costs(any())).thenReturn(List.of(new StoredCost("202601","P","ALLOC",n("1000"),n("500"),n("10"),n("20"),n("50"))));
        when(mapper.people(any())).thenReturn(List.of(person())); when(mapper.payroll(any())).thenReturn(List.of(pay()));
        when(mapper.assignments(any())).thenReturn(List.of(assignment())); when(mapper.codes()).thenReturn(codes());
        when(mapper.amounts(any())).thenReturn(List.of(new Amount("202602","SGA",n("100")),new Amount("202602","ADJUSTMENT",n("-5")),new Amount("202602","NOPR_IN",n("20")),new Amount("202602","NOPR_OUT",n("5"))));
        when(mapper.annualTax("2026")).thenReturn(n("120"));
        when(mapper.insertAccess(anyString(),anyString())).thenReturn(1);
    }
    MockHttpServletRequestBuilder query(String suffix, String unit, String month) {
        return get(PATH+suffix).param("year",month.substring(0,4)).param("periodUnit",unit).param("baseMonth",month);
    }
    @Test @DisplayName("FX-PROFIT-007: public mixed fixed/live quarter has one period for every projection")
    void publicMixedQuarterIsConsistent() throws Exception {
        mvc.perform(query("","quarter","202602")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.periodStart").value("202601")).andExpect(jsonPath("$.data.periodEnd").value("202602"))
            .andExpect(jsonPath("$.data.summary.revenue").value(2000)).andExpect(jsonPath("$.data.waterfall.revenue").value(2000))
            .andExpect(jsonPath("$.data.trends[0].revenue").value(2000)).andExpect(jsonPath("$.data.numericRows[0].summary.revenue").value(2000))
            .andExpect(jsonPath("$.data.summary.grossProfitRate").value(51.0)).andExpect(jsonPath("$.data.summary.operatingProfit").value(825))
            .andExpect(jsonPath("$.data.waterfall.netProfit").value(835)).andExpect(jsonPath("$.data.composition.projectSalesParts[0].name").value("프로젝트 영업 파트"))
            .andExpect(jsonPath("$.data.comparison.previousYearVisible").value(false)).andExpect(jsonPath("$.data.comparison.previousYear").isEmpty());
        verify(mapper,never()).insertAccess(anyString(),anyString());
    }
    @ParameterizedTest @ValueSource(strings={"202613","202512","202600","20261","20260200"})
    void invalidBaseMonthFailsBeforeBusinessQueries(String month) throws Exception {
        mvc.perform(get(PATH).param("year","2026").param("periodUnit","month").param("baseMonth",month))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("baseMonth"));
        verifyNoInteractions(mapper);
    }
    @Test void unmaterializedMonthUsesSharedReadonlyProfitBoundary() throws Exception {
        var result=monthly.year(2026,2,"202601").get("202602").values();
        mvc.perform(query("","month","202602")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.operatingProfit").value(result.operatingProfit().intValueExact()))
            .andExpect(jsonPath("$.data.waterfall.netProfit").value(result.netProfit().intValueExact()));
        assertThat(result.allocatedCost()).isEqualByComparingTo("200"); assertThat(result.directExpense()).isEqualByComparingTo("80");
        assertThat(result.operatingProfit()).isEqualByComparingTo("615"); assertThat(result.netProfit()).isEqualByComparingTo("620");
        verify(mapper,never()).insertAccess(anyString(),anyString());
    }
    @Test void fixedSnapshotWinsOverConflictingUpstreamAndDoesNotLog() throws Exception {
        var before=fixed("202601");
        when(mapper.snapshots(any())).thenReturn(List.of(before));
        mvc.perform(query("","month","202601")).andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.operatingProfit").value(210))
            .andExpect(jsonPath("$.data.provisional").value(false)).andExpect(jsonPath("$.data.provisionalNotice").isEmpty());
        assertThat(mapper.snapshots(new Query(2026,1))).containsExactly(before);
        verify(mapper,never()).insertAccess(anyString(),anyString());
    }
    @Test void closeMarkerWithoutFixedSnapshotStillCalculatesProvisional() throws Exception {
        when(periods.selectClosedMonths()).thenReturn(List.of("202601","202602"));
        mvc.perform(query("","month","202602")).andExpect(status().isOk()).andExpect(jsonPath("$.data.provisional").value(true))
            .andExpect(jsonPath("$.data.provisionalNotice").value("2월은 아직 마감 전이라 요약 칸과 누계는 잠정 숫자입니다. 마감하면 확정됩니다."));
    }
    @Test void secondQuarterSecondMonthComparesOnlyTwoMonthsOfPreviousQuarter() throws Exception {
        when(periods.selectClosedMonths()).thenReturn(List.of("202604"));
        var march=new Snapshot("202603",n("9000"),n("500"),n("150"),n("50"),n("700"),n("8300"),n("100"),n("10"),n("8210"),n("20"),n("5"),n("10"),n("8215"),"N","Y",null);
        when(mapper.snapshots(any())).thenReturn(List.of(fixed("202601"),fixed("202602"),march,fixed("202604"),fixed("202605")));
        mvc.perform(query("","quarter","202605")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.periodStart").value("202604")).andExpect(jsonPath("$.data.periodEnd").value("202605"))
            .andExpect(jsonPath("$.data.summary.revenue").value(2000)).andExpect(jsonPath("$.data.comparison.previousPeriod.revenue").value(2000))
            .andExpect(jsonPath("$.data.trends.length()").value(2)).andExpect(jsonPath("$.data.trends[0].revenue").value(11000))
            .andExpect(jsonPath("$.data.numericRows[1].summary.revenue").value(2000))
            .andExpect(jsonPath("$.data.comparison.previousPeriodLabel").value("전 분기 같은 기간"))
            .andExpect(jsonPath("$.data.periodName").value("2026년 2분기(5월까지)"))
            .andExpect(jsonPath("$.data.numericTable.columns[0].label").value("1분기"))
            .andExpect(jsonPath("$.data.numericTable.columns[1].label").value("2분기(5월까지)"))
            .andExpect(jsonPath("$.data.numericTable.rows[0].label").value("매출"))
            .andExpect(jsonPath("$.data.numericTable.rows[0].values[1]").value(2000));
    }
    @Test void yearToDateHasMonthlyCumulativeColumnsAndNoPreviousPeriod() throws Exception {
        mvc.perform(query("","year-to-date","202602")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.numericTable.columns.length()").value(2))
            .andExpect(jsonPath("$.data.numericTable.columns[0].label").value("1월"))
            .andExpect(jsonPath("$.data.numericTable.columns[1].label").value("2월"))
            .andExpect(jsonPath("$.data.numericRows[0].period").value("1월"))
            .andExpect(jsonPath("$.data.numericRows[1].period").value("2월"))
            .andExpect(jsonPath("$.data.periodName").value("2026년 1~2월 누계"))
            .andExpect(jsonPath("$.data.comparison.previousPeriod").isEmpty())
            .andExpect(jsonPath("$.data.comparison.previousPeriodLabel").isEmpty())
            .andExpect(jsonPath("$.meta.previousPeriodAvailable").value(false));
    }
    @Test void migratedMonthKeepsUnknownLeavesAndIsNeverProvisional() throws Exception {
        when(mapper.snapshots(any())).thenReturn(List.of(migrated("202501")));
        mvc.perform(query("","month","202501")).andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.revenue").value(1000))
            .andExpect(jsonPath("$.data.summary.grossProfit").isEmpty()).andExpect(jsonPath("$.data.waterfall.netProfit").isEmpty())
            .andExpect(jsonPath("$.data.provisional").value(false)).andExpect(jsonPath("$.data.compositionVisible").value(false))
            .andExpect(jsonPath("$.data.referenceRevenueVisible").value(false)).andExpect(jsonPath("$.data.numericRows[0].composition").isEmpty());
    }
    @Test void exportIsPublicAndWritesTheNumericTableItemsByPeriods() throws Exception {
        var bytes=mvc.perform(get("/api/common/profit-structures/excel").param("year","2026").param("periodUnit","quarter").param("baseMonth","202602"))
            .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("attachment"))).andReturn().getResponse().getContentAsByteArray();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet=book.getSheetAt(0); assertThat(sheet.getRow(2).getPhysicalNumberOfCells()).isEqualTo(2);
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("항목");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("1분기(2월까지) 잠정");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("매출");
            assertThat(sheet.getRow(3).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(3).getCell(1).getNumericCellValue()).isEqualTo(2000);
            assertThat(sheet.getRow(12).getCell(0).getStringCellValue()).isEqualTo("영업이익");
            assertThat(sheet.getRow(12).getCell(1).getNumericCellValue()).isEqualTo(825);
            assertThat(sheet.getRow(9).getCell(0).getStringCellValue()).isEqualTo("매출총이익률");
            assertThat(sheet.getRow(9).getCell(1).getCellStyle().getDataFormatString()).isEqualTo("0.0");
            assertThat(sheet.getRow(3).getCell(1).getCellStyle().getDataFormatString()).isEqualTo("#,##0");
        }
        verify(mapper,times(1)).insertAccess(eq("admin"),contains("202601–202602"));
    }
    @Test void closedExportHasTextPeriodButNoProvisionalMarker() throws Exception {
        var bytes=mvc.perform(query("/excel","month","202601")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var cell=book.getSheetAt(0).getRow(2).getCell(1);
            assertThat(cell.getCellType()).isEqualTo(CellType.STRING); assertThat(cell.getStringCellValue()).isEqualTo("1월");
        }
    }
    @Test void invalidMonthNeverGeneratesDownloadLog() throws Exception {
        mvc.perform(query("/excel","month","202613")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("baseMonth"));
        verifyNoInteractions(mapper);
    }
    @Test void migratedExportOmitsHiddenCompositionReferenceAndProvisional() throws Exception {
        when(mapper.snapshots(any())).thenReturn(List.of(migrated("202501")));
        var bytes=mvc.perform(query("/excel","month","202501"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet=book.getSheetAt(0); assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("1월");
            assertThat(sheet.getLastRowNum()).isEqualTo(3+14);
            assertThat(sheet.getRow(3+13).getCell(0).getStringCellValue()).isEqualTo("당기순이익");
            assertThat(sheet.getRow(3+13).getCell(1).getStringCellValue()).isEqualTo("–");
        }
    }
}
