package com.company.common.common;

import com.company.common.operatingprofitstatements.dto.OperatingProfitStatementContracts.*;
import com.company.common.operatingprofitstatements.service.OperatingProfitStatementProjection;
import com.company.common.profitstructures.mapper.ProfitStructureRows.Snapshot;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService.Month;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NetProfitStatementProjectionTest {
    private static BigDecimal n(String value) { return new BigDecimal(value); }

    private static Month month(String key, BigDecimal tax, BigDecimal net, boolean provisional) {
        var snapshot = new Snapshot(key, n("1000"), n("100"), n("20"), n("30"), n("150"),
            n("850"), n("200"), n("-10"), n("640"), n("50"), n("20"), tax, net,
            "N", provisional ? "N" : "Y", null, n("150"), n("30"), n("20"));
        return new Month(snapshot, List.of(), null, true, false, provisional);
    }

    private static Line row(NumericTable table, String label) {
        return table.rows().stream().filter(line -> line.label().equals(label)).findFirst().orElseThrow();
    }

    @Test void appendsFiveRowsUsingSharedSnapshotAmountsAndSameOperatingRows() {
        var source = Map.of("202601", month("202601", n("10"), n("123"), false),
            "202501", month("202501", n("8"), n("100"), false));
        var filter = new Filter(2026, "202601");
        var original = OperatingProfitStatementProjection.of(filter, source, false);
        var table = OperatingProfitStatementProjection.netProfitTable(filter, source, false);
        assertThat(table.columns()).isEqualTo(original.numericTable().columns());
        assertThat(table.rows().subList(0, 13)).isEqualTo(original.numericTable().rows());
        assertThat(table.rows()).extracting(Line::label).endsWith(
            "영업외수익", "영업외비용", "법인세(월할)", "당기순이익", "순이익률");
        assertThat(table.rows()).hasSize(18).allSatisfy(line -> assertThat(line.values()).hasSize(15));
        assertThat(row(table, "영업외수익").values().get(0)).isEqualTo(new Cell(n("50"), "money"));
        assertThat(row(table, "영업외비용").isCost()).isTrue();
        assertThat(row(table, "법인세(월할)").isCost()).isTrue();
        var net = row(table, "당기순이익");
        assertThat(net.style()).isEqualTo("bold");
        assertThat(net.isCost()).isFalse();
        // The snapshot deliberately differs from live finance arithmetic; it must not be recalculated.
        assertThat(net.values().get(0).value()).isEqualByComparingTo("123");
        assertThat(net.values().get(12).value()).isEqualByComparingTo("123");
        assertThat(net.values().get(13).value()).isEqualByComparingTo("100");
        assertThat(net.values().get(14)).isEqualTo(new Cell(n("23.0"), "percent"));
        var rate = row(table, "순이익률");
        assertThat(rate.values().get(12)).isEqualTo(new Cell(n("12.3"), "percent"));
        assertThat(rate.values().get(14)).isEqualTo(new Cell(n("2.3"), "percentage-point"));
        assertThat(OperatingProfitStatementProjection.of(filter, source, false)).isEqualTo(original);
    }

    @Test void ui08DefaultRetainsAllAmountsStylesAndSummary() {
        var source = Map.of("202601", month("202601", n("10"), n("123"), true),
            "202501", month("202501", n("8"), n("100"), false));
        var report = OperatingProfitStatementProjection.of(new Filter(2026, "202601"), source, false);
        assertThat(report.numericTable().rows()).extracting(Line::label).containsExactly(
            "매출액", "매출원가", "배분 원가", "대기 원가", "직접 경비", "매출총이익", "판관비", "입력 항목",
            "간접 인건비", "연구개발비(내부)", "전월 조정", "영업이익", "영업이익률");
        assertThat(report.numericTable().rows()).extracting(Line::style).containsExactly(
            "bold", "group", "indent", "indent", "indent", "bold", "plain", "indent", "indent", "indent",
            "plain", "bold", "plain");
        var expected = List.of("1000", "150", "100", "20", "30", "850", "200", "150", "30", "20", "-10", "640", "64.0");
        for (int i = 0; i < expected.size(); i++) {
            var values = report.numericTable().rows().get(i).values();
            assertThat(values.get(0).value()).isEqualByComparingTo(expected.get(i));
            assertThat(values.get(12).value()).isEqualByComparingTo(expected.get(i));
            assertThat(values.get(13).value()).isEqualByComparingTo(expected.get(i));
            assertThat(values.get(14).value()).isEqualByComparingTo("0");
            assertThat(values.subList(1, 12)).allSatisfy(cell -> assertThat(cell.value()).isNull());
        }
        assertThat(report.summary().cumulativeRevenue().amount()).isEqualByComparingTo("1000");
        assertThat(report.summary().cumulativeGrossProfit().amount()).isEqualByComparingTo("850");
        assertThat(report.summary().cumulativeOperatingProfit().amount()).isEqualByComparingTo("640");
        assertThat(report.summary().operatingProfitRate()).isEqualByComparingTo("64.0");
        assertThat(report.summary().baseMonthOperatingProfit().previousMonthChangePercent()).isNull();
        assertThat(report.provisionalNotice()).isEqualTo(
            "1월은 아직 마감 전이라 요약 칸과 누계는 잠정 숫자입니다. 마감하면 확정됩니다.");
    }

    @Test void firstYearOmitsBothComparisonColumnsForEveryOldAndNewRow() {
        var filter = new Filter(2025, "202501");
        var source = Map.of("202501", month("202501", n("10"), n("123"), false));
        var table = OperatingProfitStatementProjection.netProfitTable(filter, source, true);
        assertThat(table.columns()).hasSize(13).extracting(Column::label).doesNotContain("전년 누계", "전년 대비");
        assertThat(table.rows()).allSatisfy(line -> assertThat(line.values()).hasSize(13));
        assertThat(OperatingProfitStatementProjection.of(filter, source, true).numericTable().columns())
            .isEqualTo(table.columns());
        assertThat(OperatingProfitStatementProjection.netProfitTable(filter, source, false).columns()).hasSize(15);
    }

    @Test void incompletePriorTaxDoesNotZeroFillOrSuppressCompleteIncomeComparison() {
        var source = new TreeMap<String, Month>();
        source.put("202601", month("202601", n("10"), n("123"), false));
        source.put("202602", month("202602", n("10"), n("123"), true));
        source.put("202501", month("202501", n("8"), n("100"), false));
        source.put("202502", month("202502", null, n("100"), false));
        var table = OperatingProfitStatementProjection.netProfitTable(new Filter(2026, "202602"), source, false);
        var tax = row(table, "법인세(월할)").values();
        assertThat(tax.get(12).value()).isEqualByComparingTo("20");
        assertThat(tax.get(13).value()).isNull();
        assertThat(tax.get(14).value()).isNull();
        assertThat(row(table, "영업외수익").values().get(13).value()).isEqualByComparingTo("100");
        assertThat(row(table, "영업외수익").values().get(14).value()).isEqualByComparingTo("0");
        assertThat(table.columns().get(1).heading()).isEqualTo("2월 작업중");
        assertThat(table.columns().get(12).heading()).isEqualTo("누계 잠정");
    }

    @Test void missingMonthIsNotZeroAndExplicitZeroRemainsNumeric() {
        var source = Map.of("202601", month("202601", BigDecimal.ZERO, BigDecimal.ZERO, false));
        var table = OperatingProfitStatementProjection.netProfitTable(new Filter(2026, "202602"), source, false);
        var net = row(table, "당기순이익").values();
        assertThat(net.get(0).value()).isEqualByComparingTo("0");
        assertThat(net.get(1).value()).isNull();
        assertThat(net.get(12).value()).isNull();
        assertThat(row(table, "순이익률").values().get(0).value()).isEqualByComparingTo("0");
        assertThat(row(table, "순이익률").values().get(12).value()).isNull();
    }

    @Test void fixedMissingTaxAndNetProfitAreNeverRecomputedOrChanged() {
        var fixed = month("202601", null, null, false);
        var source = Map.of("202601", fixed);
        var before = fixed.values();
        var table = OperatingProfitStatementProjection.netProfitTable(new Filter(2026, "202601"), source, false);
        for (var label : List.of("법인세(월할)", "당기순이익", "순이익률")) {
            assertThat(row(table, label).values().get(0).value()).isNull();
            assertThat(row(table, label).values().get(12).value()).isNull();
        }
        assertThat(table.columns()).noneMatch(Column::provisional).noneMatch(Column::working);
        assertThat(fixed.values()).isEqualTo(before);
    }
}
