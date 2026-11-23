package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.mapper.CalculationRuleRow;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.service.CodeFailure;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonthCloseDefinitionsTest {
    private static final List<String> IDS = List.of(
        "식-00", "식-31", "식-44", "식-57", "식-58", "식-59", "식-88", "식-95");
    private final CalculationRuleMapper mapper = mock(CalculationRuleMapper.class);
    private final UserPreferencePeriodSourceMapper periods = mock(UserPreferencePeriodSourceMapper.class);
    private final MonthCloseDefinitions definitions = new MonthCloseDefinitions(
        new CalculationRuleService(mapper, periods));

    @BeforeEach
    void setup() {
        when(mapper.selectDefinition(anyString())).thenAnswer(call -> {
            var row = new CalculationRuleRow();
            row.calcId = call.getArgument(0);
            row.formula = "DB 정의 " + row.calcId;
            row.details = "DB 상세\n" + row.calcId;
            return row;
        });
    }

    @Test
    void readsCommonPrincipleAndEveryRequiredFormulaIncludingMmRounding() {
        var result = definitions.definitions();
        assertThat(result).extracting(Definition::calcId).containsExactlyElementsOf(IDS);
        for (var definition : result) {
            assertThat(definition.formula()).isEqualTo("DB 정의 " + definition.calcId());
            assertThat(definition.details()).isEqualTo("DB 상세\n" + definition.calcId());
            verify(mapper).selectDefinition(definition.calcId());
        }
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(periods);
    }

    @Test
    void reloadsChangedTextVerbatimAndNeverEvaluatesDefinitionProse() {
        var before = definitions.definitions();
        var row = new CalculationRuleRow();
        row.calcId = "식-31";
        row.formula = "실행하지 않는 문서: SELECT 1; throw new RuntimeException()";
        row.details = "새 DB 상세 × ÷ −\n원문 보존";
        when(mapper.selectDefinition(row.calcId)).thenReturn(row);
        assertThat(definitions.definitions()).contains(row.value());
        assertThat(before).doesNotContain(row.value());
        verify(mapper, times(2)).selectDefinition(row.calcId);
        verifyNoInteractions(periods);
    }

    static Stream<String> ids() { return IDS.stream(); }

    @ParameterizedTest
    @MethodSource("ids")
    void missingDefinitionFailsWithoutFallbackOrWrites(String id) {
        when(mapper.selectDefinition(id)).thenReturn(null);
        assertThatThrownBy(definitions::definitions).isInstanceOfSatisfying(CodeFailure.class, error -> {
            assertThat(error.getStatus()).isEqualTo(400);
            assertThat(error.getField()).isEqualTo("calcId");
        });
        verify(mapper).selectDefinition(id);
        verifyNoInteractions(periods);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void blankFormulaCannotBecomeAnImplicitFormula(String formula) {
        var row = new CalculationRuleRow();
        row.calcId = "식-58";
        row.formula = formula;
        row.details = "MM 반올림";
        when(mapper.selectDefinition(row.calcId)).thenReturn(row);
        assertThatThrownBy(definitions::definitions).isInstanceOf(CodeFailure.class);
    }

    @Test
    void mismatchedDatabaseIdentityIsRejected() {
        var row = new CalculationRuleRow();
        row.calcId = "식-32";
        row.formula = "다른 식";
        row.details = "다른 상세";
        when(mapper.selectDefinition("식-31")).thenReturn(row);
        assertThatThrownBy(definitions::definitions).isInstanceOf(CodeFailure.class);
    }

    @Test
    void resultIsImmutableAndUsesReadOnlyRepeatableRead() throws Exception {
        assertThatThrownBy(() -> definitions.definitions().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        var transaction = MonthCloseDefinitions.class.getMethod("definitions")
            .getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
