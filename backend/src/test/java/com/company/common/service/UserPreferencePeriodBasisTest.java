package com.company.common.service;

import com.company.common.mapper.UserPreferencePeriodSourceMapper;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserPreferencePeriodBasisTest {
    private final UserPreferencePeriodSourceMapper mapper = mock(UserPreferencePeriodSourceMapper.class);
    private final UserPreferencePeriodBasis service = new UserPreferencePeriodBasis(mapper);

    @Test
    void lastClosedDecemberRollsIntoNextYear() {
        when(mapper.selectCashBaseYm()).thenReturn(new BigDecimal("202601.0000"));
        when(mapper.selectClosedMonths()).thenReturn(List.of("202611", "202612"));
        assertThat(service.metadata().get("periodBasis"))
            .isEqualTo(new UserPreferencePeriodBasis.Basis("202701", "202601", List.of("202611", "202612")));
    }

    @Test
    void noClosedMonthsUsesActualCashBasis() {
        when(mapper.selectCashBaseYm()).thenReturn(new BigDecimal("202605.0000"));
        when(mapper.selectClosedMonths()).thenReturn(List.of());
        assertThat(service.metadata().get("periodBasis"))
            .isEqualTo(new UserPreferencePeriodBasis.Basis("202605", "202605", List.of()));
    }

    @Test
    void missingOperatingBasisIsExplicitlyNullAndNeverCurrentMonth() {
        when(mapper.selectClosedMonths()).thenReturn(List.of());
        var meta = service.metadata();
        assertThat(meta).containsEntry("periodBasis", null);
        assertThat(meta.get("periodBasisError")).isEqualTo("기간 기준을 확인할 수 없습니다. 운영 기준값을 확인해 주세요.");
    }

    @Test
    void malformedBasisDoesNotFabricateAValidMonth() {
        when(mapper.selectCashBaseYm()).thenReturn(new BigDecimal("202613"));
        when(mapper.selectClosedMonths()).thenReturn(List.of());
        assertThat(service.metadata()).containsEntry("periodBasis", null);
    }

    @Test
    void databaseFailurePropagatesInsteadOfReturningAnExampleMonth() {
        when(mapper.selectCashBaseYm()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private"));
        assertThatThrownBy(service::metadata).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
    }
}
