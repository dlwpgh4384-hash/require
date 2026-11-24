package com.company.common.manpowerboard;

import com.company.common.common.ManpowerBoardDefinitions;
import com.company.common.manpowerboard.controller.CommonManpowerBoardController;
import com.company.common.manpowerboard.mapper.ManpowerBoardMapper;
import com.company.common.manpowerboard.mapper.ManpowerBoardRows.*;
import com.company.common.manpowerboard.service.CommonManpowerBoardService;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonManpowerBoardController.class)
@ContextConfiguration(classes = {CommonManpowerBoardController.class, CommonManpowerBoardService.class,
    ExcelExportService.class, CurrentBusinessActor.class, CodeExceptionHandler.class, ApiExceptionHandler.class})
class ManpowerBoardControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ManpowerBoardMapper mapper;
    @MockBean ManpowerBoardDefinitions definitions;
    @MockBean BusinessCalendar calendar;
    @SpyBean ExcelExportService excel;
    static final String ROOT = "/api/common/manpower-board";

    @BeforeEach void setup() {
        when(calendar.today()).thenReturn(LocalDate.of(2026, 11, 15));
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 11));
        when(calendar.now()).thenReturn(Instant.parse("2026-11-15T00:00:00Z"));
        when(mapper.codes()).thenReturn(List.of(new Code("JOB", "BE", "백엔드"),
            new Code("GRADE", "MD", "중급"), new Code("EMP_TYPE", "FT", "정규직")));
        when(mapper.developers(any())).thenReturn(List.of(
            new Developer("person", "시험 인력", "백엔드", LocalDate.of(2026, 1, 1), null)));
        when(mapper.insertAccess(anyString(), anyString())).thenReturn(1);
    }

    @ParameterizedTest @ValueSource(ints = {2026, 2027})
    void annualCalendarHasTwelveCellsAndCurrentKoreanMonthSummary(int year) throws Exception {
        mvc.perform(get(ROOT).param("year", String.valueOf(year)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.year").value(year))
            .andExpect(jsonPath("$.data.items[0].cells.length()").value(12))
            .andExpect(jsonPath("$.data.monthTotals.length()").value(12))
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.summary.idleCount").value(1))
            .andExpect(jsonPath("$.data.summary.availableMm").value(1))
            .andExpect(jsonPath("$.data.summary.futureAvailableMm").value(1));
        verify(definitions).definitions();
        verify(mapper, never()).insertAccess(anyString(), anyString());
    }

    @Test void pastFooterIsNullNotZero() throws Exception {
        mvc.perform(get(ROOT).param("year", "2026"))
            .andExpect(jsonPath("$.data.monthTotals[9].idleCount").isEmpty())
            .andExpect(jsonPath("$.data.monthTotals[9].availableMm").isEmpty())
            .andExpect(jsonPath("$.data.monthTotals[10].availableMm").value(1));
    }

    @ParameterizedTest @CsvSource({"jobCode,wrong", "gradeCode,wrong", "employeeTypeCode,wrong"})
    void unknownDbCodeIsFieldSpecific400(String field, String value) throws Exception {
        for (String suffix : List.of("", "/excel")) {
            mvc.perform(get(ROOT + suffix).param("year", "2026").param(field, value))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].field").value(field));
        }
        verify(mapper, never()).assignments(any());
        verify(mapper, never()).insertAccess(anyString(), anyString());
    }

    @ParameterizedTest @ValueSource(strings = {"2028", "2025", "nonnumeric", ""})
    void invalidYearDoesNotReadAssignmentsOrAudit(String year) throws Exception {
        for (String suffix : List.of("", "/excel")) {
            var request = get(ROOT + suffix);
            if (!year.isEmpty()) request.param("year", year);
            mvc.perform(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("year"));
        }
        verify(mapper, never()).assignments(any());
        verify(mapper, never()).insertAccess(anyString(), anyString());
    }

    @Test void confirmedMmAndConcurrentCountsAreIndependentOfDisplayPrecedence() throws Exception {
        when(mapper.assignments(any())).thenReturn(List.of(assignment("a", "0.7"), assignment("b", "0.6")));
        mvc.perform(get(ROOT).param("year", "2026"))
            .andExpect(jsonPath("$.data.summary.concurrentCount").value(1))
            .andExpect(jsonPath("$.data.summary.overAllocatedCount").value(1))
            .andExpect(jsonPath("$.data.summary.withdrawalCount").value(1))
            .andExpect(jsonPath("$.data.summary.highExtensionCount").value(1))
            .andExpect(jsonPath("$.data.items[0].cells[10].totalMm").value(1.3))
            .andExpect(jsonPath("$.data.items[0].cells[10].statusName").value("철수 예정"));
    }

    @Test void allApprovedConditionsReachEveryGroupedReadWithoutPagination() throws Exception {
        mvc.perform(get(ROOT).param("year", "2027").param("jobCode", "BE")
            .param("gradeCode", "MD").param("employeeTypeCode", "FT").param("onlyAvailable", "true"))
            .andExpect(status().isOk());
        var filter = new com.company.common.manpowerboard.dto.ManpowerBoardContracts.Filter(2027, "BE", "MD", "FT", true);
        verify(mapper).developers(filter);
        verify(mapper).assignments(filter);
        verify(mapper).monthly(filter);
        verify(mapper).history(filter);
        verify(mapper).statuses(filter);
        verify(mapper).tentative(filter);
    }

    @Test void publicExcelIsRealAnnualWorkbookWithMatchingCountFilenameAndOneAudit() throws Exception {
        var before = mvc.perform(get(ROOT).param("year", "2026")).andReturn().getResponse().getContentAsString();
        var response = mvc.perform(get(ROOT + "/excel").param("year", "2026"))
            .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1"))
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn().getResponse();
        var disposition = org.springframework.http.ContentDisposition.parse(response.getHeader("Content-Disposition"));
        assertThat(disposition.getFilename()).isEqualTo("인력 운영 보드_2026년_20261115_0900.xlsx");
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertThat(book.getNumberOfSheets()).isEqualTo(1);
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("admin", "2026-11-15 09:00");
            assertThat(sheet.getRow(2).getLastCellNum()).isEqualTo((short) 14);
            assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("1월");
            assertThat(sheet.getRow(2).getCell(13).getStringCellValue()).isEqualTo("12월");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("시험 인력");
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
        }
        verify(mapper, times(1)).insertAccess(eq("admin"), contains("2026년 1건"));
        var after = mvc.perform(get(ROOT).param("year", "2026")).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(after)).isEqualTo(json.readTree(before));
    }

    @Test void auditFailurePreventsSuccessfulDownload() throws Exception {
        when(mapper.insertAccess(anyString(), anyString())).thenThrow(new IllegalStateException("test"));
        mvc.perform(get(ROOT + "/excel").param("year", "2026"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false));
    }

    @Test void generationFailureNeverWritesDownloadAudit() throws Exception {
        doThrow(new IllegalStateException("generation unavailable")).when(excel)
            .write(anyString(), anyList(), anyList(), anyString(), any());
        mvc.perform(get(ROOT + "/excel").param("year", "2026"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false));
        verify(mapper, never()).insertAccess(anyString(), anyString());
    }

    @Test void workbookIsGeneratedBeforeExactlyOneAuditInsert() throws Exception {
        mvc.perform(get(ROOT + "/excel").param("year", "2026")).andExpect(status().isOk());
        var order = inOrder(excel, mapper);
        order.verify(excel).write(anyString(), anyList(), anyList(), eq("admin"), any());
        order.verify(mapper).insertAccess(eq("admin"), anyString());
        verify(mapper, times(1)).insertAccess(anyString(), anyString());
    }

    @Test void workbookKeepsFormulaLikeNamesAsTextAndDoesNotCollapseEqualNames() throws Exception {
        String name = "=HYPERLINK(\"https://invalid.example\",\"name\")";
        when(mapper.developers(any())).thenReturn(List.of(
            new Developer("first", name, "백엔드", null, null),
            new Developer("second", name, "백엔드", null, null)));
        var response = mvc.perform(get(ROOT + "/excel").param("year", "2026"))
            .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "2"))
            .andReturn().getResponse();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(4);
            for (int row : List.of(3, 4)) {
                assertThat(sheet.getRow(row).getCell(0).getCellType())
                    .isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
                assertThat(sheet.getRow(row).getCell(0).getStringCellValue()).isEqualTo(name);
            }
        }
    }

    @Test void onlyAvailableIncludesPartialFutureCapacityButNotPastOrPreEmploymentCapacity() throws Exception {
        when(mapper.developers(any())).thenReturn(List.of(
            new Developer("person", "일부 투입", "백엔드", null, null),
            new Developer("full", "전부 투입", "백엔드", null, null),
            new Developer("retired", "과거 인력", "백엔드", null, LocalDate.of(2026, 10, 1)),
            new Developer("future", "입사 전", "백엔드", LocalDate.of(2027, 1, 1), null)));
        when(mapper.assignments(any())).thenReturn(List.of(assignment("partial", "0.4"),
            new Assignment("full-assignment", "full", "전부", "ING", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 12, 31), BigDecimal.ONE, "H", "높음")));
        mvc.perform(get(ROOT).param("year", "2026").param("onlyAvailable", "true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.items[0].developerId").value("person"))
            .andExpect(jsonPath("$.data.summary.availableMm").value(0.6))
            .andExpect(jsonPath("$.data.monthTotals[10].availableMm").value(0.6));
    }

    @Test void calendarYearDoesNotChangeSummaryClockAndWithdrawalSortHasDeterministicTies() throws Exception {
        when(mapper.developers(any())).thenReturn(List.of(
            new Developer("z", "동명", "백엔드", null, null),
            new Developer("person", "철수 인력", "백엔드", null, null),
            new Developer("a", "동명", "백엔드", null, null)));
        when(mapper.assignments(any())).thenReturn(List.of(assignment("partial", "0.4")));
        var first = json.readTree(mvc.perform(get(ROOT).param("year", "2026"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].developerId").value("person"))
            .andExpect(jsonPath("$.data.items[1].developerId").value("a"))
            .andExpect(jsonPath("$.data.items[2].developerId").value("z"))
            .andReturn().getResponse().getContentAsString()).path("data").path("summary");
        var second = json.readTree(mvc.perform(get(ROOT).param("year", "2027"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("summary");
        assertThat(second).isEqualTo(first);
    }

    static Assignment assignment(String id, String mm) {
        return new Assignment(id, "person", "프로젝트 " + id, "ING", LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31), new BigDecimal(mm), "H", "높음");
    }
}
