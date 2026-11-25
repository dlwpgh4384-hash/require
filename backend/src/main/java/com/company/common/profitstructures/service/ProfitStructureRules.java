package com.company.common.profitstructures.service;

import com.company.common.profitstructures.dto.ProfitStructureContracts.*;
import com.company.common.profitstructures.mapper.ProfitStructureRows.Snapshot;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService.Month;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService.ProjectValue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import static java.math.BigDecimal.ZERO;

public final class ProfitStructureRules {
    private ProfitStructureRules() {}
    public static final String OTHER_PREFIX = "그 밖 ";
    public static final String SALES_PART_NOTICE ="영업 파트 이익금은 사람 소속 파트 기준이라 이 숫자와 다를 수 있습니다";
    /** UI-22 integration projection only; neither development GET calls this policy. */
    public static boolean projectedRoleAllows(Set<String> roles) {
        return roles != null && roles.stream().anyMatch(Set.of("FIN", "EXEC", "ADMIN")::contains);
    }
    /** 비율(%)은 소수 첫째 자리 반올림(식-36 「소수 첫째 자리 반올림」·시안, 2026-11-24 사용자 결정). 금액 버림(SFR-006-03)과 다름. */
    public static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        return numerator == null || denominator == null || denominator.signum() == 0 ? null
            : numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 1, RoundingMode.HALF_UP);
    }
    public static BigDecimal sum(List<Month> months, Function<Snapshot, BigDecimal> field) {
        BigDecimal total = ZERO;
        for (var month : months) {
            var value = field.apply(month.values());
            if (value == null) return null;
            total = total.add(value);
        }
        return total;
    }
    public static BigDecimal completeSum(List<Month> months, int expected, Function<Snapshot, BigDecimal> field) {
        if (expected < 1 || months.size() != expected || months.stream().anyMatch(m -> !m.available())) return null;
        return sum(months, field);
    }
    public static Summary completeSummary(List<Month> months, int expected) {
        var revenue = completeSum(months, expected, Snapshot::revenue);
        var gross = completeSum(months, expected, Snapshot::grossProfit);
        var operating = completeSum(months, expected, Snapshot::operatingProfit);
        return new Summary(revenue, gross, ratio(gross, revenue), operating, ratio(operating, revenue),
            ratio(completeSum(months, expected, Snapshot::idleCost), completeSum(months, expected, Snapshot::costOfSales)));
    }
    public static Summary summary(List<Month> months) {
        var revenue = sum(months, Snapshot::revenue);
        var gross = sum(months, Snapshot::grossProfit);
        var operating = sum(months, Snapshot::operatingProfit);
        return new Summary(revenue, gross, ratio(gross, revenue), operating, ratio(operating, revenue),
            ratio(sum(months, Snapshot::idleCost), sum(months, Snapshot::costOfSales)));
    }
    public static Waterfall waterfall(List<Month> months) {
        var income = sum(months, Snapshot::income); var expense = sum(months, Snapshot::expense);
        return new Waterfall(sum(months, Snapshot::revenue), sum(months, Snapshot::costOfSales), sum(months, Snapshot::grossProfit),
            sum(months, Snapshot::sga), sum(months, Snapshot::previousAdjustment), sum(months, Snapshot::operatingProfit),
            income == null || expense == null ? null : income.subtract(expense), sum(months, Snapshot::tax), sum(months, Snapshot::netProfit));
    }
    public static PeriodRow project(ProfitStructurePeriod period, List<Month> months) {
        boolean migrated = months.stream().anyMatch(Month::migrated);
        BigDecimal reference = ZERO;
        for (var month : months) reference = reference == null || month.referenceRevenue() == null ? null : reference.add(month.referenceRevenue());
        var trend = new Trend(period.label(), sum(months, Snapshot::revenue), sum(months, Snapshot::costOfSales),
            sum(months, Snapshot::grossProfit), sum(months, Snapshot::operatingProfit), sum(months, Snapshot::allocatedCost),
            sum(months, Snapshot::idleCost), sum(months, Snapshot::directExpense), migrated ? null : reference);
        return new PeriodRow(period.label(), summary(months), waterfall(months), trend,
            migrated ? null : composition(months), !months.isEmpty() && months.get(months.size() - 1).provisional());
    }
    private record Group(String id, String name, BigDecimal revenue, BigDecimal cost) {}
    public static Composition composition(List<Month> months) {
        var values = months.stream().flatMap(month -> month.projects().stream()).toList();
        return new Composition(group(values, v -> v.project().contractType(), v -> v.project().contractName(), false),
            group(values, v -> v.project().clientId(), v -> v.project().clientName(), true),
            group(values, v -> v.project().partId(), v -> v.project().partName(), false), SALES_PART_NOTICE);
    }
    private static List<CompositionRow> group(List<ProjectValue> values, Function<ProjectValue, String> identity,
        Function<ProjectValue, String> name, boolean topFive) {
        var groups = new TreeMap<String, Group>();
        BigDecimal total = ZERO;
        for (var value : values) {
            String id = Objects.toString(identity.apply(value), "");
            Group old = groups.get(id);
            groups.put(id, new Group(id, Objects.toString(name.apply(value), "미지정"),
                value.revenue().add(old == null ? ZERO : old.revenue()), value.allocatedCost().add(old == null ? ZERO : old.cost())));
            total = total.add(value.revenue());
        }
        // 시안 top(): 세 묶음 모두 매출 큰 순서, 고객사는 6곳부터 「그 밖 N곳」으로 합친다.
        List<Group> ranked = new ArrayList<>(groups.values());
        ranked.sort(Comparator.comparing(Group::revenue).reversed().thenComparing(Group::id));
        if (topFive && ranked.size() > 5) {
            var rest = ranked.subList(5, ranked.size());
            var other = new Group("", OTHER_PREFIX + rest.size() + "곳", rest.stream().map(Group::revenue).reduce(ZERO, BigDecimal::add),
                rest.stream().map(Group::cost).reduce(ZERO, BigDecimal::add));
            ranked = new ArrayList<>(ranked.subList(0, 5)); ranked.add(other);
        }
        BigDecimal denominator = total;
        return ranked.stream().map(g -> new CompositionRow(g.name(), g.revenue(), ratio(g.revenue(), denominator),
            ratio(g.revenue().subtract(g.cost()), g.revenue()))).toList();
    }
}
