package com.company.common.codegroups.service;

import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.codegroups.mapper.CodeRows;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CodeAllowanceResolverTest {
    private final CodeGroupMapper mapper=mock(CodeGroupMapper.class);
    private final UserPreferencePeriodSourceMapper periods=mock(UserPreferencePeriodSourceMapper.class);
    private final CodeAllowanceResolver resolver=new CodeAllowanceResolver(mapper,periods);
    private void periods() {
        when(periods.selectCashBaseYm()).thenReturn(new BigDecimal("202601"));
        when(periods.selectClosedMonths()).thenReturn(List.of("202602","202604"));
    }
    private CodeAllowanceResolver.Request request(String month, BigDecimal override, BigDecimal closed) {
        return new CodeAllowanceResolver.Request("TEAM_FEE",YearMonth.parse(month),override,closed);
    }
    @Test void openMonthReadsChangedDefaultEveryTimeWithoutCaching() {
        periods(); var row=new CodeRows.Code(); row.codeVal=new BigDecimal("50000"); row.delYn="N"; row.useYn="Y";
        when(mapper.selectCode("ALWNC","TEAM_FEE")).thenReturn(row);
        assertThat(resolver.resolve(request("2026-03",null,null)).value()).isEqualByComparingTo("50000");
        row.codeVal=new BigDecimal("60000.1250");
        assertThat(resolver.resolve(request("2026-03",null,null)).value()).isEqualByComparingTo("60000.1250");
        verify(mapper,times(2)).selectCode("ALWNC","TEAM_FEE");
    }
    @Test void closedMonthUsesStoredResultWithoutReadingOrUpdatingAllowance() {
        periods(); var result=resolver.resolve(request("2026-02",BigDecimal.ZERO,new BigDecimal("32100")));
        assertThat(result.value()).isEqualByComparingTo("32100");
        assertThat(result.source()).isEqualTo(CodeAllowanceResolver.Source.CLOSED_RESULT); verifyNoInteractions(mapper);
    }
    @Test void importedMonthsAreClosed() {
        periods(); assertThat(resolver.resolve(request("2025-12",null,BigDecimal.ONE)).source())
            .isEqualTo(CodeAllowanceResolver.Source.CLOSED_RESULT); verifyNoInteractions(mapper);
    }
    @Test void zeroProjectOverrideWinsInOpenMonth() {
        periods(); assertThat(resolver.resolve(request("2026-03",BigDecimal.ZERO,null)).value()).isEqualByComparingTo("0");
        verifyNoInteractions(mapper);
    }
    @Test void closedMonthCannotFallBackToCurrentDefault() {
        periods(); assertThatThrownBy(()->resolver.resolve(request("2026-02",null,null))).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper);
    }
    @Test void missingDefaultIsNotFabricated() {
        periods(); assertThatThrownBy(()->resolver.resolve(request("2026-03",null,null))).isInstanceOf(CodeFailure.class);
    }
    @Test void missingBasisDoesNotDeclareMonthOpen() {
        assertThatThrownBy(()->resolver.resolve(request("2026-03",BigDecimal.ONE,null))).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper);
    }
}
