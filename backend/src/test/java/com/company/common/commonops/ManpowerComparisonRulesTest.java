package com.company.common.commonops;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.manpowercomparisons.mapper.ManpowerComparisonRows.Source;
import com.company.common.manpowercomparisons.service.ManpowerComparisonRules;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManpowerComparisonRulesTest {
    CalculationRuleMapper definitions;
    ManpowerComparisonRules rules;

    @BeforeEach void setup() throws Exception {
        definitions = mock(CalculationRuleMapper.class);
        ManpowerComparisonDefinitions.load(definitions);
        rules = new ManpowerComparisonRules(new CalculationRuleService(definitions, mock(UserPreferencePeriodSourceMapper.class)));
        rules.requireDefinitions();
    }

    Source source(String start, String end, String plan, String actual) {
        return new Source("P", "프로젝트", "BE", "백엔드", LocalDate.parse(start), LocalDate.parse(end),
            new BigDecimal(plan), new BigDecimal(actual));
    }

    @Test void annualPlanCountsTouchedMonthsInclusivelyAndRoundsOnlyAfterDivision() {
        var row = rules.compare(source("2025-12-31", "2026-02-01", "1", "0"), 2026);
        assertThat(row.plannedMm()).isEqualByComparingTo(new BigDecimal("1").multiply(new BigDecimal("2"))
            .divide(new BigDecimal("3"), 2, java.math.RoundingMode.HALF_UP));
        assertThat(row.differenceStatus()).isEqualTo("부족");
    }

    @Test void sameMonthCountsOneAndHalfUpTieRoundsUp() {
        assertThat(rules.compare(source("2026-04-10", "2026-04-10", "1.23", "0"), 2026).plannedMm())
            .isEqualByComparingTo("1.23");
        assertThat(rules.compare(source("2025-12-31", "2026-01-01", "0.01", "0"), 2026).plannedMm())
            .isEqualByComparingTo("0.01");
    }

    @Test void equalityIsExactForPositiveNegativeTinyAndScaledZero() {
        for (String actual : new String[]{"1.00000001", "0.99999999", "1.0000"}) {
            var row = rules.compare(source("2026-01-01", "2026-12-31", "1", actual), 2026);
            BigDecimal expected = new BigDecimal(actual).subtract(BigDecimal.ONE);
            assertThat(row.differenceMm()).isEqualByComparingTo(expected);
            assertThat(row.differenceStatus()).isEqualTo(expected.signum() < 0 ? "부족" : expected.signum() > 0 ? "초과" : "맞음");
        }
    }

    @Test void explicitBothZeroIsMatchedAndMissingSideUsesZero() {
        assertThat(rules.compare(source("2026-01-01", "2026-12-31", "0", "0"), 2026).differenceStatus()).isEqualTo("맞음");
        assertThat(rules.compare(source("2026-01-01", "2026-12-31", "0", "0.01"), 2026).differenceStatus()).isEqualTo("초과");
        assertThat(rules.compare(source("2026-01-01", "2026-12-31", "0.01", "0"), 2026).differenceStatus()).isEqualTo("부족");
    }

    @Test void readsAllDefinitionsButCapacityThresholdsDoNotControlComparison() {
        for (String id : new String[]{"00", "01", "62", "67"}) verify(definitions).selectDefinition("식-" + id);
        assertThat(rules.compare(source("2026-01-01", "2026-12-31", "1", "0.99"), 2026).differenceStatus()).isEqualTo("부족");
    }

    @Test void unsupportedAnnualSemanticsFailInsteadOfReturningEmpty() {
        var unsupported = definitions.selectDefinition("식-67");
        unsupported.formula = "차이 = 반올림";
        assertThatThrownBy(rules::requireDefinitions).isInstanceOf(IllegalStateException.class);
    }

    @Test void whitespacePunctuationAndEquivalentWordingDoNotDisableComparison() {
        var month = definitions.selectDefinition("식-01");
        month.formula = month.formula.replace("소수 둘째 자리 반올림", "소수\n둘째\t자리로\u00a0반올림");
        var annual = definitions.selectDefinition("식-67");
        annual.formula = "비교 수식: 차이 = 실제·예정 MM - 계획 MM; 차이 < 0인 경우 부족; "
            + "> 0일 때 초과; = 0이면 맞음.";
        annual.details = annual.details.replace("×", "*").replace("÷", "/")
            .replace("소수 둘째 자리로", "소수 둘째 자리");
        assertThatCode(rules::requireDefinitions).doesNotThrowAnyException();
        assertThat(rules.compare(source("2026-01-01", "2026-12-31", "1", "0.99"), 2026)
            .differenceStatus()).isEqualTo("부족");
    }

    @ParameterizedTest
    @ValueSource(strings = {"계획 MM − 실제·예정 MM", "실제·예정 MM + 계획 MM"})
    void changedDifferenceArithmeticIsRejected(String expression) {
        var annual = definitions.selectDefinition("식-67");
        annual.formula = annual.formula.replace("실제·예정 MM − 계획 MM", expression);
        assertThatThrownBy(rules::requireDefinitions).isInstanceOf(IllegalStateException.class);
    }

    @Test void changedEqualityThresholdIsRejected() {
        var annual = definitions.selectDefinition("식-67");
        annual.formula = annual.formula.replace("= 0이면 맞음", "< 0.4이면 맞음");
        assertThatThrownBy(rules::requireDefinitions).isInstanceOf(IllegalStateException.class);
    }

    @Test void changedProrationIsRejected() {
        var annual = definitions.selectDefinition("식-67");
        annual.details = annual.details.replace("전체 프로젝트 개월 수", "12");
        assertThatThrownBy(rules::requireDefinitions).isInstanceOf(IllegalStateException.class);
    }

    @Test void changedAnnualRoundingIsRejected() {
        var annual = definitions.selectDefinition("식-67");
        annual.details = annual.details.replace("소수 둘째 자리로 반올림", "소수 첫째 자리로 반올림");
        assertThatThrownBy(rules::requireDefinitions).isInstanceOf(IllegalStateException.class);
    }

    @Test void missingDefinitionIsAnExplicitError() {
        when(definitions.selectDefinition("식-67")).thenReturn(null);
        assertThatThrownBy(rules::requireDefinitions)
            .isInstanceOf(com.company.common.support.service.CodeFailure.class);
    }

    @Test void emptyCapacityProvenanceIsNotTreatedAsValidData() {
        definitions.selectDefinition("식-62").formula = null;
        assertThatThrownBy(rules::requireDefinitions).isInstanceOf(IllegalStateException.class);
    }
}
