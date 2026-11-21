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

class RateRenegotiationDefinitionsTest {
    private static final List<String> IDS = List.of("식-00", "식-57", "식-85", "식-86", "식-90");
    private final CalculationRuleMapper mapper = mock(CalculationRuleMapper.class);
    private final UserPreferencePeriodSourceMapper periods = mock(UserPreferencePeriodSourceMapper.class);
    private final RateRenegotiationDefinitions definitions = new RateRenegotiationDefinitions(
        new CalculationRuleService(mapper, periods));

    @BeforeEach
    void setup() {
        when(mapper.selectDefinition(anyString())).thenAnswer(call -> {
            var row = new CalculationRuleRow();
            row.calcId = call.getArgument(0);
            row.formula = "DB에서 읽은 설명 " + row.calcId;
            row.details = "DB에서 읽은 상세\n" + row.calcId;
            return row;
        });
    }

    @Test
    void readsOnlyRequestedLiveDefinitionsThroughTheExistingService() {
        var result = definitions.definitions();
        assertThat(result).extracting(Definition::calcId).containsExactlyElementsOf(IDS);
        for (var value : result) {
            assertThat(value.formula()).isEqualTo("DB에서 읽은 설명 " + value.calcId());
            assertThat(value.details()).isEqualTo("DB에서 읽은 상세\n" + value.calcId());
            verify(mapper).selectDefinition(value.calcId());
        }
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(periods);
    }

    @ParameterizedTest
    @ValueSource(strings = {"식-00", "식-57", "식-85", "식-86", "식-90"})
    void returnsChangedProseVerbatimWithoutEvaluationOrCachedCopies(String id) {
        var before = definitions.definitions();
        var changed = new CalculationRuleRow();
        changed.calcId = id;
        changed.formula = "실행 언어가 아닌 DB 설명 × ÷ −";
        changed.details = "견적 승인 참고\n설명만 유지";
        when(mapper.selectDefinition(id)).thenReturn(changed);
        clearInvocations(mapper);

        var after = definitions.definitions();

        assertThat(after.stream().filter(value -> id.equals(value.calcId())).findFirst())
            .contains(changed.value());
        assertThat(before).doesNotContain(changed.value());
        IDS.forEach(value -> verify(mapper).selectDefinition(value));
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(periods);
    }

    @ParameterizedTest
    @ValueSource(strings = {"식-00", "식-57", "식-85", "식-86", "식-90"})
    void missingDefinitionPropagatesTheExistingSourceError(String id) {
        when(mapper.selectDefinition(id)).thenReturn(null);
        assertThatThrownBy(definitions::definitions).isInstanceOfSatisfying(CodeFailure.class, error -> {
            assertThat(error.getField()).isEqualTo("calcId");
            assertThat(error.getStatus()).isEqualTo(400);
        });
        verify(mapper).selectDefinition(id);
        verifyNoInteractions(periods);
    }

    @Test
    void resultIsAnImmutableSnapshot() {
        assertThatThrownBy(() -> definitions.definitions().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void standaloneDefinitionReadsUseAReadOnlyRepeatableSnapshot() throws Exception {
        var transaction = RateRenegotiationDefinitions.class.getMethod("definitions")
            .getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
