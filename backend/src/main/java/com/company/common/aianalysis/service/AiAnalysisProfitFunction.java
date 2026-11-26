package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.operatingprofitstatements.service.OperatingProfitStatementProjection;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.profitstructures.mapper.ProfitStructureRows.Snapshot;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService.Month;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import static com.company.common.aianalysis.service.AiAnalysisInterpreter.has;
import static com.company.common.aianalysis.service.AiAnalysisTables.*;
import static com.company.common.profitstructures.service.ProfitStructureRules.ratio;

/** F-01 회사 손익: 영업이익표·순이익표·전체 수익 구조와 같은 달 값(ProfitStructureMonthlyProfitService)과 같은 합산. */
@Component
public class AiAnalysisProfitFunction implements AiAnalysisFunction {
    static final String NOT_YET = "아직 오지 않은 달의 실적은 답할 수 없습니다. 예측을 물어보세요.";
    private final ProfitStructureMonthlyProfitService monthly;
    private final UserPreferencePeriodBasis periods;
    private final AiAnalysisTables tables;
    public AiAnalysisProfitFunction(ProfitStructureMonthlyProfitService monthly, UserPreferencePeriodBasis periods,
        AiAnalysisTables tables) {
        this.monthly = monthly; this.periods = periods; this.tables = tables;
    }
    public Set<String> functions() { return Set.of("F-01"); }
    private record Metric(String key, String label, String format, Function<List<Month>, BigDecimal> value) {}

    public Result read(AiAnalysisInterpreter.Query query) {
        if (!(periods.metadata().get("periodBasis") instanceof UserPreferencePeriodBasis.Basis basis)) throw new NoAnswer(null);
        var first = YearMonth.parse(query.period().startMonth());
        var end = YearMonth.parse(query.period().endMonth());
        // 「하반기」처럼 기간 끝이 아직 안 왔으면 조회할 수 있는 마지막 달까지만 더한다(시작부터 미래면 답할 수 없음).
        if (!first.equals(end) && key(end).compareTo(basis.latestYm()) > 0 && key(first).compareTo(basis.latestYm()) <= 0)
            end = month(basis.latestYm());
        if (key(end).compareTo(basis.latestYm()) > 0) throw new NoAnswer(NOT_YET, null, List.of("앞으로 6개월 매출은?"));
        String q = query.question();
        var asked = span(first, end);
        boolean compare = has(q, "전년", "작년 대비", "작년과");
        var source = new TreeMap<String, Month>();
        int from = Math.min(first.minusMonths(1).getYear(), compare ? first.getYear() - 1 : first.getYear());
        for (int year = from; year <= end.getYear(); year++)
            source.putAll(monthly.year(year, year == end.getYear() ? end.getMonthValue() : 12, basis.cashBaseYm()));
        var months = new ArrayList<Month>();
        for (var month = first; !month.isAfter(end); month = month.plusMonths(1)) {
            var value = source.get(key(month));
            // 자료가 없는 달(시작 전 이관하지 않은 달·자료 0건)은 0으로 답하지 않는다.
            if (value == null || !value.available() || value.values().revenue() == null) throw new NoAnswer(null);
            months.add(value);
        }
        var metrics = metrics(q);
        boolean several = months.size() > 1;
        var columns = new ArrayList<Column>(List.of(Column.month("month", "연월")));
        for (var metric : metrics) columns.add(metric.format().equals("rate") ? Column.rate(metric.key(), metric.label())
            : Column.money(metric.key(), metric.label()));
        if (compare) columns.add(Column.money("previousRevenue", "전년 매출"));
        var rows = new ArrayList<List<Object>>();
        for (int i = 0; i < months.size(); i++) {
            var row = new ArrayList<Object>(List.of(first.plusMonths(i).toString()));
            for (var metric : metrics) row.add(metric.value().apply(List.of(months.get(i))));
            if (compare) {
                var old = source.get(key(first.plusMonths(i).minusYears(1)));
                row.add(old == null || !old.available() ? null : old.values().revenue());
            }
            rows.add(row);
        }
        List<Object> total = null;
        if (several) {
            total = new ArrayList<>(List.of("합계"));
            for (var metric : metrics) total.add(metric.value().apply(months));
            if (compare) total.add(OperatingProfitStatementProjection.total(previousYear(source, first, end), months.size(), Snapshot::revenue));
        }
        var table = AiAnalysisTables.table(several ? "월별 손익" : null, columns, rows, null, total);
        boolean provisional = months.stream().anyMatch(Month::provisional);
        var parts = new ArrayList<String>();
        for (var metric : metrics) {
            var value = metric.value().apply(months);
            if (metric.key().equals("operatingRate") && metrics.stream().anyMatch(m -> m.key().equals("operatingProfit"))) continue;
            String text = metric.format().equals("rate") ? rate(value) : money(value);
            String extra = metric.key().equals("operatingProfit") && metrics.stream().anyMatch(m -> m.key().equals("operatingRate"))
                ? "(이익률 " + rate(ratio(sum(months, Snapshot::operatingProfit), sum(months, Snapshot::revenue))) + ")" : "";
            parts.add(metric.label() + "은 " + bold(text) + extra);
        }
        String label = several ? period(asked) + " 누계" : end.toString();
        var conclusion = new StringBuilder(label + " " + String.join(", ", parts) + "입니다.");
        if (!several) {
            var previous = source.get(key(end.minusMonths(1)));
            if (previous != null && previous.available()) {
                var change = OperatingProfitStatementProjection.change(months.get(0).values().revenue(), previous.values().revenue());
                if (change != null) conclusion.append(" 전월보다 매출이 ").append(change.signum() >= 0 ? "▲ " : "▼ ")
                    .append(change.abs().setScale(1, java.math.RoundingMode.HALF_UP).toPlainString()).append("%.");
            }
        }
        if (compare) {
            var prior = OperatingProfitStatementProjection.total(previousYear(source, first, end), months.size(), Snapshot::revenue);
            var change = OperatingProfitStatementProjection.change(sum(months, Snapshot::revenue), prior);
            if (change != null) conclusion.append(" 전년 같은 기간보다 매출이 ").append(change.signum() >= 0 ? "▲ " : "▼ ")
                .append(change.abs().setScale(1, java.math.RoundingMode.HALF_UP).toPlainString()).append("%.");
        }
        String screen = has(q, "순이익") ? "UI-09" : has(q, "매출총이익", "대기 원가", "수익 구조") ? "UI-07" : "UI-08";
        var chart = chart(source, end);
        var assumptions = new ArrayList<String>();
        assumptions.add("매출은 영업이익표 매출액(투입 기준, 청구 금액 아님), 영업이익은 영업이익표와 같은 숫자입니다.");
        if (has(q, "순이익")) assumptions.add("순이익은 순이익표와 같이 영업이익에 영업외 손익과 월할 법인세를 반영한 숫자입니다.");
        if (has(q, "대기 원가")) assumptions.add("대기 원가 비중은 전체 수익 구조와 같이 대기 원가 ÷ 매출원가입니다.");
        assumptions.add("마감 전 달은 잠정 값입니다.");
        var used = new Period(several ? first.toString() : YearMonth.of(end.getYear(), 1).toString(), end.toString());
        // 시안 매출 답(매출·영업이익만): 화면은 결론+그래프만, 표는 엑셀에만 쓴다.
        boolean chartOnly = !compare && metrics.stream().allMatch(m -> Set.of("revenue", "operatingProfit", "operatingRate").contains(m.key()));
        return new Result(conclusion.toString(), List.of(chartOnly ? table.hide() : table), chart, assumptions,
            used(tables.screen(screen), several ? asked : used), end.toString(), provisional, false,
            List.of(tables.open(screen, asked)));
    }
    private static List<Metric> metrics(String q) {
        var result = new ArrayList<Metric>();
        boolean structure = has(q, "수익 구조");
        boolean basic = has(q, "매출", "영업이익", "실적") && !has(q, "매출총이익") || structure
            || !has(q, "순이익", "매출총이익", "대기 원가", "이익률");
        if (basic || has(q, "매출총이익", "순이익률", "대기 원가")) result.add(new Metric("revenue", "매출", "money", m -> sum(m, Snapshot::revenue)));
        if (has(q, "매출총이익") || structure) {
            result.add(new Metric("grossProfit", "매출총이익", "money", m -> sum(m, Snapshot::grossProfit)));
            if (has(q, "매출총이익률")) result.add(new Metric("grossRate", "매출총이익률", "rate",
                m -> ratio(sum(m, Snapshot::grossProfit), sum(m, Snapshot::revenue))));
        }
        if (basic || has(q, "영업이익률") || has(q, "이익률") && !has(q, "순이익률", "매출총이익률")) {
            result.add(new Metric("operatingProfit", "영업이익", "money", m -> sum(m, Snapshot::operatingProfit)));
            result.add(new Metric("operatingRate", "영업이익률", "rate", m -> ratio(sum(m, Snapshot::operatingProfit), sum(m, Snapshot::revenue))));
        }
        if (has(q, "순이익")) {
            result.add(new Metric("netProfit", "순이익", "money", m -> sum(m, Snapshot::netProfit)));
            result.add(new Metric("netRate", "순이익률", "rate", m -> ratio(sum(m, Snapshot::netProfit), sum(m, Snapshot::revenue))));
        }
        if (has(q, "대기 원가") || structure) result.add(new Metric("idleCostShare", "대기 원가 비중", "rate",
            m -> ratio(sum(m, Snapshot::idleCost), sum(m, Snapshot::costOfSales))));
        return result;
    }
    /** 영업이익표 합산과 같다: 모든 달에 값이 있을 때만 더하고 하나라도 없으면 「–」. */
    static BigDecimal sum(List<Month> months, Function<Snapshot, BigDecimal> field) {
        return OperatingProfitStatementProjection.total(months, months.size(), field);
    }
    private static List<Month> previousYear(Map<String, Month> source, YearMonth first, YearMonth end) {
        var result = new ArrayList<Month>();
        for (var month = first.minusYears(1); !month.isAfter(end.minusYears(1)); month = month.plusMonths(1)) {
            var value = source.get(key(month));
            if (value != null && value.available()) result.add(value);
        }
        return result;
    }
    /** 시안 매출 답 그래프: 그 해 1월부터 기준월까지 매출·영업이익. */
    private static Chart chart(Map<String, Month> source, YearMonth end) {
        var labels = new ArrayList<String>();
        var revenue = new ArrayList<BigDecimal>();
        var operating = new ArrayList<BigDecimal>();
        for (var month = YearMonth.of(end.getYear(), 1); !month.isAfter(end); month = month.plusMonths(1)) {
            var value = source.get(key(month));
            boolean ok = value != null && value.available();
            labels.add(month.getMonthValue() + "월");
            revenue.add(ok ? value.values().revenue() : null);
            operating.add(ok ? value.values().operatingProfit() : null);
        }
        return new Chart("line", labels, List.of(new Series("매출", revenue), new Series("영업이익", operating)));
    }
}
