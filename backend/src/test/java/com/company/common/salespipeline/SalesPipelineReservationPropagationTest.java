package com.company.common.salespipeline;

import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.support.service.*;
import com.company.common.withdrawals.mapper.WithdrawalMapper;
import com.company.common.withdrawals.mapper.WithdrawalRows.*;
import com.company.common.withdrawals.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesPipelineReservationPropagationTest {
    WithdrawalMapper mapper;
    WithdrawalRecommendationService recommendations;
    WithdrawalTentativeService service;
    Source source;
    @BeforeEach void setup() {
        mapper = mock(WithdrawalMapper.class); recommendations = mock(WithdrawalRecommendationService.class);
        source = mock(Source.class); when(source.assignmentId()).thenReturn("assignment"); when(source.developerId()).thenReturn("developer");
        when(source.endDate()).thenReturn(LocalDate.of(2026, 11, 30));
        when(recommendations.required("assignment")).thenReturn(source);
        when(mapper.lockSource("assignment")).thenReturn(source); when(mapper.lockDeveloper("developer")).thenReturn("developer");
        service = new WithdrawalTentativeService(mapper, mock(ProjectMapper.class), recommendations, new BusinessCalendar(), new CurrentBusinessActor());
    }
    @Test void withdrawalDisplayToleratesMultipleNeedsInSameOpportunity() {
        when(mapper.reservations("developer")).thenReturn(List.of(
            new Reservation("1", "developer", "opportunity", "기회", 1, "202612", "ACT"),
            new Reservation("2", "developer", "opportunity", "기회", 2, "202612", "ACT")));
        assertThatCode(() -> service.current("assignment")).doesNotThrowAnyException();
        verify(mapper, never()).touchOpportunity(anyString(), anyString());
    }
    @Test void withdrawalTransferTouchesSourceAndDestinationOnlyAfterReservationWrites() {
        var old = new Reservation("1", "developer", "source", "이전 기회", 1, "202612", "ACT");
        var saved = new Reservation("2", "developer", "destination", "새 기회", 2, "202612", "ACT");
        when(mapper.reservations("developer")).thenReturn(List.of(old), List.of(saved));
        var context = mock(WithdrawalRecommendationService.Context.class);
        var rules = mock(WithdrawalCalculationRules.Settings.class);
        var target = mock(Need.class);
        when(target.opportunityId()).thenReturn("destination"); when(target.needSn()).thenReturn(2);
        when(target.startMonth()).thenReturn("202612"); when(target.requiredCount()).thenReturn(2);
        when(context.needs()).thenReturn(List.of(target)); when(context.rules()).thenReturn(rules);
        when(recommendations.context()).thenReturn(context);
        when(recommendations.eligible(source, context)).thenReturn(true); when(recommendations.matches(source, target, context)).thenReturn(true);
        when(rules.tentativeStartMonth(LocalDate.of(2026, 11, 30), YearMonth.of(2026, 12))).thenReturn(YearMonth.of(2026, 12));
        when(mapper.release("1", "admin")).thenReturn(1); when(mapper.insertReservation(any())).thenReturn(1);
        var body = new ObjectMapper().createObjectNode().put("opportunityId", "destination").put("needSn", 2);
        service.save("assignment", body);
        var order = inOrder(mapper);
        order.verify(mapper).release("1", "admin"); order.verify(mapper).insertReservation(any());
        order.verify(mapper).touchOpportunity("destination", "admin"); order.verify(mapper).touchOpportunity("source", "admin");
    }
    @Test void convertedCandidateCannotBeReleasedOrTouchOpportunity() {
        when(mapper.reservations("developer")).thenReturn(List.of(new Reservation("1", "developer", "source", "이전", 1, "202612", "CNVT")));
        var context = mock(WithdrawalRecommendationService.Context.class); var target = mock(Need.class);
        when(target.opportunityId()).thenReturn("destination"); when(target.needSn()).thenReturn(2);
        when(context.needs()).thenReturn(List.of(target)); when(recommendations.context()).thenReturn(context);
        var body = new ObjectMapper().createObjectNode().put("opportunityId", "destination").put("needSn", 2);
        assertThatThrownBy(() -> service.save("assignment", body)).isInstanceOf(CodeFailure.class).extracting("status").isEqualTo(409);
        verify(mapper, never()).release(anyString(), anyString()); verify(mapper, never()).touchOpportunity(anyString(), anyString());
    }
}
