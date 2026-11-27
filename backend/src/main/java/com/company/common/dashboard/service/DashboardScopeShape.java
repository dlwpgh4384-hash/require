package com.company.common.dashboard.service;

import com.company.common.dashboard.dto.DashboardContracts.*;
import com.company.common.partsettlements.service.CommonPartSettlementService;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 대시보드 모양(명세 ⑧-4, SFR-116-08, 시안 dashMode). 숫자는 원래 화면 서비스가 데이터 범위로 거른 값이다.
 * <ul>
 * <li>all: 그대로.</li>
 * <li>part(담당 파트 역할): 요약·추이는 자기 파트(영업 파트 이익금 계산), 마감 진행·자금·법인세·영업 파트 미지정 줄 숨김.</li>
 * <li>pm(담당 프로젝트만): 요약 5칸·마감 진행·추이·하단 3칸(파트별·파이프라인·자금) 숨김, 미수·청구·견적 줄 제외.</li>
 * </ul>
 * 인력 수급·인력 부족은 누가 보든 회사 전체 숫자라 part·pm에서는 제목에 「(회사 전체)」를 붙인다(SFR-116-06).
 * 숨긴 칸은 응답에서 null(목록은 빈 목록)이다.
 */
final class DashboardScopeShape {
    static final String COMPANY = " (회사 전체)";
    private static final Set<String> HIDDEN_PART = Set.of("taxPayment", "cashShortage", "unassignedSalesPart");
    private static final Set<String> HIDDEN_PM = Set.of("taxPayment", "cashShortage", "unassignedSalesPart",
        "overdueBills", "billingMissing", "pendingQuotes");

    private DashboardScopeShape() {}

    static Result shape(String mode, Result r, YearMonth month, CommonPartSettlementService parts) {
        if (!"part".equals(mode) && !"pm".equals(mode)) return copy(r, "all", r.summary(), r.todos(), r.closing(),
            r.trend(), r.salesParts(), r.pipeline(), r.cash(), r.minimumCash());
        boolean part = "part".equals(mode);
        Summary summary = null;
        List<TrendMonth> trend = List.of();
        if (part) {
            var own = ownPart(month, parts);
            var s = r.summary();
            BigDecimal revenue = own.revenue().get(month.getMonthValue() - 1);
            BigDecimal profit = own.profit().get(month.getMonthValue() - 1);
            BigDecimal cumulative = BigDecimal.ZERO;
            for (int i = 0; i < month.getMonthValue(); i++) cumulative = cumulative.add(own.profit().get(i));
            // 파트별 사업계획 목표·전월 대비는 없어 비운다(시안 partKp).
            summary = new Summary(revenue, null, null, profit, rate(profit, revenue), null, cumulative, null,
                s == null ? null : s.utilizationRate(), s == null ? null : s.utilizationTargetRate(),
                s == null ? 0 : s.idlePeople(), s == null ? BigDecimal.ZERO : s.unpaidAmount(),
                s == null ? 0 : s.unpaidCount(), s == null ? 0 : s.overdueCount(), s == null ? BigDecimal.ZERO : s.overdueAmount());
            var months = new ArrayList<TrendMonth>();
            for (int i = 0; i < 12; i++) {
                boolean shown = i < month.getMonthValue();
                months.add(new TrendMonth(YearMonth.of(month.getYear(), i + 1).toString(),
                    shown ? own.revenue().get(i) : null, shown ? own.revenue().get(i).subtract(own.profit().get(i)) : null, null));
            }
            trend = List.copyOf(months);
        }
        var hidden = part ? HIDDEN_PART : HIDDEN_PM;
        var todos = r.todos().stream().filter(t -> !hidden.contains(t.key())).map(DashboardScopeShape::scoped).toList();
        return copy(r, mode, summary, todos, null, trend, part ? r.salesParts() : List.of(), part ? r.pipeline() : null,
            List.of(), null);
    }

    /** 인력 부족은 「(회사 전체)」, 철수 예정은 0명이면 숨기고 설명은 「다음 달 n명」만(시안 FULL||ending). */
    private static Todo scoped(Todo t) {
        if ("manpowerShortage".equals(t.key())) return new Todo(t.key(), t.title() + COMPANY, t.count(), t.description(),
            t.amount(), t.targetScreenId(), t.targetScreenName(), t.tone(), t.urgent(), t.alwaysVisible(), t.visible());
        if ("withdrawal".equals(t.key())) {
            String description = t.description() == null ? null : t.description().split(" · ")[0];
            return new Todo(t.key(), t.title(), t.count(), description, t.amount(), t.targetScreenId(), t.targetScreenName(),
                t.tone(), t.urgent(), false, t.count() > 0);
        }
        return t;
    }

    record Own(List<BigDecimal> revenue, List<BigDecimal> profit) {}

    /** 자기 파트(범위 안 파트만 돌려주는 영업 파트 이익금 계산)의 1월~고른 달 매출·이익. */
    static Own ownPart(YearMonth month, CommonPartSettlementService parts) {
        var revenue = new ArrayList<BigDecimal>();
        var profit = new ArrayList<BigDecimal>();
        for (int i = 1; i <= 12; i++) {
            BigDecimal r = BigDecimal.ZERO, p = BigDecimal.ZERO;
            if (parts != null && i <= month.getMonthValue()) {
                try {
                    for (var row : parts.performance(YearMonth.of(month.getYear(), i).toString())) {
                        if (row.revenue() != null) r = r.add(row.revenue());
                        if (row.profit() != null) p = p.add(row.profit());
                    }
                } catch (CodeFailure unavailable) { /* 계산할 수 없는 달은 0(정산 기준 없음) */ }
            }
            revenue.add(r);
            profit.add(p);
        }
        return new Own(List.copyOf(revenue), List.copyOf(profit));
    }

    private static BigDecimal rate(BigDecimal profit, BigDecimal revenue) {
        return revenue == null || revenue.signum() == 0 ? null
            : profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 1, RoundingMode.HALF_UP);
    }

    private static Result copy(Result r, String mode, Summary summary, List<Todo> todos, Closing closing,
        List<TrendMonth> trend, List<SalesPart> salesParts, Pipeline pipeline, List<CashMonth> cash, BigDecimal minimumCash) {
        return new Result(r.selectedMonth(), r.today(), r.closingState(), r.lastClosedMonth(), r.provisional(), r.asOfText(),
            r.provisionalNotice(), r.mixedBasisNotice(), summary, todos, closing, r.projectAlerts(), r.projectAlertMonth(),
            trend, r.manpower(), salesParts, pipeline, cash, minimumCash, mode);
    }
}
