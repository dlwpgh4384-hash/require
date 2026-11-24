package com.company.common.commonops.service;

import com.company.common.commonops.dto.BusinessPlanComparisonContracts.*;
import com.company.common.commonops.mapper.BusinessPlanComparisonRows.Target;
import com.company.common.profitstructures.mapper.ProfitStructureRows.Snapshot;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService;
import java.math.BigDecimal;
import java.util.*;
import static java.math.BigDecimal.ZERO;
import static com.company.common.profitstructures.service.ProfitStructureRules.*;

public final class BusinessPlanComparisonProjection {
    private BusinessPlanComparisonProjection() {}
    public static Result of(Filter filter, List<Target> targets,
        Map<String, ProfitStructureMonthlyProfitService.Month> source, boolean closed) {
        var indexed = new HashMap<String, Target>();
        for (var target : targets) indexed.put(target.yearMonth(), target);
        int end = Integer.parseInt(filter.baseMonth().substring(4));
        var months = new ArrayList<Month>();
        var cumulativeSource = new ArrayList<ProfitStructureMonthlyProfitService.Month>();
        BigDecimal revenueTarget = ZERO, profitTarget = ZERO;
        for (int m = 1; m <= 12; m++) {
            String ym = String.format("%04d%02d", filter.year(), m);
            var target = indexed.get(ym);
            BigDecimal rt = target == null ? ZERO : target.revenueTarget();
            BigDecimal pt = target == null ? ZERO : target.operatingProfitTarget();
            var actual = m <= end ? source.get(ym) : null;
            boolean available = actual != null && actual.available();
            BigDecimal revenue = available ? actual.values().revenue() : null;
            BigDecimal profit = available ? actual.values().operatingProfit() : null;
            BigDecimal achievement = target == null ? null : ratio(revenue, rt);
            BigDecimal actualMargin = ratio(profit, revenue);
            BigDecimal targetMargin = target == null ? null : ratio(pt, rt);
            // 이익률 차이는 반올림 전 값끼리 빼고 마지막에 소수 첫째 자리 반올림(식-36, 반올림한 두 값을 빼면 0.1 어긋날 수 있음).
            BigDecimal difference = actualMargin == null || targetMargin == null ? null
                : profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 10, java.math.RoundingMode.HALF_UP)
                    .subtract(pt.multiply(BigDecimal.valueOf(100)).divide(rt, 10, java.math.RoundingMode.HALF_UP))
                    .setScale(1, java.math.RoundingMode.HALF_UP);
            months.add(new Month(ym, rt, revenue, pt, profit, achievement, difference));
            if (m <= end) {
                revenueTarget = revenueTarget.add(rt);
                profitTarget = profitTarget.add(pt);
                if (actual != null) cumulativeSource.add(actual);
            }
        }
        BigDecimal revenue = completeSum(cumulativeSource, end, Snapshot::revenue);
        BigDecimal profit = completeSum(cumulativeSource, end, Snapshot::operatingProfit);
        boolean registered = !targets.isEmpty();
        var base = months.get(end - 1);
        var summary = new Summary(achievement(revenue, revenueTarget, registered), revenue,
            registered ? revenueTarget : null, achievement(profit, profitTarget, registered), profit,
            registered ? profitTarget : null, registered ? base.achievementRate() : null,
            registered ? base.revenueTarget() : null);
        return new Result(filter.year(), filter.baseMonth(), closed, registered, summary, List.copyOf(months));
    }
    private static BigDecimal achievement(BigDecimal actual, BigDecimal target, boolean registered) {
        return !registered || target.signum() <= 0 ? null : ratio(actual, target);
    }
}
