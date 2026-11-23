package com.company.common.candidates.service;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.mapper.CalculationRuleRow;
import com.company.common.calculation.mapper.CalculationStandardRow;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.CandidateDefinitions;
import com.company.common.candidates.mapper.CandidateMapper;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CandidateCalculationRulesTest {
    final CalculationRuleMapper metadata = mock(CalculationRuleMapper.class);
    final UserPreferencePeriodSourceMapper periods = mock(UserPreferencePeriodSourceMapper.class);
    final CandidateMapper mapper = mock(CandidateMapper.class);
    final CalculationRuleService service = new CalculationRuleService(metadata, periods);
    final CandidateCalculationRules rules = new CandidateCalculationRules(new CandidateDefinitions(service), mapper, service);
    final YearMonth month = YearMonth.of(2026, 11);

    CalculationRuleRow original(String id) throws Exception {
        var seed = new ClassPathResource("db/migration/V8__calculation_rule_seed.sql").getContentAsString(StandardCharsets.UTF_8);
        var tuple = Pattern.compile("\\('" + Pattern.quote(id) + "', (?:NULL|'[^']*'), '[^']*', '((?:[^']|'')*)', '((?:[^']|'')*)',").matcher(seed);
        assertThat(tuple.find()).as(id).isTrue();
        var row = new CalculationRuleRow(); row.calcId = id; row.formula = tuple.group(1).replace("''", "'"); row.details = tuple.group(2).replace("''", "'"); return row;
    }
    void definitions() throws Exception {
        for (String id : new String[] {"식-00", "식-60", "식-61", "식-87"}) when(metadata.selectDefinition(id)).thenReturn(original(id));
        threshold("15");
    }
    void threshold(String value) {
        var row = new CalculationStandardRow(); row.standardCode = "QUOTE_APRV_RT"; row.effectiveMonth = "202601"; row.value = new BigDecimal(value);
        when(metadata.selectEffectiveStandard("QUOTE_APRV_RT", "202611")).thenReturn(row);
    }
    @Test void profitUsesDatabaseDefinitionAndDefaultExpensesNotProjectOverrides() throws Exception {
        definitions(); when(mapper.allowance("TEAM_FEE")).thenReturn(BigDecimal.valueOf(50000)); when(mapper.allowance("PEXP_FEE")).thenReturn(BigDecimal.valueOf(50000));
        assertThat(rules.terms(false, month).profit(BigDecimal.valueOf(8000000), BigDecimal.valueOf(6500000))).isEqualByComparingTo("17.5");
        when(mapper.allowance("TEAM_FEE")).thenReturn(BigDecimal.valueOf(150000));
        assertThat(rules.terms(false, month).profit(BigDecimal.valueOf(8000000), BigDecimal.valueOf(6500000))).isEqualByComparingTo("16.25");
        verify(metadata, times(2)).selectDefinition("식-87");
    }
    @Test void outsourcingHasZeroExpensesAndReadsNoDefaults() throws Exception {
        definitions();
        assertThat(rules.terms(true, month).profit(BigDecimal.valueOf(8000000), BigDecimal.valueOf(6500000))).isEqualByComparingTo("18.75");
        verify(mapper, never()).allowance(anyString());
    }
    @Test void exactThresholdIsNotLowMargin() throws Exception {
        definitions();
        var terms = rules.terms(true, month);
        assertThat(terms.lowMarginRate()).isEqualByComparingTo("15");
        assertThat(terms.lowMargin(new BigDecimal("14.999"))).isTrue();
        assertThat(terms.lowMargin(new BigDecimal("15"))).isFalse();
        assertThat(terms.lowMargin(new BigDecimal("15.001"))).isFalse();
    }
    @Test void lowMarginThresholdIsReadFromTheMonthsOperatingStandard() throws Exception {
        definitions(); threshold("20");
        var terms = rules.terms(true, month);
        assertThat(terms.lowMarginRate()).isEqualByComparingTo("20");
        assertThat(terms.lowMargin(new BigDecimal("18.75"))).isTrue();
        verify(metadata).selectEffectiveStandard("QUOTE_APRV_RT", "202611");
    }
    @Test void missingThresholdFailsExplicitly() throws Exception {
        definitions(); when(metadata.selectEffectiveStandard("QUOTE_APRV_RT", "202611")).thenReturn(null);
        assertThatThrownBy(() -> rules.terms(true, month)).isInstanceOf(CodeFailure.class);
    }
    @Test void missingDefaultFailsExplicitlyInsteadOfManufacturingMoney() throws Exception {
        definitions();
        assertThatThrownBy(() -> rules.terms(false, month)).isInstanceOf(CodeFailure.class);
    }
    @Test void changedDatabaseMultiplierIsReadOnNextRequest() throws Exception {
        definitions(); var changed = original("식-87"); changed.formula = changed.formula.replace("× 100", "× 10");
        when(metadata.selectDefinition("식-87")).thenReturn(changed);
        assertThat(rules.terms(true, month).profit(BigDecimal.valueOf(8000000), BigDecimal.valueOf(6500000))).isEqualByComparingTo("1.875");
    }
}
