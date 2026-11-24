package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.mapper.CalculationRuleRow;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.service.CodeFailure;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CandidateDefinitionsTest {
    private static final List<String> IDS = List.of("식-00", "식-60", "식-61", "식-87");
    private final CalculationRuleMapper mapper = mock(CalculationRuleMapper.class);
    private final UserPreferencePeriodSourceMapper periods = mock(UserPreferencePeriodSourceMapper.class);
    private final CandidateDefinitions definitions = new CandidateDefinitions(new CalculationRuleService(mapper, periods));

    @BeforeEach
    void setup() {
        when(mapper.selectDefinition(anyString())).thenAnswer(call -> row(call.getArgument(0), "DB 정의", "DB 상세\n원문"));
    }

    private CalculationRuleRow row(String id, String formula, String details) {
        var row = new CalculationRuleRow();
        row.calcId = id;
        row.formula = formula;
        row.details = details;
        return row;
    }

    @Test
    void commonPrincipleAndCandidateDefinitionsComeFromDatabaseVerbatim() {
        var result = definitions.definitions();
        assertThat(result).extracting(Definition::calcId).containsExactlyElementsOf(IDS);
        for (var value : result) {
            assertThat(value.formula()).isEqualTo("DB 정의");
            assertThat(value.details()).isEqualTo("DB 상세\n원문");
            verify(mapper).selectDefinition(value.calcId());
        }
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(periods);
    }

    @Test
    void changedDefinitionIsReadAgainRatherThanCachedOrEvaluated() {
        var before = definitions.definitions();
        var changed = row("식-87", "문서 × ÷ −\nSELECT 1", "변경된 원문");
        when(mapper.selectDefinition("식-87")).thenReturn(changed);
        assertThat(definitions.definitions()).contains(changed.value());
        assertThat(before).doesNotContain(changed.value());
        verify(mapper, times(2)).selectDefinition("식-87");
        verifyNoInteractions(periods);
    }

    @ParameterizedTest
    @ValueSource(strings = {"식-00", "식-60", "식-61", "식-87"})
    void missingDefinitionDoesNotFallBackToCodeFormula(String id) {
        when(mapper.selectDefinition(id)).thenReturn(null);
        assertThatThrownBy(definitions::definitions).isInstanceOfSatisfying(CodeFailure.class, error -> {
            assertThat(error.getField()).isEqualTo("calcId");
            assertThat(error.getStatus()).isEqualTo(400);
        });
        verifyNoInteractions(periods);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void blankFormulaIsRejected(String value) {
        when(mapper.selectDefinition("식-60")).thenReturn(row("식-60", value, "상세"));
        assertThatThrownBy(definitions::definitions).isInstanceOf(CodeFailure.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void blankDetailsAreRejected(String value) {
        when(mapper.selectDefinition("식-61")).thenReturn(row("식-61", "식", value));
        assertThatThrownBy(definitions::definitions).isInstanceOf(CodeFailure.class);
    }

    @Test
    void nullFormulaOrDetailsAreRejected() {
        when(mapper.selectDefinition("식-60")).thenReturn(row("식-60", null, "상세"));
        assertThatThrownBy(definitions::definitions).isInstanceOf(CodeFailure.class);
        when(mapper.selectDefinition("식-60")).thenReturn(row("식-60", "식", null));
        assertThatThrownBy(definitions::definitions).isInstanceOf(CodeFailure.class);
    }

    @Test
    void mismatchedDefinitionIdentityIsRejected() {
        when(mapper.selectDefinition("식-60")).thenReturn(row("식-61", "다른 식", "상세"));
        assertThatThrownBy(definitions::definitions).isInstanceOf(CodeFailure.class);
    }

    @Test
    void immutableResultUsesReadOnlyRepeatableRead() throws Exception {
        assertThatThrownBy(() -> definitions.definitions().clear()).isInstanceOf(UnsupportedOperationException.class);
        var transaction = CandidateDefinitions.class.getMethod("definitions").getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
