package com.company.common.withdrawals.service;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.mapper.CalculationRuleRow;
import com.company.common.calculation.mapper.CalculationStandardRow;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.withdrawals.service.WithdrawalCalculationRules.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WithdrawalCalculationRulesTest {
    private final CalculationRuleMapper mapper = mock(CalculationRuleMapper.class);
    private final UserPreferencePeriodSourceMapper periods = mock(UserPreferencePeriodSourceMapper.class);
    private final CalculationRuleService definitions = new CalculationRuleService(mapper, periods);
    private final WithdrawalCalculationRules rules = new WithdrawalCalculationRules(definitions);

    static CalculationRuleRow original(String id) throws Exception {
        String seed = new ClassPathResource("db/migration/V8__calculation_rule_seed.sql")
            .getContentAsString(StandardCharsets.UTF_8);
        var tuple = Pattern.compile("\\('" + Pattern.quote(id)
            + "', (?:NULL|'[^']*'), '[^']*', '((?:[^']|'')*)', '((?:[^']|'')*)',")
            .matcher(seed);
        assertThat(tuple.find()).as(id).isTrue();
        var row = new CalculationRuleRow();
        row.calcId = id;
        row.formula = tuple.group(1).replace("''", "'");
        row.details = tuple.group(2).replace("''", "'");
        return row;
    }

    private CalculationRuleRow use(String id) throws Exception {
        when(mapper.selectDefinition("식-00")).thenReturn(original("식-00"));
        var row = original(id);
        when(mapper.selectDefinition(id)).thenReturn(row);
        return row;
    }

    private ScoreInput score(int gap) {
        return new ScoreInput(true, false, 1, gap, false, new BigDecimal("60"));
    }

    @Test void originalScoreUsesOnlyThePerformedDefinitionAndCommonPrinciple() throws Exception {
        use("식-80");
        assertThat(rules.aiRecommendScore(score(0))).isEqualByComparingTo("80");
        assertThat(rules.aiRecommendScore(score(2))).isEqualByComparingTo("63");
        verify(mapper, times(2)).selectDefinition("식-00");
        verify(mapper, times(2)).selectDefinition("식-80");
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(periods);
    }

    @Test void supportedWeightAndProbabilityChangesChangeScoreWithoutSeedEqualityGate() throws Exception {
        var row = use("식-80");
        row.formula = row.formula.replace("일치 40", "일치 32")
            .replace("1개월 15", "1개월 17").replace("60% 이상 5", "70% 이상 9");
        assertThat(rules.aiRecommendScore(score(1))).isEqualByComparingTo("59");
        assertThat(rules.aiRecommendScore(new ScoreInput(true, false, 1, 1, false,
            new BigDecimal("70")))).isEqualByComparingTo("68");
        assertThat(row.formula).contains("일치 32", "70% 이상 9");
    }

    // 식 글자 전체를 비교하지 않는다(2026-11-18 결정, 투입 배정과 같은 방식): 더 붙은 글자가 있어도 화면을 막지 않고
    // 읽을 수 있는 숫자로 계산한다. 숫자를 못 읽으면 명세 숫자로 계산한다.
    @Test void extraWordingDoesNotBlockTheScoreAndReadableWeightsStillApply() throws Exception {
        var row = use("식-80");
        row.formula += " + 스킬 30";
        assertThat(rules.aiRecommendScore(score(0))).isEqualByComparingTo("80");
        row.formula = row.formula.replace("한 단계 차이 10", "한 단계 차이 12");
        assertThat(rules.aiRecommendScore(score(0))).isEqualByComparingTo("82");
    }

    @Test void unreadableDefinitionsFallBackToSpecNumbersInsteadOfFailing() throws Exception {
        for (String id : List.of("식-77", "식-79", "식-80", "식-81", "식-82", "식-60")) {
            var row = use(id);
            row.formula = "외부 AI 추천 Open API 결과를 쓴다";
        }
        LocalDate end = LocalDate.of(2026, 11, 20);
        assertThat(rules.aiRecommendScore(score(0))).isEqualByComparingTo("80");
        assertThat(rules.nearTermMonthOffset()).isEqualTo(2);
        assertThat(rules.candidateWindowMonths()).isEqualTo(3);
        assertThat(rules.waitingDays(end, YearMonth.of(2026, 12))).isEqualTo(10);
        assertThat(rules.bannerCandidate(end, LocalDate.of(2026, 11, 1), null, false)).isTrue();
        assertThat(rules.supply(List.of(new SupplyPerson(new BigDecimal("0.25"), List.of())),
            YearMonth.of(2026, 12))).isEqualByComparingTo("0.8");
    }

    @Test void oneSettingsReadsEachDefinitionOnceForAWholeList() throws Exception {
        use("식-77");
        use("식-79");
        var settings = rules.settings();
        LocalDate today = LocalDate.of(2026, 11, 1);
        for (int day = 1; day <= 30; day++) {
            settings.nearTerm(LocalDate.of(2026, 11, day), today);
            settings.candidatePeriod(LocalDate.of(2026, 11, day), YearMonth.of(2026, 12), 3);
        }
        verify(mapper, times(1)).selectDefinition("식-00");
        verify(mapper, times(1)).selectDefinition("식-77");
        verify(mapper, times(1)).selectDefinition("식-79");
    }

    @Test void scoreOverHundredIsCappedNotThrown() throws Exception {
        var row = use("식-80");
        row.formula = row.formula.replace("일치 40", "일치 90");
        assertThat(rules.aiRecommendScore(new ScoreInput(true, false, 0, 0, true, new BigDecimal("90"))))
            .isEqualByComparingTo("100");
    }

    @Test void unusedFormulaIsNeverReadWhenComputingWaitingDays() throws Exception {
        use("식-81");
        assertThat(rules.waitingDays(LocalDate.of(2026, 11, 20), YearMonth.of(2026, 12)))
            .isEqualTo(10);
        assertThat(rules.waitingDays(LocalDate.of(2026, 11, 30), YearMonth.of(2026, 12)))
            .isZero();
        assertThat(rules.waitingDays(LocalDate.of(2026, 11, 30), YearMonth.of(2026, 11)))
            .isZero();
        verify(mapper, never()).selectDefinition("식-80");
        verify(mapper, never()).selectDefinition("식-60");
    }

    @Test void changedWaitingOffsetIsConsumed() throws Exception {
        var row = use("식-81");
        row.formula = row.formula.replace("− 1", "− 2");
        assertThat(rules.waitingDays(LocalDate.of(2026, 11, 20), YearMonth.of(2026, 12)))
            .isEqualTo(9);
    }

    @Test void nearTermUsesKoreanTodayAndDbWindowIncludingEndOfMonth() throws Exception {
        var row = use("식-77");
        var calendar = new BusinessCalendar(Clock.fixed(Instant.parse("2026-01-31T15:00:00Z"), ZoneOffset.UTC));
        LocalDate today = calendar.today();
        assertThat(rules.remainingDays(today, today)).isZero();
        assertThat(rules.nearTerm(LocalDate.of(2026, 4, 30), today)).isTrue();
        assertThat(rules.nearTerm(LocalDate.of(2026, 5, 1), today)).isFalse();
        assertThat(rules.nearTerm(today.minusDays(1), today)).isFalse();
        row.formula = row.formula.replace("+ 2개월", "+ 3개월");
        assertThat(rules.nearTerm(LocalDate.of(2026, 5, 1), today)).isTrue();
    }

    @Test void candidateWindowUsesDurationAndIncludesGapThree() throws Exception {
        var row = use("식-79");
        LocalDate end = LocalDate.of(2026, 11, 20);
        assertThat(rules.candidatePeriod(end, YearMonth.of(2027, 3), 1)).isTrue();
        assertThat(rules.candidatePeriod(end, YearMonth.of(2027, 4), 1)).isFalse();
        assertThat(rules.candidatePeriod(end, YearMonth.of(2026, 10), 3)).isTrue();
        assertThat(rules.candidatePeriod(end, YearMonth.of(2026, 10), 2)).isFalse();
        assertThat(rules.tentativeStartMonth(end, YearMonth.of(2026, 10)))
            .isEqualTo(YearMonth.of(2026, 12));
        row.formula = row.formula.replace("3개월", "4개월").replace("0~3", "0~4");
        assertThat(rules.candidatePeriod(end, YearMonth.of(2027, 4), 1)).isTrue();
    }

    @Test void gradeUnknownAddsNoGradePointsAndRelatedJobUsesDifferentWeight() throws Exception {
        use("식-80");
        assertThat(rules.aiRecommendScore(new ScoreInput(false, true, null, 3, true,
            new BigDecimal("59.99")))).isEqualByComparingTo("33");
    }

    @Test void bannerExcludesHighAndOutstandingTentatives() throws Exception {
        use("식-82");
        use("식-77");
        LocalDate today = LocalDate.of(2026, 11, 1), end = LocalDate.of(2026, 12, 31);
        assertThat(rules.bannerCandidate(end, today, null, false)).isTrue();
        assertThat(rules.bannerCandidate(end, today, "H", false)).isFalse();
        assertThat(rules.bannerCandidate(end, today, "M", true)).isFalse();
        assertThat(rules.bannerCandidate(today.minusDays(1), today, "L", false)).isFalse();
    }

    @Test void supplyUsesEffectiveExtensionStandardAndRoundsSumNotPeople() throws Exception {
        var row = use("식-60");
        var standard = new CalculationStandardRow();
        standard.standardCode = "EXT_PRBL_NULL";
        standard.effectiveMonth = "202601";
        standard.value = new BigDecimal("33");
        when(mapper.selectEffectiveStandard("EXT_PRBL_NULL", "202612")).thenReturn(standard);
        var people = List.of(
            new SupplyPerson(new BigDecimal("0.96"), List.of()),
            new SupplyPerson(new BigDecimal("0.96"), List.of()),
            new SupplyPerson(BigDecimal.ZERO, List.of(new Extension(BigDecimal.ONE, null))));
        assertThat(rules.supply(people, YearMonth.of(2026, 12))).isEqualByComparingTo("0.8");
        row.formula = row.formula.replace("첫째", "둘째");
        assertThat(rules.supply(people, YearMonth.of(2026, 12))).isEqualByComparingTo("0.75");
        standard.value = new BigDecimal("50");
        assertThat(rules.supply(people, YearMonth.of(2026, 12))).isEqualByComparingTo("0.58");
    }

    @Test void futureConfirmedOverloadDoesNotProduceNegativeSupply() throws Exception {
        use("식-60");
        assertThat(rules.supply(List.of(new SupplyPerson(new BigDecimal("1.5"), List.of())),
            YearMonth.of(2026, 12))).isEqualByComparingTo("0");
        verify(mapper, never()).selectDefinition("식-61");
    }

    @Test void demandIncludesPlanShortfallAndNeverSubtractsOverAssignedProject() throws Exception {
        use("식-61");
        assertThat(rules.demand(
            List.of(new OpportunityDemand(new BigDecimal("3"), BigDecimal.ONE, new BigDecimal("60"))),
            List.of(new ProjectDemand(new BigDecimal("12"), new BigDecimal("9"), 3),
                new ProjectDemand(new BigDecimal("2"), new BigDecimal("4"), 3),
                new ProjectDemand(new BigDecimal("12"), BigDecimal.ZERO, 0))))
            .isEqualByComparingTo("2.8");
    }
}
