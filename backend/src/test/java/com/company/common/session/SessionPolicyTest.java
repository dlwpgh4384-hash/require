package com.company.common.session;

import com.company.common.session.mapper.SessionMapper;
import com.company.common.session.service.SessionFailure;
import com.company.common.session.service.SessionPolicy;
import com.company.common.support.service.BusinessCalendar;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SessionPolicyTest {
    private static final Instant NOW = Instant.parse("2026-01-02T00:00:00Z");
    SessionMapper mapper;
    BusinessCalendar calendar;
    SessionPolicy policy;
    MockHttpSession session;

    @BeforeEach void setup() {
        mapper = mock(SessionMapper.class);
        calendar = mock(BusinessCalendar.class);
        when(calendar.now()).thenReturn(NOW);
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 1));
        when(calendar.toDatabaseMonth(any())).thenReturn("202601");
        when(mapper.selectSessionMinutes("202601")).thenReturn(new BigDecimal("30.0000"));
        when(mapper.selectAccountStatus("reader")).thenReturn("USE");
        when(mapper.selectTemporaryPassword("reader")).thenReturn("N");
        policy = new SessionPolicy(mapper, calendar);
        session = new MockHttpSession();
        policy.initialize(session, "reader");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("reader", null, List.of()));
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    private MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setSession(session);
        return request;
    }

    @Test void ordinaryUserServerRequestRestartsIdleWindow() {
        when(calendar.now()).thenReturn(NOW.plusSeconds(600));
        var activity = policy.onRequest(request("/api/common/my-info"), true);
        assertThat(activity.remainingSeconds()).isEqualTo(1800);
        when(calendar.now()).thenReturn(NOW.plusSeconds(650));
        assertThat(policy.onRequest(request("/api/common/session/remaining"), false).remainingSeconds())
            .isEqualTo(1750);
    }

    @Test void inactivityEndsSessionAtExactBoundaryWithoutANewRequest() {
        when(calendar.now()).thenReturn(NOW.plusSeconds(1800));
        assertThatThrownBy(() -> policy.onRequest(request("/api/common/session/extend"), true))
            .isInstanceOf(SessionFailure.class).extracting("status").isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void scopeAndRoleAreNotNeededForOwnSession() {
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).isEmpty();
        assertThat(policy.onRequest(request("/api/common/session/remaining"), false).remainingSeconds())
            .isEqualTo(1800);
    }

    @Test void responseTimestampSubtractsHandlerTimeWithoutRefreshingOrReadingDatabase() {
        var request = request("/api/common/my-info");
        when(calendar.now()).thenReturn(NOW.plusSeconds(60));
        policy.onRequest(request, true);
        clearInvocations(mapper);
        when(calendar.now()).thenReturn(NOW.plusSeconds(65));
        var response = SessionPolicy.responded(request);
        assertThat(response.remainingSeconds()).isEqualTo(1795);
        assertThat(response.serverRespondedAt()).isEqualTo(NOW.plusSeconds(65));
        verifyNoInteractions(mapper);
    }

    @Test void responseSamplingDoesNotRecreateAnEndedSession() {
        var request = request("/api/common/session/remaining");
        policy.onRequest(request, false);
        session.invalidate();
        clearInvocations(mapper);
        assertThat(SessionPolicy.responded(request)).isNull();
        assertThat(request.getSession(false)).isNull();
        verifyNoInteractions(mapper);
    }

    @Test void missingStandardIsNotReplacedWithThirtyMinutes() {
        when(mapper.selectSessionMinutes("202601")).thenReturn(null);
        assertThatThrownBy(() -> policy.onRequest(request("/api/common/session/extend"), true))
            .isInstanceOf(SessionFailure.class).extracting("status").isEqualTo(400);
    }

    @Test void fractionalStandardIsRejectedRatherThanTruncated() {
        when(mapper.selectSessionMinutes("202601")).thenReturn(new BigDecimal("2.5"));
        assertThatThrownBy(() -> policy.onRequest(request("/api/common/session/extend"), true))
            .isInstanceOf(SessionFailure.class).extracting("status").isEqualTo(400);
    }

    @Test void passwordRestrictedSessionMayChangePasswordButNotExtend() {
        when(mapper.selectTemporaryPassword("reader")).thenReturn("Y");
        var change = new MockHttpServletRequest("PUT", "/api/common/my-password");
        change.setSession(session);
        assertThat(policy.onRequest(change, true).remainingSeconds()).isEqualTo(1800);
        assertThatThrownBy(() -> policy.onRequest(request("/api/common/session/extend"), true))
            .isInstanceOf(SessionFailure.class).extracting("status").isEqualTo(403);
    }
}
