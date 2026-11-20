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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartSettlementDefinitionsTest {
    private static final List<String> IDS = List.of(
        "식-00", "식-01", "식-35", "식-36", "식-37", "식-39", "식-40",
        "식-41", "식-42", "식-43", "식-44", "식-45", "식-57", "식-58");
    private final CalculationRuleMapper mapper = mock(CalculationRuleMapper.class);
    private final UserPreferencePeriodSourceMapper periods = mock(UserPreferencePeriodSourceMapper.class);
    private final PartSettlementDefinitions definitions = new PartSettlementDefinitions(
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
    void readsAllRequestedIdsWithoutReplacingFormulaNumberCrossReferences() {
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
    void changedDatabaseTextIsReturnedVerbatimWithoutEvaluationOrCaching() {
        var before = definitions.definitions();
        var row = new CalculationRuleRow();
        row.calcId = "식-37";
        row.formula = "임의 실행 금지: Runtime.getRuntime()";
        row.details = "새 DB 상세 × ÷ −\n원문 보존";
        when(mapper.selectDefinition(row.calcId)).thenReturn(row);
        clearInvocations(mapper);
        assertThat(definitions.definitions()).contains(row.value());
        assertThat(before).doesNotContain(row.value());
        IDS.forEach(id -> verify(mapper).selectDefinition(id));
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(periods);
    }

    static Stream<String> ids() { return IDS.stream(); }

    @ParameterizedTest
    @MethodSource("ids")
    void missingDefinitionFailsRatherThanInventingOrSeedingIt(String id) {
        when(mapper.selectDefinition(id)).thenReturn(null);
        assertThatThrownBy(definitions::definitions).isInstanceOfSatisfying(CodeFailure.class, error -> {
            assertThat(error.getStatus()).isEqualTo(400);
            assertThat(error.getField()).isEqualTo("calcId");
        });
        verify(mapper).selectDefinition(id);
        verifyNoInteractions(periods);
    }

    @Test
    void definitionReadIsImmutableAndReadOnlyRepeatableRead() throws Exception {
        assertThatThrownBy(() -> definitions.definitions().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        var transaction = PartSettlementDefinitions.class.getMethod("definitions")
            .getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
