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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManpowerBoardDefinitionsTest {
    private static final List<String> IDS = List.of(
        "식-00", "식-01", "식-60", "식-61", "식-62", "식-63", "식-77", "식-95");
    private final CalculationRuleMapper mapper = mock(CalculationRuleMapper.class);
    private final UserPreferencePeriodSourceMapper periods = mock(UserPreferencePeriodSourceMapper.class);
    private final ManpowerBoardDefinitions definitions =
        new ManpowerBoardDefinitions(new CalculationRuleService(mapper, periods));

    @BeforeEach
    void setup() {
        when(mapper.selectDefinition(anyString())).thenAnswer(call ->
            row(call.getArgument(0), "DB 정의 × ÷ −", "DB 상세\n원문"));
    }

    private CalculationRuleRow row(String id, String formula, String details) {
        var row = new CalculationRuleRow();
        row.calcId = id;
        row.formula = formula;
        row.details = details;
        return row;
    }

    @Test
    void exactIdsIncludingCommonPrincipleAreLoadedOncePerSnapshot() {
        var result = definitions.definitions();
        assertThat(result).extracting(Definition::calcId).containsExactlyElementsOf(IDS);
        for (var definition : result) {
            assertThat(definition.formula()).isEqualTo("DB 정의 × ÷ −");
            assertThat(definition.details()).isEqualTo("DB 상세\n원문");
            verify(mapper).selectDefinition(definition.calcId());
        }
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(periods);
    }

    @ParameterizedTest
    @ValueSource(strings = {"식-00", "식-01", "식-60", "식-61", "식-62", "식-63", "식-77", "식-95"})
    void eachMissingDefinitionFailsWithoutFallback(String id) {
        when(mapper.selectDefinition(id)).thenReturn(null);
        assertFieldError();
        verify(mapper).selectDefinition(id);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void missingFormulaFailsWithoutFallback(String formula) {
        when(mapper.selectDefinition("식-63")).thenReturn(row("식-63", formula, "상세"));
        assertFieldError();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void missingDetailsFailWithoutFallback(String details) {
        when(mapper.selectDefinition("식-77")).thenReturn(row("식-77", "식", details));
        assertFieldError();
    }

    @Test
    void wrongIdentityCannotSubstituteAnotherDefinition() {
        when(mapper.selectDefinition("식-95")).thenReturn(row("식-94", "다른 식", "다른 상세"));
        assertFieldError();
    }

    @Test
    void aNewRequestReadsChangedDatabaseTextInsteadOfCachingIt() {
        var before = definitions.definitions();
        var changed = row("식-63", "변경된 DB 정의", "변경된 상세");
        when(mapper.selectDefinition("식-63")).thenReturn(changed);
        assertThat(definitions.definitions()).contains(changed.value());
        assertThat(before).doesNotContain(changed.value());
        for (String id : IDS) verify(mapper, times(2)).selectDefinition(id);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void snapshotIsImmutableAndUsesReadOnlyRepeatableRead() throws Exception {
        assertThatThrownBy(() -> definitions.definitions().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        var transaction = ManpowerBoardDefinitions.class.getMethod("definitions")
            .getAnnotation(Transactional.class);
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }

    private void assertFieldError() {
        assertThatThrownBy(definitions::definitions).isInstanceOfSatisfying(CodeFailure.class, error -> {
            assertThat(error.getStatus()).isEqualTo(400);
            assertThat(error.getField()).isEqualTo("calcId");
        });
        verifyNoInteractions(periods);
    }
}
