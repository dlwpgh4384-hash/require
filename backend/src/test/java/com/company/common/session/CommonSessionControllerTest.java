package com.company.common.session;

import com.company.common.session.controller.CommonSessionController;
import com.company.common.session.controller.SessionResponseAdvice;
import com.company.common.session.mapper.SessionMapper;
import com.company.common.session.service.SessionPolicy;
import com.company.common.support.service.BusinessCalendar;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.company.common.support.controller.OpenApiResponseContract.response;

@WebMvcTest(CommonSessionController.class)
@ContextConfiguration(classes = {CommonSessionController.class, SessionPolicy.class, SessionResponseAdvice.class,
    SessionSecurityConfiguration.class, SessionWebConfiguration.class})
class CommonSessionControllerTest {
    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");
    @Autowired MockMvc mvc;
    @Autowired SessionPolicy policy;
    @MockBean SessionMapper mapper;
    @MockBean BusinessCalendar calendar;
    MockHttpSession session;

    @BeforeEach void setup() throws Exception {
        var contract = new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8);
        assertThat(contract).contains("operationId: postCommonSessionExtend", "operationId: getCommonSessionRemaining");
        when(calendar.now()).thenReturn(NOW);
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 1));
        when(calendar.toDatabaseMonth(any())).thenReturn("202601");
        when(mapper.selectSessionMinutes("202601")).thenReturn(new BigDecimal("30.0000"));
        when(mapper.selectAccountStatus("reader")).thenReturn("USE");
        when(mapper.selectTemporaryPassword("reader")).thenReturn("N");
        session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
            new UsernamePasswordAuthenticationToken("reader", null, List.of())));
        policy.initialize(session, "reader");
    }

    private MockHttpServletRequestBuilder extend() {
        return post("/api/common/session/extend").session(session)
            .cookie(new Cookie("XSRF-TOKEN", "session-test-csrf"))
            .header("X-XSRF-TOKEN", "session-test-csrf");
    }

    @Test void remainingReturnsRealCountdownAndZeroRoleMayRead() throws Exception {
        when(calendar.now()).thenReturn(NOW.plusSeconds(75));
        mvc.perform(get("/api/common/session/remaining").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(response("GET", "/api/common/session/remaining"))
            .andExpect(jsonPath("$.data.remainingSeconds").value(1725))
            .andExpect(jsonPath("$.data.sessionMinutes").value(30))
            .andExpect(jsonPath("$.data.serverRespondedAt").value(NOW.plusSeconds(75).toString()))
            .andExpect(jsonPath("$.meta.remainingSeconds").value(1725))
            .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void extendRestartsConfiguredDurationAndFollowingRemainingDecreases() throws Exception {
        when(calendar.now()).thenReturn(NOW.plusSeconds(120));
        mvc.perform(extend()).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.remainingSeconds").value(1800));
        when(calendar.now()).thenReturn(NOW.plusSeconds(130));
        mvc.perform(get("/api/common/session/remaining").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.remainingSeconds").value(1790));
    }

    @Test
    @DisplayName("happy side-effect condition: SESSION_MIN 재조회·세션 시간 갱신·serverRespondedAt 반환")
    void extendReadsChangedDatabaseStandardInsteadOfLiteralThirty() throws Exception {
        when(mapper.selectSessionMinutes("202601")).thenReturn(new BigDecimal("12.0000"));
        clearInvocations(mapper);
        mvc.perform(post("/api/common/session/extend").session(session)
            .cookie(new Cookie("XSRF-TOKEN", "session-test-csrf"))
            .header("X-XSRF-TOKEN", "session-test-csrf")).andExpect(status().isOk())
            .andExpect(response("POST", "/api/common/session/extend"))
            .andExpect(jsonPath("$.data.sessionMinutes").value(12))
            .andExpect(jsonPath("$.data.remainingSeconds").value(720))
            .andExpect(jsonPath("$.data.serverRespondedAt").value(NOW.toString()));
        assertThat(session.getMaxInactiveInterval()).isEqualTo(720);
        verify(mapper).selectSessionMinutes("202601");
        verify(mapper).selectAccountStatus("reader");
        verify(mapper).selectTemporaryPassword("reader");
    }

    @Test void expiredExtendIs401AndNeverRecreatesSession() throws Exception {
        when(calendar.now()).thenReturn(NOW.plusSeconds(1800));
        var result = mvc.perform(extend()).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false)).andReturn();
        assertThat(session.isInvalid()).isTrue();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test void missingCsrfIs403AndDoesNotRefresh() throws Exception {
        when(calendar.now()).thenReturn(NOW.plusSeconds(90));
        mvc.perform(post("/api/common/session/extend").session(session))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.meta").isMap());
        assertThat(session.isInvalid()).isFalse();
        mvc.perform(get("/api/common/session/remaining").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.remainingSeconds").value(1710));
    }

    @Test void mismatchedCsrfCannotExtend() throws Exception {
        when(calendar.now()).thenReturn(NOW.plusSeconds(100));
        mvc.perform(post("/api/common/session/extend").session(session)
            .cookie(new Cookie("XSRF-TOKEN", "session-test-csrf"))
            .header("X-XSRF-TOKEN", "incorrect"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/common/session/remaining").session(session))
            .andExpect(jsonPath("$.data.remainingSeconds").value(1700));
    }

    @Test
    @DisplayName("business condition: STOP 사용자의 연장은 401·세션 종료·SESSION_MIN 재설정 없음")
    void stoppedAccountEndsSessionBeforeExtension() throws Exception {
        when(mapper.selectAccountStatus("reader")).thenReturn("STOP");
        clearInvocations(mapper);
        mvc.perform(post("/api/common/session/extend").session(session)
            .cookie(new Cookie("XSRF-TOKEN", "session-test-csrf"))
            .header("X-XSRF-TOKEN", "session-test-csrf"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        assertThat(session.isInvalid()).isTrue();
        verify(mapper, never()).selectSessionMinutes(anyString());
    }

    @Test void lockedAccountCannotExtend() throws Exception {
        when(mapper.selectAccountStatus("reader")).thenReturn("LOCK");
        mvc.perform(extend()).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void removedAccountCannotReadRemaining() throws Exception {
        when(mapper.selectAccountStatus("reader")).thenReturn(null);
        mvc.perform(get("/api/common/session/remaining").session(session))
            .andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void temporaryPasswordCannotExtendButDoesNotLoseChangeSession() throws Exception {
        when(mapper.selectTemporaryPassword("reader")).thenReturn("Y");
        mvc.perform(extend()).andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();
    }

    @Test void temporaryPasswordCannotReadProtectedRemaining() throws Exception {
        when(mapper.selectTemporaryPassword("reader")).thenReturn("Y");
        mvc.perform(get("/api/common/session/remaining").session(session))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("side-effect condition: serverRespondedAt은 새 응답 시각·요청 없는 대조 세션은 연장 없음")
    void extensionUpdatesServerTimeWhileInactiveControlDoesNotRestart() throws Exception {
        var control = new MockHttpSession();
        control.setAttribute("SPRING_SECURITY_CONTEXT", session.getAttribute("SPRING_SECURITY_CONTEXT"));
        policy.initialize(control, "reader");
        when(calendar.now()).thenReturn(NOW.plusSeconds(100));
        mvc.perform(post("/api/common/session/extend").session(session)
            .cookie(new Cookie("XSRF-TOKEN", "session-test-csrf"))
            .header("X-XSRF-TOKEN", "session-test-csrf")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.remainingSeconds").value(1800))
            .andExpect(jsonPath("$.data.serverRespondedAt").value(NOW.plusSeconds(100).toString()));
        mvc.perform(get("/api/common/session/remaining").session(control))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.remainingSeconds").value(1700));
    }

    @Test void remainingDoesNotKeepIdleSessionAlive() throws Exception {
        when(calendar.now()).thenReturn(NOW.plusSeconds(1799));
        mvc.perform(get("/api/common/session/remaining").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.remainingSeconds").value(1));
        when(calendar.now()).thenReturn(NOW.plusSeconds(1800));
        mvc.perform(get("/api/common/session/remaining").session(session))
            .andExpect(status().isUnauthorized());
    }

    @Test void anonymousRemainingIs401WithoutSessionCreation() throws Exception {
        var result = mvc.perform(get("/api/common/session/remaining"))
            .andExpect(status().isUnauthorized()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test void anonymousExtendIs401WithoutSessionCreation() throws Exception {
        var result = mvc.perform(post("/api/common/session/extend"))
            .andExpect(status().isUnauthorized()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test void invalidOperatingStandardReturns400WithoutRefresh() throws Exception {
        when(mapper.selectSessionMinutes("202601")).thenReturn(new BigDecimal("0"));
        when(calendar.now()).thenReturn(NOW.plusSeconds(60));
        mvc.perform(extend()).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("세션 운영 기준값을 확인해 주세요."));
        mvc.perform(get("/api/common/session/remaining").session(session))
            .andExpect(jsonPath("$.data.remainingSeconds").value(1740));
    }
}
