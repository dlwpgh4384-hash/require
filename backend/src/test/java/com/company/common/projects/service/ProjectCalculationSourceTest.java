package com.company.common.projects.service;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.mapper.CalculationRuleRow;
import com.company.common.calculation.mapper.CalculationStandardRow;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectCalculationSourceTest {
    private CalculationRuleMapper mapper;
    private UserPreferencePeriodSourceMapper periods;
    private ProjectAmountRules amounts;
    private final YearMonth month = YearMonth.of(2026, 11);

    @BeforeEach
    void setup() {
        mapper = mock(CalculationRuleMapper.class);
        periods = mock(UserPreferencePeriodSourceMapper.class);
        amounts = new ProjectAmountRules(new CalculationRuleService(mapper, periods));
    }

    @ParameterizedTest
    @ValueSource(strings = {"selectDefinition", "selectEffectiveStandard"})
    void repeatedReadsCannotReuseSessionOrSecondLevelMetadataCache(String operation) throws Exception {
        var config = new Configuration();
        String resource = "mapper/CalculationRuleMapper.xml";
        try (var stream = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
        }
        var statement = config.getMappedStatement(CalculationRuleMapper.class.getName() + "." + operation);
        assertThat(statement.isFlushCacheRequired()).isTrue();
        assertThat(statement.isUseCache()).isFalse();
    }

    @Test
    void missingDefinitionFailsWithoutInventingTheRemainingDefinitions() {
        when(mapper.selectDefinition("식-00")).thenReturn(definition("식-00", "공통 원칙"));
        assertThatThrownBy(amounts::definitions)
            .isInstanceOf(CodeFailure.class)
            .hasMessageContaining("등록된 식 정의가 없습니다.");
        verify(mapper).selectDefinition("식-00");
        verify(mapper).selectDefinition("식-04");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void readsAllDeclaredDefinitionsThroughTheExistingMapper() {
        when(mapper.selectDefinition(anyString())).thenAnswer(call -> definition(call.getArgument(0), "DB 정의"));
        assertThat(amounts.definitions()).extracting(value -> value.calcId()).containsExactly(
            "식-00", "식-04", "식-11", "식-16", "식-18", "식-19", "식-20", "식-24", "식-25",
            "식-53", "식-54", "식-55", "식-56", "식-67", "식-72", "식-73", "식-76");
        verifyNoInteractions(periods);
    }

    @Test
    void changedMapperDefinitionChangesTheNextBudgetWithoutMutatingItsSourceRow() {
        when(mapper.selectDefinition("식-00")).thenReturn(definition("식-00", "공통 원칙"));
        var original = definition("식-19", "예산 기본값 = 현재 계약금액 × (1 − 목표 마진율)");
        var changed = definition("식-19", "예산 기본값 = 현재 계약금액 × (0.9 − 목표 마진율)");
        when(mapper.selectDefinition("식-19")).thenReturn(original, changed);
        var standard = new CalculationStandardRow();
        standard.standardCode = "TGT_MRGN_RT";
        standard.effectiveMonth = "202601";
        standard.value = new BigDecimal("20");
        when(mapper.selectEffectiveStandard("TGT_MRGN_RT", "202611")).thenReturn(standard);

        assertThat(amounts.automaticBudget(new BigDecimal("1000000"), month)).isEqualByComparingTo("800000");
        assertThat(amounts.automaticBudget(new BigDecimal("1000000"), month)).isEqualByComparingTo("700000");
        assertThat(original.formula).isEqualTo("예산 기본값 = 현재 계약금액 × (1 − 목표 마진율)");
        verify(mapper, times(2)).selectDefinition("식-00");
        verify(mapper, times(2)).selectDefinition("식-19");
        verify(mapper, times(2)).selectEffectiveStandard("TGT_MRGN_RT", "202611");
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(periods);
    }

    @Test
    void missingEffectiveStandardDoesNotUseAnExampleMargin() {
        when(mapper.selectDefinition("식-00")).thenReturn(definition("식-00", "공통 원칙"));
        when(mapper.selectDefinition("식-19"))
            .thenReturn(definition("식-19", "예산 기본값 = 현재 계약금액 × (1 − 목표 마진율)"));
        assertThatThrownBy(() -> amounts.automaticBudget(BigDecimal.ONE, month))
            .isInstanceOf(CodeFailure.class)
            .hasMessageContaining("적용 가능한 운영 기준값이 없습니다.");
    }

    private CalculationRuleRow definition(String id, String formula) {
        var row = new CalculationRuleRow();
        row.calcId = id;
        row.formula = formula;
        row.details = "재조회 시험 정의";
        return row;
    }
}
