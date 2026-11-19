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

class PersonalCostProfitDefinitionsTest {
    private final CalculationRuleMapper mapper = mock(CalculationRuleMapper.class);
    private final UserPreferencePeriodSourceMapper periods = mock(UserPreferencePeriodSourceMapper.class);
    private final PersonalCostProfitDefinitions definitions = new PersonalCostProfitDefinitions(
        new CalculationRuleService(mapper, periods));
    private static final List<String> IDS = List.of(
        "식-00", "식-01", "식-02", "식-07", "식-08", "식-09", "식-10", "식-11",
        "식-12", "식-13", "식-14", "식-15", "식-16", "식-17", "식-19", "식-20",
        "식-21", "식-23", "식-24", "식-25", "식-27", "식-30", "식-34", "식-35",
        "식-37", "식-38", "식-39", "식-40", "식-53", "식-55", "식-57", "식-66",
        "식-95", "식-96");

    @BeforeEach
    void setup() {
        when(mapper.selectDefinition(anyString())).thenAnswer(call -> {
            var row = new CalculationRuleRow();
            row.calcId = call.getArgument(0);
            row.formula = "독립 DB 정의 " + row.calcId;
            row.details = "독립 DB 상세\n" + row.calcId;
            return row;
        });
    }

    @Test
    void everyRequestedDefinitionIncludesTheCommonPrincipleAndUsesTheRealService() {
        var result = definitions.definitions();
        assertThat(result).extracting(Definition::calcId).containsExactlyElementsOf(IDS);
        for (var value : result) {
            assertThat(value.formula()).isEqualTo("독립 DB 정의 " + value.calcId());
            assertThat(value.details()).isEqualTo("독립 DB 상세\n" + value.calcId());
            verify(mapper).selectDefinition(value.calcId());
        }
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(periods);
    }

    @Test
    void changedDatabaseProseIsReturnedVerbatimWithoutFingerprintChecksOrEvaluation() {
        var before = definitions.definitions();
        var changed = new CalculationRuleRow();
        changed.calcId = "식-57";
        changed.formula = "변경된 DB 설명; 실행할 수 없는 자연어";
        changed.details = "공백 × ÷ −\n세부 정의";
        when(mapper.selectDefinition(changed.calcId)).thenReturn(changed);
        clearInvocations(mapper);

        var after = definitions.definitions();
        assertThat(after.stream().filter(value -> value.calcId().equals(changed.calcId)).findFirst())
            .contains(changed.value());
        assertThat(before).doesNotContain(changed.value());
        IDS.forEach(id -> verify(mapper).selectDefinition(id));
        verifyNoMoreInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "식-00", "식-01", "식-02", "식-07", "식-08", "식-09", "식-10", "식-11",
        "식-12", "식-13", "식-14", "식-15", "식-16", "식-17", "식-19", "식-20",
        "식-21", "식-23", "식-24", "식-25", "식-27", "식-30", "식-34", "식-35",
        "식-37", "식-38", "식-39", "식-40", "식-53", "식-55", "식-57", "식-66",
        "식-95", "식-96"
    })
    void missingDefinitionHasNoInventedFormulaOrNumericFallback(String id) {
        when(mapper.selectDefinition(id)).thenReturn(null);
        assertThatThrownBy(definitions::definitions).isInstanceOfSatisfying(CodeFailure.class, error -> {
            assertThat(error.getField()).isEqualTo("calcId");
            assertThat(error.getStatus()).isEqualTo(400);
        });
        verify(mapper).selectDefinition(id);
        verifyNoInteractions(periods);
    }

    @Test
    void definitionSnapshotCannotBeMutatedByAnotherConsumer() {
        var result = definitions.definitions();
        assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void standaloneReadsDeclareAConsistentReadOnlySnapshot() throws Exception {
        var transaction = PersonalCostProfitDefinitions.class.getMethod("definitions")
            .getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
