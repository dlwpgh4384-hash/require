package com.company.common.dashboard.service;

import com.company.common.dashboard.dto.DashboardContracts.*;
import com.company.common.partsettlements.service.CommonPartSettlementService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 대시보드 모양(명세 ⑧-4, SFR-116-08): 범위에 따라 칸·줄을 숨기고 파트장 요약은 자기 파트 숫자. */
class DashboardScopeShapeTest {
    static Todo todo(String key, String title, int count, String description, boolean always) {
        return new Todo(key, title, count, description, null, "UI-00", "화면", "warn", false, always, always || count > 0);
    }

    static Result full() {
        var summary = new Summary(new BigDecimal("1000"), BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("100"), BigDecimal.TEN,
            BigDecimal.ONE, new BigDecimal("500"), BigDecimal.TEN, new BigDecimal("80"), new BigDecimal("90"), 2,
            new BigDecimal("300"), 3, 1, new BigDecimal("50"));
        var todos = List.of(todo("overdueBills", "미수 연체 1건", 1, "x", false), todo("taxPayment", "법인세", 1, "x", false),
            todo("cashShortage", "자금", 1, "x", false), todo("manpowerShortage", "인력 부족 예상", 1, "11월 1.0명 부족", false),
            todo("withdrawal", "3개월 안 철수 예정 0명", 0, "다음 달 0명 · 다음 배정 없는 0명 중 0명은 추천 자리 있음", true),
            todo("billingMissing", "청구 누락", 1, "x", false), todo("pendingQuotes", "견적", 1, "x", false),
            todo("unassignedSalesPart", "미지정", 1, "x", false), todo("riskProjects", "위험", 1, "x", false));
        return new Result("2026-03", LocalDate.of(2026, 3, 10), "작업중", null, true, "오늘", null, null, summary, todos,
            new Closing(1, 2, List.of()), List.of(), "2026-03", List.of(), List.of(), List.of(new SalesPart("p", "P", BigDecimal.ONE, null)),
            new Pipeline(1, BigDecimal.ONE, List.of()), List.of(new CashMonth("2026-03", BigDecimal.ONE, false, null)), BigDecimal.TEN, "all");
    }

    static java.util.Set<String> keys(Result r) {
        return r.todos().stream().map(Todo::key).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("전체 모양은 그대로")
    void allKeepsEverything() {
        var shaped = DashboardScopeShape.shape("all", full(), YearMonth.of(2026, 3), null);
        assertThat(shaped).isEqualTo(full());
    }

    @Test
    @DisplayName("파트장 모양: 자기 파트 매출·이익·누계, 마감 진행·자금·법인세·미지정 줄 숨김, 인력 부족은 (회사 전체)")
    void partShape() {
        var parts = mock(CommonPartSettlementService.class);
        when(parts.performance(anyString())).thenAnswer(call -> List.of(new CommonPartSettlementService.Performance(
            "p", "P", new BigDecimal("200"), new BigDecimal("25.0"), new BigDecimal("50"))));
        var shaped = DashboardScopeShape.shape("part", full(), YearMonth.of(2026, 3), parts);
        assertThat(shaped.dashMode()).isEqualTo("part");
        assertThat(shaped.closing()).isNull();
        assertThat(shaped.cash()).isEmpty();
        assertThat(shaped.minimumCash()).isNull();
        assertThat(shaped.summary().revenue()).isEqualByComparingTo("200");
        assertThat(shaped.summary().operatingProfit()).isEqualByComparingTo("50");
        assertThat(shaped.summary().operatingProfitRate()).isEqualByComparingTo("25.0");
        assertThat(shaped.summary().cumulativeOperatingProfit()).isEqualByComparingTo("150");
        assertThat(shaped.summary().revenueTargetRate()).isNull();
        assertThat(shaped.summary().unpaidCount()).isEqualTo(3);
        assertThat(shaped.trend()).hasSize(12);
        assertThat(shaped.trend().get(2).cost()).isEqualByComparingTo("150");
        assertThat(shaped.trend().get(3).revenue()).isNull();
        assertThat(keys(shaped)).doesNotContain("taxPayment", "cashShortage", "unassignedSalesPart")
            .contains("overdueBills", "billingMissing", "pendingQuotes");
        var shortage = shaped.todos().stream().filter(t -> t.key().equals("manpowerShortage")).findFirst().orElseThrow();
        assertThat(shortage.title()).isEqualTo("인력 부족 예상 (회사 전체)");
        var withdrawal = shaped.todos().stream().filter(t -> t.key().equals("withdrawal")).findFirst().orElseThrow();
        assertThat(withdrawal.visible()).isFalse();
        assertThat(withdrawal.description()).isEqualTo("다음 달 0명");
        assertThat(shaped.salesParts()).hasSize(1);
        assertThat(shaped.pipeline()).isNotNull();
        verify(parts, times(3)).performance(anyString());
    }

    @Test
    @DisplayName("PM 모양: 요약·마감·추이·파트별·파이프라인·자금 숨김, 미수·청구·견적 줄 제외")
    void pmShape() {
        var shaped = DashboardScopeShape.shape("pm", full(), YearMonth.of(2026, 3), null);
        assertThat(shaped.dashMode()).isEqualTo("pm");
        assertThat(shaped.summary()).isNull();
        assertThat(shaped.closing()).isNull();
        assertThat(shaped.pipeline()).isNull();
        assertThat(shaped.trend()).isEmpty();
        assertThat(shaped.salesParts()).isEmpty();
        assertThat(shaped.cash()).isEmpty();
        assertThat(keys(shaped)).containsExactlyInAnyOrder("manpowerShortage", "withdrawal", "riskProjects");
        assertThat(DashboardExcelService.rows(shaped)).noneMatch(r -> r.get(0).equals("요약") || r.get(0).equals("마감")
            || r.get(0).equals("파이프라인") || r.get(0).equals("자금 전망"));
    }
}
