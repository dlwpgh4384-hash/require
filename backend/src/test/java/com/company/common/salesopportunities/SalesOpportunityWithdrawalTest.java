package com.company.common.salesopportunities;

import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.support.service.*;
import com.company.common.withdrawals.mapper.WithdrawalMapper;
import com.company.common.withdrawals.mapper.WithdrawalRows.*;
import com.company.common.withdrawals.service.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesOpportunityWithdrawalTest {
    @Test void sharedWithdrawalViewAcceptsSeveralNeedsOfOneOpportunityWithoutMutation() {
        var mapper = mock(WithdrawalMapper.class);
        var projects = mock(ProjectMapper.class);
        var recommendations = mock(WithdrawalRecommendationService.class);
        var calendar = mock(BusinessCalendar.class);
        var service = new WithdrawalTentativeService(mapper, projects, recommendations, calendar, new CurrentBusinessActor());
        var source = new Source("assignment", "developer", "개발자", "프로젝트", "BE", "백엔드",
            LocalDate.of(2026, 11, 30), "L", 0, Instant.parse("2026-11-15T00:00:00Z"), "ACT", "BE", "MD", "customer", "중급", "project", "number");
        when(recommendations.required("assignment")).thenReturn(source);
        when(mapper.reservations("developer")).thenReturn(List.of(
            new Reservation("1", "developer", "opportunity", "기회", 3, "202612", "ACT"),
            new Reservation("2", "developer", "opportunity", "기회", 7, "202612", "ACT")));
        when(calendar.fromDatabaseMonth("202612")).thenReturn(YearMonth.of(2026, 12));
        assertThat(service.current("assignment")).isNotNull();
        verify(mapper, never()).release(anyString(), anyString());
        verify(mapper, never()).insertReservation(any());
        verify(mapper, never()).touchOpportunity(anyString(), anyString());
    }
}
