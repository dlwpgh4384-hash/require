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

class BusinessPlanComparisonDefinitionsTest {
    private final CalculationRuleMapper mapper = mock(CalculationRuleMapper.class);
    private final UserPreferencePeriodSourceMapper periods = mock(UserPreferencePeriodSourceMapper.class);
    private final BusinessPlanComparisonDefinitions definitions = new BusinessPlanComparisonDefinitions(
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
    void readsCommonPrincipleAndBothBusinessPlanRulesByCanonicalId() {
        var result = definitions.definitions();
        assertThat(result).extracting(Definition::calcId).containsExactly("식-00", "식-30", "식-36");
        for (var row : result) {
            assertThat(row.formula()).isEqualTo("DB 정의 " + row.calcId());
            assertThat(row.details()).isEqualTo("DB 상세\n" + row.calcId());
            verify(mapper).selectDefinition(row.calcId());
        }
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(periods);
    }

    @Test
    void rereadsDatabaseProseWithoutCachingOrEvaluatingIt() {
        var before = definitions.definitions();
        var changed = new CalculationRuleRow();
        changed.calcId = "식-36";
        changed.formula = "DB에서 바뀐 설명 × ÷ −";
        changed.details = "실행하지 않는 원문\n둘째 줄";
        when(mapper.selectDefinition(changed.calcId)).thenReturn(changed);
        assertThat(definitions.definitions()).contains(changed.value());
        assertThat(before).doesNotContain(changed.value());
        verify(mapper, times(2)).selectDefinition("식-36");
    }

    @ParameterizedTest
    @ValueSource(strings = {"식-00", "식-30", "식-36"})
    void missingDefinitionHasNoInventedFallback(String id) {
        when(mapper.selectDefinition(id)).thenReturn(null);
        assertThatThrownBy(definitions::definitions).isInstanceOfSatisfying(CodeFailure.class, error -> {
            assertThat(error.getField()).isEqualTo("calcId");
            assertThat(error.getStatus()).isEqualTo(400);
        });
        verifyNoInteractions(periods);
    }

    @Test
    void definitionsAreImmutableReadOnlySnapshots() throws Exception {
        assertThatThrownBy(() -> definitions.definitions().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        var transaction = BusinessPlanComparisonDefinitions.class.getMethod("definitions")
            .getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
