package com.company.common.profitstructures.service;

import com.company.common.profitstructures.dto.ProfitStructureContracts.*;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService.ProjectValue;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import static java.math.BigDecimal.ZERO;

/** 시안 UI-07 「숫자로 보기」 표: 줄 = 항목(rowsDef), 열 = 기간. 화면과 엑셀이 같은 표를 쓴다. */
public final class ProfitStructureTable {
    private ProfitStructureTable() {}
    private record Line(String label, String style, String format, Function<PeriodRow, BigDecimal> value) {}
    private record Group(String title, Function<ProjectValue, String> id, Function<ProjectValue, String> name, boolean topFive) {}
    private static final List<Line> LINES = List.of(
        new Line("매출", "bold", "money", r -> r.summary().revenue()),
        new Line("매출원가", "plain", "money", r -> r.waterfall().costOfSales()),
        new Line("└ 배분 원가", "indent", "money", r -> r.trend().allocatedCost()),
        new Line("└ 대기 원가", "indent", "money", r -> r.trend().idleCost()),
        new Line("└ 직접 경비", "indent", "money", r -> r.trend().directExpense()),
        new Line("매출총이익", "bold", "money", r -> r.summary().grossProfit()),
        new Line("매출총이익률", "plain", "percent", r -> r.summary().grossProfitRate()),
        new Line("판관비", "plain", "money", r -> r.waterfall().sellingAdministrativeExpense()),
        new Line("전월 조정", "plain", "money", r -> r.waterfall().previousAdjustment()),
        new Line("영업이익", "bold", "money", r -> r.summary().operatingProfit()),
        new Line("영업이익률", "plain", "percent", r -> r.summary().operatingProfitRate()),
        new Line("영업외손익", "plain", "money", r -> r.waterfall().nonOperatingProfitLoss()),
        new Line("법인세(월할)", "plain", "money", r -> r.waterfall().monthlyCorporateTax()),
        new Line("당기순이익", "bold", "money", r -> r.waterfall().netProfit()),
        new Line("대기 원가 비중", "plain", "percent", r -> r.summary().idleCostShare()));
    private static final List<Group> GROUPS = List.of(
        new Group("계약 유형별", v -> v.project().contractType(), v -> v.project().contractName(), false),
        new Group("고객사 상위 5곳", v -> v.project().clientId(), v -> v.project().clientName(), true),
        new Group("영업 파트별", v -> v.project().partId(), v -> v.project().partName(), false));

    /** projects: 기간 칸마다 그 기간 프로젝트 값(이관 섞인 칸은 null). */
    public static NumericTable of(List<PeriodRow> periods, List<List<ProjectValue>> projects, boolean compositionVisible) {
        var columns = periods.stream().map(p -> new NumericColumn(p.period(), p.provisional())).toList();
        List<NumericLine> rows = new ArrayList<>();
        for (var line : LINES) {
            List<BigDecimal> values = new ArrayList<>();
            for (var period : periods) values.add(line.value().apply(period));
            rows.add(new NumericLine(line.label(), line.style(), line.format(), values));
        }
        if (!compositionVisible || periods.isEmpty() || projects.get(projects.size() - 1) == null)
            return new NumericTable(columns, rows);
        for (var group : GROUPS) {
            rows.add(new NumericLine("매출 구성 · " + group.title(), "group", "money", Collections.nCopies(periods.size(), null)));
            // 줄 이름과 순서는 맨 오른쪽(고른) 기간의 매출 큰 순서, 다른 기간은 같은 묶음의 매출을 넣는다.
            var current = sums(projects.get(projects.size() - 1), group);
            var ranked = new ArrayList<>(current.keySet());
            ranked.sort(Comparator.comparing((String id) -> current.get(id)).reversed().thenComparing(id -> id));
            var shown = group.topFive() && ranked.size() > 5 ? ranked.subList(0, 5) : ranked;
            var names = names(projects.get(projects.size() - 1), group);
            for (var id : shown) {
                List<BigDecimal> values = new ArrayList<>();
                for (var bucket : projects) values.add(bucket == null ? null : sums(bucket, group).getOrDefault(id, ZERO));
                rows.add(new NumericLine(names.getOrDefault(id, "미지정"), "indent", "money", values));
            }
            if (shown.size() < ranked.size()) {
                List<BigDecimal> values = new ArrayList<>();
                for (var bucket : projects) {
                    if (bucket == null) { values.add(null); continue; }
                    var sums = sums(bucket, group);
                    BigDecimal rest = ZERO;
                    for (var entry : sums.entrySet()) if (!shown.contains(entry.getKey())) rest = rest.add(entry.getValue());
                    values.add(rest);
                }
                rows.add(new NumericLine(ProfitStructureRules.OTHER_PREFIX + (ranked.size() - shown.size()) + "곳", "indent", "money", values));
            }
        }
        return new NumericTable(columns, rows);
    }
    private static Map<String, BigDecimal> sums(List<ProjectValue> values, Group group) {
        var result = new HashMap<String, BigDecimal>();
        for (var value : values) result.merge(Objects.toString(group.id().apply(value), ""), value.revenue(), BigDecimal::add);
        return result;
    }
    private static Map<String, String> names(List<ProjectValue> values, Group group) {
        var result = new HashMap<String, String>();
        for (var value : values) result.putIfAbsent(Objects.toString(group.id().apply(value), ""), Objects.toString(group.name().apply(value), "미지정"));
        return result;
    }
}
