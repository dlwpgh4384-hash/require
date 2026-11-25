package com.company.common.dashboard;

import com.company.common.dashboard.mapper.DashboardMapper;
import com.company.common.dashboard.service.DashboardUtilizationService;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.calculation.dto.CalculationRuleContracts.Standard;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardUtilizationTest {
    @Test void distinctPeopleRateUsesHalfUpAndDatabaseTarget() {
        var mapper = mock(DashboardMapper.class);
        var rules = mock(CalculationRuleService.class);
        var month = YearMonth.of(2026, 11);
        when(mapper.utilization("202611", month.atDay(1), month.atEndOfMonth()))
            .thenReturn(new DashboardMapper.Utilization(6, 5));
        when(rules.effectiveStandard("TGT_UTIL_RT", month))
            .thenReturn(new Standard("TGT_UTIL_RT", month, new BigDecimal("87.5")));
        var result = new DashboardUtilizationService(mapper, rules).read(month);
        assertThat(result.rate()).isEqualByComparingTo("83.3");
        assertThat(result.idle()).isEqualByComparingTo("1");
        assertThat(result.target()).isEqualByComparingTo("87.5");
        verify(rules).definition("식-00");
        verify(rules).definition("식-37");
        verify(mapper, never()).insertAccess(any(), any());
    }
    @Test void zeroDenominatorIsUnknownRatherThanZeroPercent() {
        var mapper = mock(DashboardMapper.class);
        var rules = mock(CalculationRuleService.class);
        var month = YearMonth.of(2026, 2);
        when(mapper.utilization(any(), any(), any())).thenReturn(new DashboardMapper.Utilization(0, 0));
        when(rules.effectiveStandard("TGT_UTIL_RT", month))
            .thenReturn(new Standard("TGT_UTIL_RT", month, BigDecimal.ONE));
        assertThat(new DashboardUtilizationService(mapper, rules).read(month).rate()).isNull();
    }
}
