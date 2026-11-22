package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.mapper.CalculationRuleRow;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Code;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NetProfitStatementDefinitionsTest {
    private final CalculationRuleMapper mapper = mock(CalculationRuleMapper.class);
    private final UserPreferencePeriodSourceMapper periods = mock(UserPreferencePeriodSourceMapper.class);
    private final ProfitStructureMapper source = mock(ProfitStructureMapper.class);
    private final NetProfitStatementDefinitions definitions = new NetProfitStatementDefinitions(
        new CalculationRuleService(mapper, periods), source);
    private static final List<String> IDS = List.of(
        "식-00", "식-02", "식-14", "식-15", "식-28", "식-29", "식-31", "식-32", "식-34", "식-59");

    @BeforeEach void setup() {
        when(mapper.selectDefinition(anyString())).thenAnswer(call -> {
            var row = new CalculationRuleRow();
            row.calcId = call.getArgument(0);
            row.formula = "DB 정의 " + row.calcId;
            row.details = "DB 상세\n" + row.calcId;
            return row;
        });
    }

    @Test void readsEveryRequiredFormulaAndCommonPrincipleThroughExistingService() {
        var result = definitions.definitions();
        assertThat(result).extracting(Definition::calcId).containsExactlyElementsOf(IDS);
        for (var row : result) {
            assertThat(row.formula()).isEqualTo("DB 정의 " + row.calcId());
            assertThat(row.details()).isEqualTo("DB 상세\n" + row.calcId());
            verify(mapper).selectDefinition(row.calcId());
        }
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(source, periods);
    }

    @Test void readsChangedDatabaseDefinitionWithoutCachingOrSubstitutingFormulaText() {
        var before = definitions.definitions();
        var changed = new CalculationRuleRow();
        changed.calcId = "식-32";
        changed.formula = "새 DB 설명 × ÷ −";
        changed.details = "실행하지 않는 자연어\n원문";
        when(mapper.selectDefinition(changed.calcId)).thenReturn(changed);
        assertThat(definitions.definitions()).contains(changed.value());
        assertThat(before).doesNotContain(changed.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"식-00", "식-02", "식-14", "식-15", "식-28", "식-29", "식-31", "식-32", "식-34", "식-59"})
    void missingFormulaHasNoInventedFallback(String id) {
        when(mapper.selectDefinition(id)).thenReturn(null);
        assertThatThrownBy(definitions::definitions).isInstanceOfSatisfying(CodeFailure.class, error -> {
            assertThat(error.getField()).isEqualTo("calcId");
            assertThat(error.getStatus()).isEqualTo(400);
        });
        verifyNoInteractions(source, periods);
    }

    @Test void readsOnlyFourGroupsPreservingDatabaseCodesNamesAndValuesIncludingInternalType() {
        var rows = List.of(
            new Code("CTRT_TYPE", "TK", "DB 턴키", null),
            new Code("CTRT_TYPE", "IN", "DB 내부", null),
            new Code("SGA_ITEM", "RENT", "DB 임차료", BigDecimal.TEN),
            new Code("FIN_CLS", "NOPR_IN", "DB 영업외수익", null),
            new Code("COST_CLS", "ALLOC", "DB 배분", null),
            new Code("EMP_TYPE", "FT", "DB 정규직", null));
        when(source.codes()).thenReturn(rows);
        assertThat(definitions.codes()).containsExactlyElementsOf(rows.subList(0, 5));
        verify(source).codes();
        verifyNoMoreInteractions(source);
        verifyNoInteractions(mapper, periods);
    }

    @Test void codeChangesAreReadAgainWithoutHardcodedNamesOrSeedFallback() {
        var changed = new Code("CTRT_TYPE", "MO", "변경된 DB 표시명", null);
        when(source.codes()).thenReturn(List.of(changed)).thenReturn(List.of());
        assertThat(definitions.codes()).containsExactly(changed);
        assertThat(definitions.codes()).isEmpty();
        verify(source, times(2)).codes();
    }

    @Test void consumerCannotMutateDefinitionOrCodeSnapshots() {
        when(source.codes()).thenReturn(List.of(new Code("CTRT_TYPE", "DP", "DB 파견", null)));
        assertThatThrownBy(() -> definitions.definitions().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> definitions.codes().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"definitions", "codes"})
    void standaloneReadsDeclareReadOnlyRepeatableRead(String method) throws Exception {
        var transaction = NetProfitStatementDefinitions.class.getMethod(method).getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
