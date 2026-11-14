package com.company.common.calculation.service;

import com.company.common.support.service.CodeFailure;
import com.company.common.calculation.dto.CalculationRuleContracts.Source;
import com.company.common.calculation.dto.CalculationRuleContracts.ValueRequest;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.mapper.CalculationRuleRow;
import com.company.common.calculation.mapper.CalculationStandardRow;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalculationRuleServiceTest {
    private final CalculationRuleMapper mapper = mock(CalculationRuleMapper.class);
    private final UserPreferencePeriodSourceMapper periods = mock(UserPreferencePeriodSourceMapper.class);
    private final CalculationRuleService service = new CalculationRuleService(mapper, periods);

    private void openMonth() {
        when(periods.selectCashBaseYm()).thenReturn(new BigDecimal("202601.0000"));
        when(periods.selectClosedMonths()).thenReturn(List.of("202602", "202604"));
    }
    private ValueRequest request(String month, String override, String closed) {
        return new ValueRequest("TEAM_EXPS", YearMonth.parse(month),
            override == null ? null : new BigDecimal(override), closed == null ? null : new BigDecimal(closed));
    }

    @Test void selectedOriginalDefinitionsAreReturnedVerbatimWithoutEvaluation() throws Exception {
        try (var input = new ClassPathResource("calculation/source-definitions.json").getInputStream()) {
            for (var fixture : new ObjectMapper().readTree(input)) {
                var row = new CalculationRuleRow();
                row.calcId = fixture.get("calcId").asText();
                row.formula = fixture.get("formula").asText();
                row.details = fixture.get("details").asText();
                when(mapper.selectDefinition(row.calcId)).thenReturn(row);
                var value = service.definition(row.calcId);
                assertThat(value.calcId()).isEqualTo(row.calcId);
                assertThat(value.formula()).isEqualTo(row.formula);
                assertThat(value.details()).isEqualTo(row.details);
                verify(mapper).selectDefinition(row.calcId);
            }
        }
        verifyNoInteractions(periods);
    }
    @Test void missingDefinitionDoesNotFabricateFormula() {
        assertThatThrownBy(() -> service.definition("식-03")).isInstanceOf(CodeFailure.class);
    }
    @Test void malformedIdentifierFailsBeforeLookup() {
        assertThatThrownBy(() -> service.definition("formula")).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper);
    }
    @Test void closedMonthUsesStoredResultBeforeOverrideAndCurrentDefaults() {
        openMonth();
        var result = service.resolve(request("2026-02", "90", "72.1250"));
        assertThat(result.source()).isEqualTo(Source.CLOSED_RESULT);
        assertThat(result.value()).isEqualByComparingTo("72.1250");
        verifyNoInteractions(mapper);
    }
    @Test void importedMonthAlsoPreservesStoredResult() {
        openMonth();
        assertThat(service.resolve(request("2025-12", "90", "15")).source()).isEqualTo(Source.CLOSED_RESULT);
        verifyNoInteractions(mapper);
    }
    @Test void closedMonthCannotFallBackToCurrentDefaults() {
        openMonth();
        assertThatThrownBy(() -> service.resolve(request("2026-02", "90", null))).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper);
    }
    @Test void openProjectOverrideWinsWithoutReadingDefaults() {
        openMonth();
        var result = service.resolve(request("2026-03", "0.0000", null));
        assertThat(result.source()).isEqualTo(Source.PROJECT_OVERRIDE);
        assertThat(result.value()).isEqualByComparingTo("0");
        verifyNoInteractions(mapper);
    }
    @Test void absentCloseRowBetweenClosedMonthsUsesEffectiveDatabaseDefault() {
        openMonth();
        var row = new CalculationStandardRow();
        row.standardCode = "TEAM_EXPS"; row.effectiveMonth = "202601"; row.value = new BigDecimal("12345678901.1234");
        when(mapper.selectEffectiveStandard("TEAM_EXPS", "202603")).thenReturn(row);
        var result = service.resolve(request("2026-03", null, null));
        assertThat(result.source()).isEqualTo(Source.OPERATING_STANDARD);
        assertThat(result.value()).isEqualTo(row.value);
    }
    @Test void missingDefaultDoesNotReturnZero() {
        openMonth();
        assertThatThrownBy(() -> service.resolve(request("2026-08", null, null))).isInstanceOf(CodeFailure.class);
    }
    @Test void missingCashBasisCannotDeclareMonthOpen() {
        assertThatThrownBy(() -> service.resolve(request("2026-03", "90", null))).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper);
    }
    @Test void malformedCashBasisFailsSafely() {
        when(periods.selectCashBaseYm()).thenReturn(new BigDecimal("202613"));
        assertThatThrownBy(() -> service.resolve(request("2026-03", "90", null))).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper);
    }
    @Test void koreanDateAndMonthRetainTheSameServerInstantAtUtcBoundary() {
        var instant = java.time.Instant.parse("2026-01-31T15:00:00Z");
        var korea = instant.atZone(java.time.ZoneId.of("Asia/Seoul"));
        assertThat(korea.toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 2, 1));
        assertThat(YearMonth.from(korea)).isEqualTo(YearMonth.of(2026, 2));
        assertThat(korea.toInstant()).isEqualTo(instant);
        assertThat(YearMonth.from(instant.atZone(java.time.ZoneOffset.UTC))).isEqualTo(YearMonth.of(2026, 1));
    }
    @Test void absentRequestIsFieldError() {
        assertThatThrownBy(() -> service.resolve(null)).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper, periods);
    }
}
