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

class SalesPipelineDefinitionsTest {
    private static final List<String> IDS = List.of(
        "식-00", "식-60", "식-61", "식-79", "식-81", "식-82",
        "식-83", "식-84", "식-85", "식-86", "식-92", "식-93");
    private final CalculationRuleMapper mapper = mock(CalculationRuleMapper.class);
    private final UserPreferencePeriodSourceMapper periods = mock(UserPreferencePeriodSourceMapper.class);
    private final SalesPipelineDefinitions definitions =
        new SalesPipelineDefinitions(new CalculationRuleService(mapper, periods));

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
    void exactCalculationIdsAndCommonPrincipleAreReadVerbatim() {
        var values = definitions.definitions();
        assertThat(values).extracting(Definition::calcId).containsExactlyElementsOf(IDS);
        for (var value : values) {
            assertThat(value.formula()).isEqualTo("DB 정의 × ÷ −");
            assertThat(value.details()).isEqualTo("DB 상세\n원문");
            verify(mapper).selectDefinition(value.calcId());
        }
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(periods);
    }

    @ParameterizedTest
    @ValueSource(strings = {"식-00", "식-60", "식-61", "식-79", "식-81", "식-82",
        "식-83", "식-84", "식-85", "식-86", "식-92", "식-93"})
    void eachMissingDefinitionIsAFieldErrorWithoutFallback(String id) {
        when(mapper.selectDefinition(id)).thenReturn(null);
        assertThatThrownBy(definitions::definitions).isInstanceOfSatisfying(CodeFailure.class, error -> {
            assertThat(error.getField()).isEqualTo("calcId");
            assertThat(error.getStatus()).isEqualTo(400);
        });
        verify(mapper).selectDefinition(id);
        verifyNoInteractions(periods);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void absentFormulaIsRejected(String formula) {
        when(mapper.selectDefinition("식-83")).thenReturn(row("식-83", formula, "상세"));
        assertThatThrownBy(definitions::definitions).isInstanceOf(CodeFailure.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void absentDetailsAreRejected(String details) {
        when(mapper.selectDefinition("식-83")).thenReturn(row("식-83", "식", details));
        assertThatThrownBy(definitions::definitions).isInstanceOf(CodeFailure.class);
    }

    @Test
    void differentCalculationIdentityCannotReplaceRequestedFormula() {
        when(mapper.selectDefinition("식-84")).thenReturn(row("식-87", "후보 식", "다른 상세"));
        assertThatThrownBy(definitions::definitions).isInstanceOf(CodeFailure.class);
    }

    @Test
    void changesAreRereadWithoutCachingOrReplacingTextWithConstants() {
        var before = definitions.definitions();
        var changed = row("식-86", "새 DB 정의", "새 상세");
        when(mapper.selectDefinition("식-86")).thenReturn(changed);
        assertThat(definitions.definitions()).contains(changed.value());
        assertThat(before).doesNotContain(changed.value());
        verify(mapper, times(2)).selectDefinition("식-86");
    }

    @Test
    void definitionSnapshotIsImmutableAndReadOnlyRepeatableRead() throws Exception {
        assertThatThrownBy(() -> definitions.definitions().clear()).isInstanceOf(UnsupportedOperationException.class);
        var transaction = SalesPipelineDefinitions.class.getMethod("definitions").getAnnotation(Transactional.class);
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
