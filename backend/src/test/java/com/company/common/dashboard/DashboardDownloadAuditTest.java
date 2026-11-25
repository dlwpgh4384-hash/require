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
                assertThat(body).hasSize(21);
                return new byte[] { 80, 75 };
            });
        when(excel.fileName("대시보드", "2026년 10월", now)).thenReturn("대시보드_2026년 10월_test.xlsx");
        var service = new DashboardExcelService(source, mapper, excel, actor, calendar);
        var workbook = service.getCommonDashboardExcel("2026-10");
        assertThat(workbook.count()).isEqualTo(21);
        assertThat(workbook.bytes()).containsExactly((byte) 80, (byte) 75);
        var ordered = inOrder(excel, mapper);
        ordered.verify(excel).write(eq("대시보드"), anyList(), anyList(), eq("export-test-actor"), eq(now));
        ordered.verify(excel).fileName("대시보드", "2026년 10월", now);
        ordered.verify(mapper).insertAccess("export-test-actor", "대시보드 2026-10 21건");
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

    @Test @SuppressWarnings("unchecked")
    void cashRowsUseFixedItemAndPutWarningInMetric() {
        var source = mock(CommonDashboardService.class);
        var excel = mock(ExcelExportService.class);
        var actor = mock(CurrentBusinessActor.class);
        when(actor.userId()).thenReturn("export-test-actor");
        var json = JsonMapper.builder().findAndAddModules().build();
        Map<String, Object> base = json.convertValue(result(), Map.class);
        var withCash = new java.util.HashMap<>(base);
        var warned = new java.util.HashMap<String, Object>();
        warned.put("month", "2026-12"); warned.put("monthEndBalance", 800); warned.put("shortage", true);
        warned.put("warning", "최소 운영자금보다 부족할 것으로 예상됩니다.");
        var fine = new java.util.HashMap<String, Object>();
        fine.put("month", "2026-11"); fine.put("monthEndBalance", 1500); fine.put("shortage", false); fine.put("warning", null);
        withCash.put("cash", List.of(fine, warned));
        when(source.getCommonDashboard("2026-10"))
            .thenReturn(new Response<>(true, Map.of(), json.convertValue(withCash, DashboardContracts.Result.class)));
        var body = new java.util.ArrayList<List<String>>();
        when(excel.write(anyString(), anyList(), anyList(), anyString(), any())).thenAnswer(call -> {
            body.addAll((List<List<String>>) call.getArgument(2));
            return new byte[] { 80, 75 };
        });
        new DashboardExcelService(source, mock(DashboardMapper.class), excel, actor,
            new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-25T01:00:00Z"), ZoneOffset.UTC)))
            .getCommonDashboardExcel("2026-10");
        assertThat(body.stream().filter(r -> r.get(0).equals("자금 전망")).toList()).containsExactly(
            List.of("자금 전망", "회사 전체", "2026-11", "월말 잔액", "1500"),
            List.of("자금 전망", "회사 전체", "2026-12", "월말 잔액 · 최소 운영자금보다 부족할 것으로 예상됩니다.", "800"));
    }

    private static DashboardContracts.Result result() {
        return JsonMapper.builder().findAndAddModules().build().convertValue(Map.ofEntries(
            Map.entry("selectedMonth", "2026-10"), Map.entry("today", "2026-11-25"),
            Map.entry("summary", Map.of("revenue", 37)),
            Map.entry("todos", List.of(Map.of("key", "billingMissing", "title", "10월 청구 누락 의심 2건", "count", 2,
                    "description", "선택월 점검", "visible", true),
                Map.of("key", "preContract", "title", "미계약 선투입 0명", "count", 0, "description", "", "visible", false))),
            Map.entry("closing", Map.of("completedCount", 3, "totalCount", 4,
                "remainingItems", List.of(Map.of("name", "급여 확인", "description", "3명")))),
            Map.entry("projectAlerts", List.of()), Map.entry("trend", List.of()), Map.entry("manpower", List.of()),
            Map.entry("salesParts", List.of()), Map.entry("pipeline", Map.of("activeCount", 1, "upcoming", List.of())),
            Map.entry("cash", List.of())), DashboardContracts.Result.class);
    }
}
