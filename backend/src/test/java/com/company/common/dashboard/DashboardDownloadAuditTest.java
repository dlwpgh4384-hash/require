package com.company.common.dashboard;

import com.company.common.dashboard.dto.DashboardContracts;
import com.company.common.dashboard.mapper.DashboardMapper;
import com.company.common.dashboard.service.CommonDashboardService;
import com.company.common.dashboard.service.DashboardExcelService;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.support.service.ExcelExportService;
import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DashboardDownloadAuditTest {
    @Test void successfulWorkbookPrecedesExactlyOneDashboardAuditAndCountMatchesBodyRows() {
        var source = mock(CommonDashboardService.class);
        var mapper = mock(DashboardMapper.class);
        var excel = mock(ExcelExportService.class);
        var actor = mock(CurrentBusinessActor.class);
        var now = Instant.parse("2026-11-25T01:00:00Z");
        var calendar = new BusinessCalendar(Clock.fixed(now, ZoneOffset.UTC));
        when(actor.userId()).thenReturn("export-test-actor");
        when(source.getCommonDashboard("2026-10")).thenReturn(new Response<>(true, Map.of(), result()));
        when(excel.write(eq("대시보드"), anyList(), anyList(), eq("export-test-actor"), eq(now)))
            .thenAnswer(call -> {
                List<?> body = call.getArgument(2);
                assertThat(body).hasSize(20);
                return new byte[] { 80, 75 };
            });
        when(excel.fileName("대시보드", "2026년 10월", now)).thenReturn("대시보드_2026년 10월_test.xlsx");
        var service = new DashboardExcelService(source, mapper, excel, actor, calendar);
        var workbook = service.getCommonDashboardExcel("2026-10");
        assertThat(workbook.count()).isEqualTo(20);
        assertThat(workbook.bytes()).containsExactly((byte) 80, (byte) 75);
        var ordered = inOrder(excel, mapper);
        ordered.verify(excel).write(eq("대시보드"), anyList(), anyList(), eq("export-test-actor"), eq(now));
        ordered.verify(excel).fileName("대시보드", "2026년 10월", now);
        ordered.verify(mapper).insertAccess("export-test-actor", "대시보드 2026-10 20건");
        verifyNoMoreInteractions(mapper);
    }

    @Test void workbookFailureDoesNotCreateSuccessfulDownloadAudit() {
        var source = mock(CommonDashboardService.class);
        var mapper = mock(DashboardMapper.class);
        var excel = mock(ExcelExportService.class);
        var actor = mock(CurrentBusinessActor.class);
        when(actor.userId()).thenReturn("export-failure-test");
        when(source.getCommonDashboard("2026-10")).thenReturn(new Response<>(true, Map.of(), result()));
        when(excel.write(anyString(), anyList(), anyList(), anyString(), any()))
            .thenThrow(new IllegalStateException("generation failure"));
        var service = new DashboardExcelService(source, mapper, excel, actor,
            new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-25T01:00:00Z"), ZoneOffset.UTC)));
        assertThatThrownBy(() -> service.getCommonDashboardExcel("2026-10")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(mapper);
    }

    private static DashboardContracts.Result result() {
        return JsonMapper.builder().findAndAddModules().build().convertValue(Map.ofEntries(
            Map.entry("selectedMonth", "2026-10"), Map.entry("today", "2026-11-25"),
            Map.entry("summary", Map.of("revenue", 37)),
            Map.entry("todos", List.of(Map.of("title", "청구 누락 의심", "count", 2, "description", "선택월 점검"))),
            Map.entry("closing", Map.of("completedCount", 3, "totalCount", 4, "remainingItems", List.of("급여 확인"))),
            Map.entry("projectAlerts", List.of()), Map.entry("trend", List.of()), Map.entry("manpower", List.of()),
            Map.entry("salesParts", List.of()), Map.entry("pipeline", Map.of("activeCount", 1, "upcoming", List.of())),
            Map.entry("cash", List.of())), DashboardContracts.Result.class);
    }
}
