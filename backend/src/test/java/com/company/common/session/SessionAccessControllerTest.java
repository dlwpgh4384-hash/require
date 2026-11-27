package com.company.common.session;

import com.company.common.commonops.AccountAccessConfiguration;
import com.company.common.commonops.mapper.AccountAccessMapper;
import com.company.common.commonops.mapper.AccountAccessMapper.Account;
import com.company.common.commonops.service.AccountAccessService;
import com.company.common.commonops.service.AccountApiPolicy;
import com.company.common.commonops.service.AccountLoginCompletion;
import com.company.common.mfa.service.CommonMfaService;
import com.company.common.session.controller.CommonSessionController;
import com.company.common.session.controller.SessionResponseAdvice;
import com.company.common.session.mapper.SessionMapper;
import com.company.common.session.service.SessionPolicy;
import com.company.common.support.auth.AccountSessions;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.users.mapper.UserMapper;
import com.company.common.users.service.TemporaryUserPassword;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.company.common.support.controller.OpenApiResponseContract.response;

@WebMvcTest(CommonSessionController.class)
@ContextConfiguration(classes = {CommonSessionController.class, SessionPolicy.class,
    SessionResponseAdvice.class, SessionWebConfiguration.class, SessionSecurityConfiguration.class,
    AccountAccessConfiguration.class, AccountAccessService.class, AccountLoginCompletion.class,
    AccountApiPolicy.class, AccountSessions.class, SessionAccessControllerTest.Transactions.class})
class SessionAccessControllerTest {
    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");
    private static final String REMAINING = "/api/common/session/remaining";
    private static final String EXTEND = "/api/common/session/extend";
    @Autowired MockMvc mvc;
    @Autowired SessionPolicy policy;
    @Autowired AccountSessions sessions;
    @MockBean SessionMapper mapper;
    @MockBean AccountAccessMapper accounts;
    @MockBean UserMapper users;
    @MockBean TemporaryUserPassword passwords;
    @MockBean CommonMfaService mfa;
    @MockBean BusinessCalendar calendar;
    Account account;
    MockHttpSession session;

    @BeforeEach void setup() {
        when(calendar.now()).thenReturn(NOW);
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 1));
        when(calendar.toDatabaseMonth(any())).thenReturn("202601");
        when(mapper.selectSessionMinutes("202601")).thenReturn(new BigDecimal("30.0000"));
        when(mapper.selectAccountStatus("reader")).thenReturn("USE");
        when(mapper.selectTemporaryPassword("reader")).thenReturn("N");
        account = new Account();
        account.userId = "reader";
        account.hash = "session-test-credential";
        account.status = "USE";
        account.temporary = "N";
        when(accounts.account("reader")).thenAnswer(call -> account);
        when(accounts.roles("reader")).thenReturn(List.of());
        when(accounts.permissions("reader")).thenReturn(List.of());
        session = device();
        clearInvocations(mapper);
    }

    MockHttpSession device() {
        var device = new MockHttpSession();
        sessions.bind(device, account.userId, account.hash, false, "127.0.0.1");
        device.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
            new UsernamePasswordAuthenticationToken(account.userId, null, List.of())));
        return device;
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    MockHttpServletRequestBuilder extend() {
        return post(EXTEND).servletPath(EXTEND).session(session)
            .cookie(new Cookie("XSRF-TOKEN", "session-test-csrf"))
            .header("X-XSRF-TOKEN", "session-test-csrf");
    }

    @Test void zeroRoleActorMayReadThroughAuthoritativeChainWithSecurityHeaders() throws Exception {
        when(calendar.now()).thenReturn(NOW.plusSeconds(75));
        mvc.perform(get(REMAINING).servletPath(REMAINING).session(session))
            .andExpect(status().isOk()).andExpect(response("GET", REMAINING))
            .andExpect(jsonPath("$.data.remainingSeconds").value(1725))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Referrer-Policy", "same-origin"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        verify(accounts).account("reader");
    }

    @Test void extensionRestartsDatabaseDurationAndFollowingReadDoesNotExtend() throws Exception {
        when(calendar.now()).thenReturn(NOW.plusSeconds(120));
        mvc.perform(extend()).andExpect(status().isOk()).andExpect(response("POST", EXTEND))
            .andExpect(jsonPath("$.data.remainingSeconds").value(1800))
            .andExpect(jsonPath("$.data.serverRespondedAt").value(NOW.plusSeconds(120).toString()));
        when(calendar.now()).thenReturn(NOW.plusSeconds(130));
        mvc.perform(get(REMAINING).servletPath(REMAINING).session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.remainingSeconds").value(1790));
        verify(mapper, times(1)).selectSessionMinutes("202601");
    }

    @Test void expiredExtensionCannotRecreateSessionThroughAuthoritativeChain() throws Exception {
        when(calendar.now()).thenReturn(NOW.plusSeconds(1800));
        var result = mvc.perform(extend()).andExpect(status().isUnauthorized()).andReturn();
        assertThat(session.isInvalid()).isTrue();
        assertThat(result.getRequest().getSession(false)).isNull();
        verify(mapper, never()).selectSessionMinutes(anyString());
    }

    @Test void missingCsrfDoesNotReachExtensionOrChangeDeadline() throws Exception {
        when(calendar.now()).thenReturn(NOW.plusSeconds(90));
        mvc.perform(post(EXTEND).servletPath(EXTEND).session(session))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
            .andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(mapper);
        mvc.perform(get(REMAINING).servletPath(REMAINING).session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.remainingSeconds").value(1710));
    }

    @Test void stoppedAccountEndsEveryDeviceBeforeExtension() throws Exception {
        var other = device();
        clearInvocations(mapper);
        account.status = "STOP";
        mvc.perform(extend()).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
        assertThat(other.isInvalid()).isTrue();
        verifyNoInteractions(mapper);
    }

    @Test void lockedAccountCannotExtend() throws Exception {
        account.status = "LOCK";
        mvc.perform(extend()).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
        verifyNoInteractions(mapper);
    }

    @Test void temporaryPasswordBindingCannotExtendOrLoseItsChangeSession() throws Exception {
        sessions.current(session).passwordChangeRequired = true;
        mvc.perform(extend()).andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();
        verifyNoInteractions(mapper);
    }

    @Test void authenticatedBindingStartsAtLoginRatherThanPreAuthenticationSessionCreation() throws Exception {
        var older = new MockHttpSession() {
            @Override public long getCreationTime() { return NOW.minusSeconds(3600).toEpochMilli(); }
        };
        sessions.bind(older, account.userId, account.hash, false, "127.0.0.1");
        older.setAttribute("SPRING_SECURITY_CONTEXT", session.getAttribute("SPRING_SECURITY_CONTEXT"));
        mvc.perform(get(REMAINING).servletPath(REMAINING).session(older))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.remainingSeconds").value(1800));
    }

    @Test void inactiveControlKeepsDeadlineWhileExtensionUpdatesResponseTime() throws Exception {
        var control = device();
        when(calendar.now()).thenReturn(NOW.plusSeconds(100));
        mvc.perform(extend()).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.serverRespondedAt").value(NOW.plusSeconds(100).toString()));
        mvc.perform(get(REMAINING).servletPath(REMAINING).session(control))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.remainingSeconds").value(1700));
    }

    @Test void responseSamplesElapsedHandlerTimeWithoutRenewingAgain() throws Exception {
        when(calendar.now()).thenReturn(NOW.plusSeconds(120), NOW.plusSeconds(125));
        mvc.perform(extend()).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.remainingSeconds").value(1795))
            .andExpect(jsonPath("$.meta.remainingSeconds").value(1795))
            .andExpect(jsonPath("$.data.serverRespondedAt").value(NOW.plusSeconds(125).toString()))
            .andExpect(header().stringValues("X-Session-Remaining-Seconds", "1795"))
            .andExpect(header().stringValues("X-Session-Minutes", "30"))
            .andExpect(header().stringValues("X-Session-Server-Responded-At", NOW.plusSeconds(125).toString()));
        verify(mapper).selectSessionMinutes("202601");
    }

    @Test void changedSessionStandardComesFromDatabase() throws Exception {
        when(mapper.selectSessionMinutes("202601")).thenReturn(new BigDecimal("12.0000"));
        mvc.perform(extend()).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sessionMinutes").value(12))
            .andExpect(jsonPath("$.data.remainingSeconds").value(720));
        assertThat(session.getMaxInactiveInterval()).isEqualTo(720);
    }

    @Test void missingOperatingStandardIs400AndPreservesDeadline() throws Exception {
        when(mapper.selectSessionMinutes("202601")).thenReturn(null);
        when(calendar.now()).thenReturn(NOW.plusSeconds(60));
        mvc.perform(extend()).andExpect(status().isBadRequest());
        mvc.perform(get(REMAINING).servletPath(REMAINING).session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.remainingSeconds").value(1740));
    }

    @Test void anonymousReadDoesNotCreateSession() throws Exception {
        var result = mvc.perform(get(REMAINING).servletPath(REMAINING))
            .andExpect(status().isUnauthorized()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        verifyNoInteractions(mapper);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Transactions {
        @Bean AbstractPlatformTransactionManager sessionTestTransactions() {
            return new AbstractPlatformTransactionManager() {
                @Override protected Object doGetTransaction() { return new Object(); }
                @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
                @Override protected void doCommit(DefaultTransactionStatus status) { }
                @Override protected void doRollback(DefaultTransactionStatus status) { }
            };
        }
    }
}
