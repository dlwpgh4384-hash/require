package com.company.common.netprofitstatements;

import com.company.common.netprofitstatements.service.NetProfitStatementContributions;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Code;
import com.company.common.profitstructures.mapper.ProfitStructureRows.ProjectMonth;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService.ProjectValue;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NetProfitStatementContributionsTest {
    static final List<Code> CODES = List.of(new Code("CTRT_TYPE", "TK", "턴키", null),
        new Code("CTRT_TYPE", "MO", "월단위", null), new Code("CTRT_TYPE", "DP", "파견", null),
        new Code("CTRT_TYPE", "IN", "내부", null));
    static ProjectValue project(String code, int revenue, int allocated) {
        return new ProjectValue(new ProjectMonth("202601", code, code, "프로젝트 유형", null, null,
            null, null, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null),
            BigDecimal.valueOf(revenue), BigDecimal.valueOf(allocated));
    }
    @Test void mixedTypesUseGrossProfitExcludeInternalAndLossesFromDenominator() {
        var values = NetProfitStatementContributions.of(List.of(project("TK", 100, 70),
            project("MO", 100, 30), project("DP", 10, 20), project("IN", 999, 0)), CODES);
        assertThat(values).hasSize(3);
        assertThat(values.get(0).amount()).isEqualByComparingTo("30");
        assertThat(values.get(0).sharePercent()).isEqualByComparingTo("30.0");
        assertThat(values.get(1).sharePercent()).isEqualByComparingTo("70.0");
        assertThat(values.get(2).amount()).isEqualByComparingTo("-10");
        assertThat(values.get(2).sharePercent()).isNull();
        assertThat(values.get(0).contractTypeName()).isEqualTo("턴키");
    }
    @Test void thirdsSumToExactlyOneHundredWithCodeOrderTieBreaking() {
        var values = NetProfitStatementContributions.of(List.of(project("TK", 1, 0),
            project("MO", 1, 0), project("DP", 1, 0)), CODES);
        assertThat(values.stream().map(v -> v.sharePercent()).reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo("100.0");
        assertThat(values.get(2).sharePercent()).isEqualByComparingTo("33.4");
        assertThat(values.get(0).sharePercent()).isEqualByComparingTo("33.3");
    }
    @Test void missingExternalTypesHaveZeroWithoutInventedDisplayNames() {
        var codes = CODES.stream().map(c -> new Code(c.groupCode(), c.code(), c.name() + " DB", null)).toList();
        var values = NetProfitStatementContributions.of(List.of(project("TK", 10, 0)), codes);
        assertThat(values.get(1).amount()).isEqualByComparingTo("0");
        assertThat(values.get(1).sharePercent()).isEqualByComparingTo("0.0");
        assertThat(values.get(1).contractTypeName()).isEqualTo("월단위 DB");
    }
    @Test void emptyOperationalMonthKeepsThreeAmountsWithUndefinedShares() {
        var values = NetProfitStatementContributions.of(List.of(), CODES);
        assertThat(values).hasSize(3).allSatisfy(v -> {
            assertThat(v.amount()).isZero();
            assertThat(v.sharePercent()).isNull();
        });
    }
    @Test void allLossesRemainAmountsWithoutAnyShare() {
        assertThat(NetProfitStatementContributions.of(List.of(project("TK", 0, 5),
            project("MO", 0, 3), project("DP", 0, 1)), CODES)).allSatisfy(v -> {
                assertThat(v.amount()).isNegative();
                assertThat(v.sharePercent()).isNull();
            });
    }
    @Test void multipleProjectsOfSameTypeAreSummedBeforeShares() {
        var values = NetProfitStatementContributions.of(List.of(project("TK", 20, 10),
            project("TK", 10, 15), project("MO", 20, 5)), CODES);
        assertThat(values.get(0).amount()).isEqualByComparingTo("5");
        assertThat(values.get(0).sharePercent()).isEqualByComparingTo("25");
    }
}
