package com.company.common.manpowerboard;

import com.company.common.common.ManpowerBoardDefinitions;
import com.company.common.manpowerboard.controller.CommonManpowerBoardController;
import com.company.common.manpowerboard.mapper.ManpowerBoardMapper;
import com.company.common.manpowerboard.mapper.ManpowerBoardRows;
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
        when(mapper.developers()).thenReturn(List.of(
            developer("person", "시험 인력", LocalDate.of(2026, 1, 1), null)));
        when(mapper.insertAccess(anyString(), anyString())).thenReturn(1);
    }

    /** 직접 인력·대기(IDLE)·투입 종료일 없음이 기본. */
    static Developer developer(String id, String name, LocalDate hire, LocalDate retire) {
        return developer(id, name, "BE", "IDLE", hire, retire, null, null);
    }

    static Developer developer(String id, String name, String job, String work, LocalDate hire, LocalDate retire,
        LocalDate firstEnd, LocalDate lastEnd) {
        return new Developer(id, "K-" + id, name, job, "백엔드", "MD", "FT", work, hire, retire, firstEnd, lastEnd);
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
            .andExpect(jsonPath("$.data.items[0].cells[10].statusCode").value("end"))
            .andExpect(jsonPath("$.data.items[0].cells[10].statusName").value("철수 예정"))
            .andExpect(jsonPath("$.data.items[0].cells[10].extensionPossibilityName").value("높음"))
            .andExpect(jsonPath("$.data.items[0].managementNumber").value("K-person"));
    }

    @Test void allApprovedConditionsReachEveryGroupedReadWithoutPagination() throws Exception {
        mvc.perform(get(ROOT).param("year", "2027").param("jobCode", "BE")
            .param("gradeCode", "MD").param("employeeTypeCode", "FT").param("onlyAvailable", "true"))
            .andExpect(status().isOk());
        // 읽는 기간 = min(연초, 이번 달 1일) ~ max(연말, 이번 달 + 3의 말일).
        var period = new ManpowerBoardRows.Period(LocalDate.of(2026, 11, 1), LocalDate.of(2027, 12, 31));
        verify(mapper, times(1)).developers();
        verify(mapper).assignments(period);
        verify(mapper).monthly(period);
        verify(mapper).history(period);
        verify(mapper).statuses(period);
        verify(mapper).tentative(period);
        mvc.perform(get(ROOT).param("year", "2026")).andExpect(status().isOk());
        verify(mapper).assignments(new ManpowerBoardRows.Period(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 2, 28)));
    }

    @Test void withdrawalConcurrentAndExcessCountWholeRosterButIdleAndFreeFollowFilterAndOnlyAvailable() throws Exception {
        when(mapper.developers()).thenReturn(List.of(
            developer("person", "백엔드 인력", LocalDate.of(2026, 1, 1), null),
            developer("other", "다른 직무", "FE", "INPUT", null, null, null, LocalDate.of(2026, 12, 31))));
        when(mapper.codes()).thenReturn(List.of(new Code("JOB", "BE", "백엔드"), new Code("JOB", "FE", "프론트"),
            new Code("GRADE", "MD", "중급"), new Code("EMP_TYPE", "FT", "정규직")));
        var other = new Assignment("o1", "other", "프로젝트 o1", "ING", LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31), new BigDecimal("0.7"), "H", "높음 (80%)");
        var other2 = new Assignment("o2", "other", "프로젝트 o2", "ING", LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31), new BigDecimal("0.6"), "M", "보통 (50%)");
        when(mapper.assignments(any())).thenReturn(List.of(other, other2));
        String plain = null;
        for (String only : List.of("false", "true")) {
            var summary = json.readTree(mvc.perform(get(ROOT).param("year", "2026").param("jobCode", "BE").param("onlyAvailable", only))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].developerId").value("person"))
                .andExpect(jsonPath("$.data.summary.idleCount").value(1))
                .andExpect(jsonPath("$.data.summary.availableMm").value(1))
                .andExpect(jsonPath("$.data.summary.withdrawalCount").value(1))
                .andExpect(jsonPath("$.data.summary.highExtensionCount").value(1))
                .andExpect(jsonPath("$.data.summary.concurrentCount").value(1))
                .andExpect(jsonPath("$.data.summary.overAllocatedCount").value(1))
                .andReturn().getResponse().getContentAsString()).path("data").path("summary").toString();
            if (plain == null) plain = summary; else assertThat(summary).isEqualTo(plain);
        }
    }

    @Test void tentativeCellHasNoProjectsAndExcelShowsOpportunityWithoutMmAndFooterRows() throws Exception {
        when(mapper.tentative(any())).thenReturn(List.of(new Tentative("person", "신규 기회", LocalDate.of(2026, 12, 1),
            LocalDate.of(2027, 5, 31), new BigDecimal("30"))));
        mvc.perform(get(ROOT).param("year", "2026")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].cells[10].statusCode").value("idle"))
            .andExpect(jsonPath("$.data.items[0].cells[11].statusCode").value("tent"))
            .andExpect(jsonPath("$.data.items[0].cells[11].projects.length()").value(0))
            .andExpect(jsonPath("$.data.items[0].cells[11].winningProbability").value(30))
            .andExpect(jsonPath("$.data.monthTotals[11].idleCount").value(0))
            .andExpect(jsonPath("$.data.monthTotals[11].availableMm").value(1));
        var response = mvc.perform(get(ROOT + "/excel").param("year", "2026")).andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1")).andReturn().getResponse();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(3).getCell(13).getStringCellValue()).isEqualTo("가배정 · 신규 기회 (30%)");
            assertThat(sheet.getRow(3).getCell(12).getStringCellValue()).isEqualTo("비어 있음");
            var idle = sheet.getRow(4);
            assertThat(idle.getCell(0).getStringCellValue()).isEqualTo("비는 인원");
            assertThat(idle.getCell(11).getStringCellValue()).isEqualTo("–");
            assertThat(idle.getCell(12).getStringCellValue()).isEqualTo("1명");
            assertThat(idle.getCell(13).getStringCellValue()).isEqualTo("0명");
            var free = sheet.getRow(5);
            assertThat(free.getCell(0).getStringCellValue()).isEqualTo("남는 MM");
            assertThat(free.getCell(1).getStringCellValue()).isEqualTo("일부 투입 포함");
            assertThat(free.getCell(2).getStringCellValue()).isEqualTo("–");
            assertThat(free.getCell(12).getStringCellValue()).isEqualTo("1.0");
            assertThat(free.getCell(13).getStringCellValue()).isEqualTo("1.0");
        }
        verify(mapper, times(1)).insertAccess(eq("admin"), contains("2026년 1건"));
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
            // 사람 1줄 + 합계 줄 2줄(비는 인원·남는 MM)
            assertThat(sheet.getLastRowNum()).isEqualTo(5);
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
        when(mapper.developers()).thenReturn(List.of(
            developer("first", name, null, null),
            developer("second", name, null, null)));
        var response = mvc.perform(get(ROOT + "/excel").param("year", "2026"))
            .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "2"))
            .andReturn().getResponse();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(6);
            for (int row : List.of(3, 4)) {
                assertThat(sheet.getRow(row).getCell(0).getCellType())
                    .isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
                assertThat(sheet.getRow(row).getCell(0).getStringCellValue()).isEqualTo(name);
            }
        }
    }

    @Test void onlyAvailableIncludesPartialFutureCapacityButNotPastOrPreEmploymentCapacity() throws Exception {
        when(mapper.developers()).thenReturn(List.of(
            developer("person", "일부 투입", null, null),
            developer("full", "전부 투입", null, null),
            developer("retired", "과거 인력", null, LocalDate.of(2026, 10, 1)),
            developer("future", "입사 전", LocalDate.of(2027, 1, 1), null)));
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
        // 시안 정렬: 마지막 투입 종료일 빠른 순, 투입이 없는 사람('0')이 맨 앞, 같으면 이름(한국어)·ID.
        when(mapper.developers()).thenReturn(List.of(
            developer("z", "동명", "BE", "IDLE", null, null, null, null),
            developer("late", "가나다", "BE", "INPUT", null, null, LocalDate.of(2027, 6, 30), LocalDate.of(2027, 6, 30)),
            developer("person", "철수 인력", "BE", "INPUT", null, null, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 31)),
            developer("a", "동명", "BE", "IDLE", null, null, null, null),
            developer("b", "가나다", "BE", "IDLE", null, null, null, null)));
        when(mapper.assignments(any())).thenReturn(List.of(assignment("partial", "0.4")));
        var first = json.readTree(mvc.perform(get(ROOT).param("year", "2026"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].developerId").value("b"))
            .andExpect(jsonPath("$.data.items[1].developerId").value("a"))
            .andExpect(jsonPath("$.data.items[2].developerId").value("z"))
            .andExpect(jsonPath("$.data.items[3].developerId").value("person"))
            .andExpect(jsonPath("$.data.items[4].developerId").value("late"))
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
