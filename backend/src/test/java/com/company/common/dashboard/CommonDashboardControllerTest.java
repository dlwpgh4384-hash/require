package com.company.common.dashboard;

import com.company.common.dashboard.controller.CommonDashboardController;
import com.company.common.dashboard.dto.DashboardContracts.*;
import com.company.common.dashboard.service.*;
import com.company.common.dashboard.mapper.DashboardMapper;
import com.company.common.monthcloses.service.CommonMonthCloseService;
import com.company.common.monthcloses.dto.MonthCloseContracts.*;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonDashboardController.class)
@ContextConfiguration(classes = {CommonDashboardController.class, CommonDashboardService.class,
    DashboardExcelService.class, ExcelExportService.class, CurrentBusinessActor.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class})
class CommonDashboardControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean DashboardFinancialService financial;
    @MockBean DashboardOutlookService outlook;
    @MockBean DashboardActivityService activity;
    @MockBean DashboardAssignmentService assignments;
    @MockBean CommonMonthCloseService closes;
    @MockBean UserPreferencePeriodBasis periods;
    @MockBean BusinessCalendar calendar;
    @MockBean DashboardMapper audit;
    @SpyBean ExcelExportService excel;

    @BeforeEach void setup() {
        var first = YearMonth.of(2026, 11);
        when(calendar.today()).thenReturn(LocalDate.of(2026, 11, 25));
        when(calendar.now()).thenReturn(Instant.parse("2026-11-25T01:00:00Z"));
        when(periods.metadata()).thenReturn(Map.of("periodBasis",
            new UserPreferencePeriodBasis.Basis("202611", "202601", List.of("202610"))));
        var summary = new Summary(new BigDecimal("1234"), null, null, new BigDecimal("-10"),
            null, null, new BigDecimal("-40"), null, null, new BigDecimal("90"), 3,
            BigDecimal.ZERO, 0, 0, BigDecimal.ZERO);
        when(assignments.read(any())).thenReturn(new DashboardAssignmentService.Counts(0, 3, 0, List.of()));
        when(financial.read(any(), any(), anyInt())).thenReturn(new DashboardFinancialService.Financial(summary,
            List.of(new TrendMonth("2026-11", new BigDecimal("1234"), null, null)), List.of()));
        var manpower = IntStream.range(0, 6).mapToObj(i -> new ManpowerMonth(first.plusMonths(i).toString(),
            BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, false)).toList();
        var cash = IntStream.range(0, 3).mapToObj(i -> new CashMonth(first.plusMonths(i).toString(), null, false, null)).toList();
        when(outlook.read(any(), any(), any())).thenReturn(new DashboardOutlookService.Outlook(manpower, List.of(),
            new Pipeline(0, BigDecimal.ZERO, List.of()), cash, new BigDecimal("50000000"), null, List.of(), List.of()));
        when(activity.read(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(closes.preview(any())).thenReturn(new CommonMonthCloseService.Preview(
            new MonthClose("2026-11", false, false, null, null, null, 6, 1, List.of()),
            List.of(new Check("급여 업로드", "확인 필요", "3명 — 급여 없음", "UI-26"))));
    }

    @Test void basisLinesFollowMockupForCurrentAndPastMonth() throws Exception {
        var current = mvc.perform(get("/api/common/dashboard").param("yearMonth", "2026-11"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.asOfText").value("오늘 2026-11-25 · 10월까지 마감"))
            .andExpect(jsonPath("$.data.provisionalNotice").value("11월은 마감 전이라 숫자는 잠정입니다"))
            .andExpect(jsonPath("$.data.mixedBasisNotice").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.minimumCash").value(50000000))
            .andExpect(jsonPath("$.data.summary.idlePeople").value(3))
            .andExpect(jsonPath("$.data.closing.remainingItems[0].name").value("급여 업로드"))
            .andExpect(jsonPath("$.data.closing.remainingItems[0].description").value("3명 — 급여 없음"))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(current).contains("asOfText");
        mvc.perform(get("/api/common/dashboard").param("yearMonth", "2026-10"))
            .andExpect(jsonPath("$.data.mixedBasisNotice")
                .value("미수금·확인할 일·프로젝트 경보·인력 수급·파이프라인·자금은 오늘 기준(청구 누락 의심은 고른 달)"))
            .andExpect(jsonPath("$.data.provisionalNotice").value("10월은 마감 전이라 숫자는 잠정입니다"));
        // 마지막 마감(2026-10) 다음 달부터 자금을 본다(시안 CF.m > closedTo).
        verify(outlook, atLeastOnce()).read(YearMonth.of(2026, 11), YearMonth.of(2026, 11), YearMonth.of(2026, 11));
    }

    @Test void publicQueryPreservesNullMixedBasisAndForwardWindowsWithoutDownloadAudit() throws Exception {
        mvc.perform(get("/api/common/dashboard").param("yearMonth", "2026-10"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.selectedMonth").value("2026-10"))
            .andExpect(jsonPath("$.data.today").value("2026-11-25"))
            .andExpect(jsonPath("$.data.closing.completedCount").value(5))
            .andExpect(jsonPath("$.data.closingState").value("작업중"))
            .andExpect(jsonPath("$.data.summary.utilizationRate").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.mixedBasisNotice").isString())
            .andExpect(jsonPath("$.data.manpower[5].month").value("2027-04"))
            .andExpect(jsonPath("$.data.cash[2].month").value("2027-01"));
        verify(financial).read(YearMonth.of(2026, 10), LocalDate.of(2026, 11, 25), 3);
        verify(outlook).read(YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2026, 11));
        verify(closes, never()).getCommonMonthClose(any());
        verifyNoInteractions(audit);
    }

    @Test void projectAlertMonthIsFirstOpenMonthWhenTodayIsLater() throws Exception {
        // 마지막 마감 2026-09, 오늘 2026-11-25: 원가 분석은 2026-10까지만 받으니 경보도 그 달 기준.
        when(periods.metadata()).thenReturn(Map.of("periodBasis",
            new UserPreferencePeriodBasis.Basis("202610", "202601", List.of("202609"))));
        mvc.perform(get("/api/common/dashboard").param("yearMonth", "2026-10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projectAlertMonth").value("2026-10"));
        verify(outlook).read(YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2026, 10));
        assertThat(DashboardOutlookService.alertMonth(YearMonth.of(2026, 10), YearMonth.of(2026, 11)))
            .isEqualTo(YearMonth.of(2026, 10));
        assertThat(DashboardOutlookService.alertMonth(YearMonth.of(2026, 12), YearMonth.of(2026, 11)))
            .isEqualTo(YearMonth.of(2026, 11));
    }
    @Test void projectAlertMonthIsTodayMonthWhenOpen() throws Exception {
        mvc.perform(get("/api/common/dashboard").param("yearMonth", "2026-11"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projectAlertMonth").value("2026-11"));
    }

    @ParameterizedTest @ValueSource(strings = {"", "202611", "2026-1", "2026-13", "2025-12", "2026-12"})
    void invalidPeriodUsesFieldErrorForBothOperations(String month) throws Exception {
        for (String suffix : List.of("", "/excel")) mvc.perform(get("/api/common/dashboard" + suffix).param("yearMonth", month))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors[0].field").value("yearMonth"));
        verifyNoInteractions(audit, financial, outlook, activity);
    }
    @Test void missingMonthIsValidationErrorAndMutationIsNotExposed() throws Exception {
        mvc.perform(get("/api/common/dashboard")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("yearMonth"));
        mvc.perform(post("/api/common/dashboard")).andExpect(status().isMethodNotAllowed());
    }
    @Test void workbookHasRealNumericCellsCountAndOneSuccessfulAudit() throws Exception {
        var response = mvc.perform(get("/api/common/dashboard/excel").param("yearMonth", "2026-11"))
            .andExpect(status().isOk()).andExpect(header().string("Content-Type",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=")))
            .andReturn().getResponse();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = book.getSheetAt(0);
            int count = Integer.parseInt(response.getHeader("X-Total-Count"));
            assertThat(sheet.getLastRowNum() - 2).isEqualTo(count);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("admin", "2026-11-25");
            assertThat(sheet.getRow(3).getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(3).getCell(4).getNumericCellValue()).isEqualTo(1234);
        }
        verify(audit).insertAccess(eq("admin"), contains("대시보드 2026-11"));
        verify(closes, never()).getCommonMonthClose(any());
    }
    @Test void sourceFailureDoesNotLeakSqlOrRecordSuccessfulDownload() throws Exception {
        when(financial.read(any(), any(), anyInt())).thenThrow(new IllegalStateException("SELECT secret FROM private_table"));
        var response = mvc.perform(get("/api/common/dashboard/excel").param("yearMonth", "2026-11"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false))
            .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("SELECT", "private_table", "IllegalStateException");
        verifyNoInteractions(audit);
    }
    @Test void bothOperationsRemainPublicInClasspathContract() throws Exception {
        Map<?, ?> contract;
        try (var stream = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            contract = new org.yaml.snakeyaml.Yaml().load(stream);
        }
        var paths = (Map<?, ?>) contract.get("paths");
        for (String path : List.of("/api/common/dashboard", "/api/common/dashboard/excel")) {
            var operation = (Map<?, ?>) ((Map<?, ?>) paths.get(path)).get("get");
            assertThat(operation.get("operationId")).isNotNull();
            assertThat(operation.get("security")).isIn(null, List.of());
        }
    }
}
