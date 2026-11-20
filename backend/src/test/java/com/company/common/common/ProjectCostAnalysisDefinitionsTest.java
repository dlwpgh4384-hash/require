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

class ProjectCostAnalysisDefinitionsTest {
    private final CalculationRuleMapper mapper = mock(CalculationRuleMapper.class);
    private final UserPreferencePeriodSourceMapper periods = mock(UserPreferencePeriodSourceMapper.class);
    private final ProjectCostAnalysisDefinitions definitions = new ProjectCostAnalysisDefinitions(
        new CalculationRuleService(mapper, periods));
    private static final List<String> IDS = List.of(
        "식-00", "식-02", "식-09", "식-10", "식-11", "식-13", "식-15", "식-16",
        "식-18", "식-19", "식-20", "식-21", "식-22", "식-23", "식-24", "식-25",
        "식-26", "식-27", "식-28", "식-29", "식-31", "식-53", "식-55", "식-56",
        "식-57", "식-75", "식-87", "식-88", "식-90", "식-91", "식-94");

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
    void readsEveryDeclaredIdAndCommonPrincipleThroughTheExistingService() {
        var result = definitions.definitions();
        assertThat(result).extracting(Definition::calcId).containsExactlyElementsOf(IDS);
        for (var definition : result) {
            assertThat(definition.formula()).isEqualTo("독립 DB 정의 " + definition.calcId());
            assertThat(definition.details()).isEqualTo("독립 DB 상세\n" + definition.calcId());
            verify(mapper).selectDefinition(definition.calcId());
        }
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(periods);
    }

    @Test
    void declaredEightySevenEightyEightAndNinetyAreNotAliasesForCauseNinetyOne() {
        var result = definitions.definitions();
        for (String id : List.of("식-87", "식-88", "식-90", "식-91")) {
            assertThat(result.stream().filter(value -> id.equals(value.calcId())).toList())
                .containsExactly(new Definition(id, "독립 DB 정의 " + id, "독립 DB 상세\n" + id));
            verify(mapper).selectDefinition(id);
        }
    }

    @Test
    void returnsChangedDatabaseNarrativeWithoutExecutingItOrUsingCachedConstants() {
        var before = definitions.definitions();
        var changed = new CalculationRuleRow();
        changed.calcId = "식-18";
        changed.formula = "변경된 DB 자연어 × ÷ −";
        changed.details = "실행하지 않는 설명\n새 정의";
        when(mapper.selectDefinition(changed.calcId)).thenReturn(changed);
        clearInvocations(mapper);

        var after = definitions.definitions();
        assertThat(after).contains(changed.value());
        assertThat(before).doesNotContain(changed.value());
        IDS.forEach(id -> verify(mapper).selectDefinition(id));
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(periods);
    }

    static Stream<String> ids() {
        return IDS.stream();
    }

    @ParameterizedTest
    @MethodSource("ids")
    void missingDefinitionFailsWithoutInventingAFormulaOrSeedingIt(String id) {
        when(mapper.selectDefinition(id)).thenReturn(null);
        assertThatThrownBy(definitions::definitions).isInstanceOfSatisfying(CodeFailure.class, error -> {
            assertThat(error.getStatus()).isEqualTo(400);
            assertThat(error.getField()).isEqualTo("calcId");
        });
        verify(mapper).selectDefinition(id);
        verifyNoInteractions(periods);
    }

    @Test
    void resultIsAnImmutableSnapshot() {
        var result = definitions.definitions();
        assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void standaloneDefinitionReadUsesAReadOnlyRepeatableSnapshot() throws Exception {
        var transaction = ProjectCostAnalysisDefinitions.class.getMethod("definitions")
            .getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
