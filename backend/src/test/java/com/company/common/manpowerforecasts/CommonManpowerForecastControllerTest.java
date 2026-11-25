package com.company.common.manpowerforecasts;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.manpowerforecasts.controller.CommonManpowerForecastController;
import com.company.common.manpowerforecasts.mapper.ManpowerForecastMapper;
import com.company.common.manpowerforecasts.service.*;
import com.company.common.salespipeline.mapper.SalesPipelineMapper;
import com.company.common.salespipeline.mapper.SalesPipelineRows.*;
import com.company.common.salespipeline.service.*;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.company.common.withdrawals.service.WithdrawalCalculationRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonManpowerForecastController.class)
@ContextConfiguration(classes = {CommonManpowerForecastController.class, CommonManpowerForecastService.class,
    ManpowerForecastExcelService.class, SalesPipelineForecastService.class, WithdrawalCalculationRules.class,
    ExcelExportService.class, CurrentBusinessActor.class, CodeExceptionHandler.class, ApiExceptionHandler.class})
class CommonManpowerForecastControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean SalesPipelineMapper workforce;
    @MockBean ManpowerForecastMapper audit;
    @MockBean CalculationRuleService metadata;
    @MockBean SalesPipelineCalculationRules calculations;
    @MockBean BusinessCalendar calendar;
    @MockBean PlatformTransactionManager transactions;
    @SpyBean ExcelExportService excel;

    @BeforeEach void setup() throws Exception {
        ManpowerForecastTestData.definitions(metadata, calculations);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(calendar.now()).thenReturn(Instant.parse("2026-11-15T00:00:00Z"));
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 11));
        when(calendar.toDatabaseMonth(any())).thenAnswer(call -> ((YearMonth) call.getArgument(0)).format(java.time.format.DateTimeFormatter.ofPattern("uuuuMM")));
        when(workforce.jobs()).thenReturn(List.of(new Job("BE", "백엔드")));
        when(workforce.opportunityDemand(anyList())).thenReturn(List.of(new MonthOpportunityDemand("202611", "BE", BigDecimal.ONE, new BigDecimal("1.25"), new BigDecimal("100"))));
    }
    @Test void publicGetReturnsApprovedEnvelopeSummaryAndThresholds() throws Exception {
        mvc.perform(get("/api/common/manpower-forecasts").param("year", "2026"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.months.length()").value(2))
            .andExpect(jsonPath("$.data.months[0].demand").value(1.25))
            // 차이 = 한 자리 가용(0.0) − 한 자리 필요(1.25 → 1.3), 긴 소수(DECIMAL128)가 아니다.
            .andExpect(jsonPath("$.data.months[0].difference").value(-1.3))
            .andExpect(jsonPath("$.data.summary.lastMonthDemand").value(0))
            .andExpect(jsonPath("$.data.summary.mostShortage.jobName").value("백엔드"))
            .andExpect(jsonPath("$.data.summary.mostShortage.maximumShortage").value(1.3))
            .andExpect(jsonPath("$.data.thresholds.shortageThreshold").value(0.4));
        verifyNoInteractions(audit);
    }
    @Test void publicExcelIsRealXlsxThreeRowsSameProjectionRecipientTimeAndFilename() throws Exception {
        var query = mvc.perform(get("/api/common/manpower-forecasts").param("year", "2026")).andExpect(status().isOk()).andReturn().getResponse();
        var months = json.readTree(query.getContentAsString(StandardCharsets.UTF_8)).path("data").path("months");
        var response = mvc.perform(get("/api/common/manpower-forecasts/excel").param("year", "2026"))
            .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("X-Total-Count", "2")).andReturn().getResponse();
        var disposition = org.springframework.http.ContentDisposition.parse(response.getHeader("Content-Disposition"));
        assertThat(disposition.getFilename()).isEqualTo("인력 수요 예측_2026년_20261115_0900.xlsx");
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertThat(book.getNumberOfSheets()).isEqualTo(1);
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("admin", "2026-11-15 09:00");
            assertThat(sheet.getLastRowNum()).isEqualTo(5);
            for (int r = 0; r < 3; r++) {
                assertThat(sheet.getRow(r + 3).getCell(0).getStringCellValue()).isEqualTo(List.of("필요 인력", "가용 인력", "차이").get(r));
                for (int c = 0; c < months.size(); c++) {
                    String field = List.of("demand", "supply", "difference").get(r);
                    double expected = months.get(c).path(field).decimalValue().setScale(1, java.math.RoundingMode.HALF_UP).doubleValue();
                    assertThat(sheet.getRow(r + 3).getCell(c + 1).getNumericCellValue()).isEqualTo(expected);
                    assertThat(sheet.getRow(r + 3).getCell(c + 1).getCellStyle().getDataFormatString()).isEqualTo("#,##0.0");
                }
            }
        }
        var order = inOrder(excel, audit);
        order.verify(excel).write(eq("인력 수요 예측"), anyList(), anyList(), eq("admin"), any());
        order.verify(audit).insertAccess("admin", 2);
        verify(workforce, never()).insertAccess(anyString(), anyInt());
    }
    @ParameterizedTest @ValueSource(strings = {"/api/common/manpower-forecasts", "/api/common/manpower-forecasts/excel"})
    void missingYearIsFieldErrorWithoutBusinessReadsOrSuccessfulAudit(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("year"));
        verifyNoInteractions(workforce, audit);
    }
    @ParameterizedTest @ValueSource(strings = {"/api/common/manpower-forecasts", "/api/common/manpower-forecasts/excel"})
    void malformedYearIsFieldError(String path) throws Exception {
        mvc.perform(get(path).param("year", "2026.5")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("year"));
        verifyNoInteractions(workforce, audit);
    }
    @ParameterizedTest @ValueSource(strings = {"/api/common/manpower-forecasts", "/api/common/manpower-forecasts/excel"})
    void outOfRangeYearIsFieldError(String path) throws Exception {
        // 올해(2026)·내년(2027)만 고를 수 있다. 지난해·내후년은 400.
        for (String year : List.of("2025", "2024", "2028")) {
            var response = mvc.perform(get(path).param("year", year)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("year")).andReturn().getResponse();
            assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("선택할 수 있는 조회 연도를 확인해 주세요.");
        }
        verifyNoInteractions(workforce, audit);
    }
    @Test void nextYearExportsTwelveMonthsWithYearFileName() throws Exception {
        var file = mvc.perform(get("/api/common/manpower-forecasts/excel").param("year", "2027"))
            .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "12")).andReturn().getResponse();
        var disposition = org.springframework.http.ContentDisposition.parse(file.getHeader("Content-Disposition"));
        assertThat(disposition.getFilename()).isEqualTo("인력 수요 예측_2027년_20261115_0900.xlsx");
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(file.getContentAsByteArray()))) {
            assertThat(book.getSheetAt(0).getRow(3).getLastCellNum()).isEqualTo((short) 13);
            assertThat(book.getSheetAt(0).getRow(5).getCell(0).getStringCellValue()).isEqualTo("차이");
        }
        verify(audit).insertAccess("admin", 12);
    }
    @Test void serializationFailureNeverRecordsSuccessfulDownload() throws Exception {
        doThrow(new IllegalStateException("serialization failed")).when(excel).write(anyString(), anyList(), anyList(), anyString(), any());
        mvc.perform(get("/api/common/manpower-forecasts/excel").param("year", "2026")).andExpect(status().isInternalServerError());
        verifyNoInteractions(audit);
    }
    @Test void calculationFailureNeverRecordsSuccessfulDownload() throws Exception {
        when(workforce.supply(anyList())).thenThrow(new IllegalStateException("calculation failed"));
        mvc.perform(get("/api/common/manpower-forecasts/excel").param("year", "2026")).andExpect(status().isInternalServerError());
        verifyNoInteractions(audit);
        verify(excel, never()).write(anyString(), anyList(), anyList(), anyString(), any());
    }
    @Test void auditFailureDoesNotReturnBinarySuccess() throws Exception {
        when(audit.insertAccess(anyString(), anyInt())).thenThrow(new IllegalStateException("audit failed"));
        mvc.perform(get("/api/common/manpower-forecasts/excel").param("year", "2026")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false));
    }
}
