package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.mapper.CalculationRuleRow;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiRecommendationDefinitionsTest {
    private final CalculationRuleMapper mapper = mock(CalculationRuleMapper.class);
    private final UserPreferencePeriodSourceMapper periods = mock(UserPreferencePeriodSourceMapper.class);
    private final BusinessCalendar calendar = new BusinessCalendar(
        Clock.fixed(Instant.parse("2026-11-30T15:00:00Z"), ZoneOffset.UTC)
    );
    private final AiRecommendationDefinitions definitions = new AiRecommendationDefinitions(
        new CalculationRuleService(mapper, periods), calendar
    );

    @BeforeEach
    void setup() {
        when(mapper.selectDefinition(anyString())).thenAnswer(call -> row(call.getArgument(0), "DB 식", "DB 상세"));
    }

    private CalculationRuleRow row(String id, String formula, String details) {
        var row = new CalculationRuleRow();
        row.calcId = id;
        row.formula = formula;
        row.details = details;
        return row;
    }

    @Test
    void readsEveryApprovedDefinitionIncludingPrincipleWithoutEvaluatingOrWritingText() {
        var values = definitions.definitions();
        assertThat(values).extracting(Definition::calcId)
            .containsExactly("식-00", "식-68", "식-69", "식-70", "식-71", "식-79", "식-80");
        assertThat(values).allSatisfy(value -> {
            assertThat(value.formula()).isEqualTo("DB 식");
            assertThat(value.details()).isEqualTo("DB 상세");
            verify(mapper).selectDefinition(value.calcId());
        });
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(periods);
    }

    @Test
    void definitionChangesAreReadAgainWithoutChangingPreviousSnapshot() {
        var before = definitions.definitions();
        when(mapper.selectDefinition("식-70")).thenReturn(row("식-70", "바뀐 DB 식", "변경 상세"));
        assertThat(definitions.definitions()).contains(new Definition("식-70", "바뀐 DB 식", "변경 상세"));
        assertThat(before).contains(new Definition("식-70", "DB 식", "DB 상세"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"식-00", "식-68", "식-69", "식-70", "식-71", "식-79", "식-80"})
    void missingDefinitionNeverUsesAnEmbeddedFormula(String id) {
        when(mapper.selectDefinition(id)).thenReturn(null);
        assertThatThrownBy(definitions::definitions).isInstanceOfSatisfying(CodeFailure.class,
            failure -> assertThat(failure.getField()).isEqualTo("calcId"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void blankDefinitionIsRejected(String value) {
        when(mapper.selectDefinition("식-70")).thenReturn(row("식-70", value, "상세"));
        assertThatThrownBy(definitions::definitions).isInstanceOf(CodeFailure.class);
    }

    @Test
    void nullDetailsAreRejected() {
        when(mapper.selectDefinition("식-70")).thenReturn(row("식-70", "식", null));
        assertThatThrownBy(definitions::definitions).isInstanceOf(CodeFailure.class);
    }

    @Test
    void mismatchingIdentityIsRejected() {
        when(mapper.selectDefinition("식-70")).thenReturn(row("식-71", "식", "상세"));
        assertThatThrownBy(definitions::definitions).isInstanceOf(CodeFailure.class);
    }

    @Test
    void readsCurrentKoreaMonthRatherThanRequestedFutureMonth() {
        // UTC is still November; the business month is already December.
        var service = mock(CalculationRuleService.class);
        var source = new AiRecommendationDefinitions(service, calendar);
        List<String> codes = List.of("AI_SCORE_JOB", "AI_SCORE_SKILL", "AI_SCORE_CUST", "AI_SCORE_GRADE",
            "AI_SCORE_PERF", "AI_SCORE_COST", "AI_SCORE_AVAIL", "AI_SCORE_LOC");
        List<Integer> maxima = List.of(25, 20, 15, 10, 5, 5, 10, 10);
        var month = java.time.YearMonth.of(2026, 12);
        for (int i = 0; i < codes.size(); i++) {
            when(service.effectiveStandard(codes.get(i), month)).thenReturn(
                new com.company.common.calculation.dto.CalculationRuleContracts.Standard(
                    codes.get(i), month, BigDecimal.valueOf(maxima.get(i))
                )
            );
        }
        var saved = source.currentMaxima();
        assertThat(saved).containsEntry("job", new BigDecimal("25")).containsEntry("workplace", BigDecimal.TEN);
        assertThat(saved).hasSize(8);
        for (String code : codes) verify(service).effectiveStandard(code, month);
        verifyNoMoreInteractions(service);
        when(service.effectiveStandard("AI_SCORE_JOB", month)).thenReturn(
            new com.company.common.calculation.dto.CalculationRuleContracts.Standard("AI_SCORE_JOB", month, BigDecimal.ZERO)
        );
        assertThatThrownBy(source::currentMaxima).isInstanceOf(CodeFailure.class);
        assertThat(saved.get("job")).isEqualByComparingTo("25");
    }

    @Test
    void absentStandardIsAnInputFailureNotDefaultWeights() {
        var service = mock(CalculationRuleService.class);
        assertThatThrownBy(() -> new AiRecommendationDefinitions(service, calendar).currentMaxima())
            .isInstanceOfSatisfying(CodeFailure.class,
                failure -> assertThat(failure.getField()).isEqualTo("standardCode"));
    }
}
