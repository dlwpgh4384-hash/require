package com.company.common.operatingprofitstatements.service;

import com.company.common.operatingprofitstatements.dto.OperatingProfitStatementContracts.*;
import com.company.common.profitstructures.mapper.ProfitStructureRows.Snapshot;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService.Month;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import static com.company.common.profitstructures.service.ProfitStructureRules.*;

public final class OperatingProfitStatementProjection {
    private OperatingProfitStatementProjection() {}
    private record Item(String label, String style, boolean cost, Function<Snapshot, BigDecimal> field) {}
    /** 시안 PL_ROWS 순서와 줄 모양: 굵게(bold)·회색 묶음(group)·들여쓰기(indent)·보통(plain). */
    private static final List<Item> ITEMS = List.of(
        new Item("매출액", "bold", false, Snapshot::revenue),
        new Item("매출원가", "group", true, Snapshot::costOfSales),
        new Item("배분 원가", "indent", true, Snapshot::allocatedCost),
        new Item("대기 원가", "indent", true, Snapshot::idleCost),
        new Item("직접 경비", "indent", true, Snapshot::directExpense),
        new Item("매출총이익", "bold", false, Snapshot::grossProfit),
        new Item("판관비", "plain", true, Snapshot::sga),
        new Item("입력 항목", "indent", true, Snapshot::sellingAdministrativeInput),
        new Item("간접 인건비", "indent", true, Snapshot::indirectLaborCost),
        new Item("연구개발비(내부)", "indent", true, Snapshot::internalResearchCost),
        new Item("전월 조정", "plain", false, Snapshot::previousAdjustment),
        new Item("영업이익", "bold", false, Snapshot::operatingProfit));

    public static BigDecimal change(BigDecimal current, BigDecimal prior) {
        return current == null || prior == null ? null : ratio(current.subtract(prior), prior.abs());
    }
    public static List<Month> window(Map<String, Month> source, int year, int end) {
        var result = new ArrayList<Month>();
        for (int m = 1; m <= end; m++) {
            var month = source.get(String.format("%04d%02d", year, m));
            if (month != null) result.add(month);
        }
        return result;
    }
    /**
     * 한 달 값. 시작한 달부터는 실시간 계산이라 자료가 없어도 0이다(전체 수익 구조와 같은 숫자).
     * 시작 전 이관 달은 올린 엑셀 값 그대로이고, 올리지 않은 달·빈 칸은 null(「–」)이다.
     */
    private static BigDecimal single(Month month, Function<Snapshot, BigDecimal> field) {
        return month == null ? null : field.apply(month.values());
    }
    /** 기간의 모든 달에 값이 있을 때만 더한다. 한 달이라도 없으면 null(「–」). */
    public static BigDecimal total(List<Month> months, int expected, Function<Snapshot, BigDecimal> field) {
        if (expected < 1 || months.size() != expected) return null;
        BigDecimal sum = BigDecimal.ZERO;
        for (var month : months) {
            var value = single(month, field);
            if (value == null) return null;
            sum = sum.add(value);
        }
        return sum;
    }
    /** firstYear: 자료가 시작되는 첫 해라 전년이 없다. 전년 누계·전년 대비 열을 두지 않는다(SFR-108-05, 시안). */
    public static Result of(Filter filter, Map<String, Month> source, boolean firstYear) {
        int year = filter.year(), end = Integer.parseInt(filter.baseMonth().substring(4));
        var current = window(source, year, end);
        var prior = window(source, year - 1, end);
        var previous = window(source, year, end - 1);
        var base = source.get(filter.baseMonth());
        boolean provisional = base != null && base.provisional();
        var revenue = metric(current, prior, previous, end, Snapshot::revenue);
        var gross = metric(current, prior, previous, end, Snapshot::grossProfit);
        var operating = metric(current, prior, previous, end, Snapshot::operatingProfit);
        String lastKey = com.company.common.profitstructures.service.ProfitStructurePeriod.key(
            java.time.YearMonth.of(year, end).minusMonths(1));
        var baseAmount = single(base, Snapshot::operatingProfit);
        var baseMetric = new Metric(baseAmount,
            change(baseAmount, single(source.get(String.format("%04d%02d", year - 1, end)), Snapshot::operatingProfit)),
            change(baseAmount, single(source.get(lastKey), Snapshot::operatingProfit)));
        var columns = new ArrayList<Column>();
        for (int m = 1; m <= 12; m++) {
            var month = source.get(String.format("%04d%02d", year, m));
            columns.add(new Column(m + "월", false, m <= end && month != null && month.provisional()));
        }
        columns.add(new Column("누계", provisional, false));
        if (!firstYear) {
            columns.add(new Column("전년 누계", false, false));
            columns.add(new Column("전년 대비", false, false));
        }
        var rows = new ArrayList<Line>();
        for (var item : ITEMS) {
            var values = new ArrayList<Cell>();
            for (int m = 1; m <= 12; m++) values.add(new Cell(m > end ? null :
                single(source.get(String.format("%04d%02d", year, m)), item.field()), "money"));
            var sum = total(current, end, item.field());
            values.add(new Cell(sum, "money"));
            if (!firstYear) {
                var old = total(prior, end, item.field());
                values.add(new Cell(old, "money"));
                values.add(new Cell(change(sum, old), "percent"));
            }
            rows.add(new Line(item.label(), item.style(), item.cost(), List.copyOf(values)));
        }
        var rates = new ArrayList<Cell>();
        for (int m = 1; m <= 12; m++) {
            var month = m > end ? null : source.get(String.format("%04d%02d", year, m));
            rates.add(new Cell(ratio(single(month, Snapshot::operatingProfit), single(month, Snapshot::revenue)), "percent"));
        }
        var rate = ratio(operating.amount(), revenue.amount());
        rates.add(new Cell(rate, "percent"));
        if (!firstYear) {
            var oldRate = ratio(total(prior, end, Snapshot::operatingProfit), total(prior, end, Snapshot::revenue));
            rates.add(new Cell(oldRate, "percent"));
            rates.add(new Cell(rate == null || oldRate == null ? null : rate.subtract(oldRate), "percentage-point"));
        }
        rows.add(new Line("영업이익률", "plain", false, List.copyOf(rates)));
        return new Result(year, filter.baseMonth(), provisional, provisional ? end
            + "월은 아직 마감 전이라 요약 칸과 누계는 잠정 숫자입니다. 마감하면 확정됩니다." : null,
            new Summary(revenue, gross, operating, rate, baseMetric), new NumericTable(List.copyOf(columns), List.copyOf(rows)));
    }
    private static Metric metric(List<Month> current, List<Month> prior, List<Month> previous,
        int end, Function<Snapshot, BigDecimal> field) {
        var amount = total(current, end, field);
        return new Metric(amount, change(amount, total(prior, end, field)),
            change(amount, total(previous, end - 1, field)));
    }
}
