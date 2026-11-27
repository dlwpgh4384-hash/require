package com.company.common.notifications;

import com.company.common.commonops.AccountAccessConfiguration;
import com.company.common.commonops.mapper.AccountAccessMapper;
import com.company.common.commonops.service.AccountAccessService;
import com.company.common.commonops.service.AccountApiPolicy;
import com.company.common.commonops.service.AccountLoginCompletion;
import com.company.common.mfa.service.CommonMfaService;
import com.company.common.notifications.controller.CommonNotificationController;
import com.company.common.notifications.controller.NotificationExceptionHandler;
import com.company.common.notifications.mapper.NotificationMapper;
import com.company.common.notifications.service.NotificationService;
import com.company.common.support.auth.AccountSessions;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.users.service.TemporaryUserPassword;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import static com.company.common.notifications.NotificationTestData.NOW;
import static com.company.common.support.controller.OpenApiResponseContract.response;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonNotificationController.class)
@ContextConfiguration(classes = {CommonNotificationController.class, NotificationService.class,
    NotificationExceptionHandler.class, ApiExceptionHandler.class, AccountAccessConfiguration.class,
    AccountAccessService.class, AccountApiPolicy.class, AccountSessions.class, NotificationSecurityConfiguration.class})
class NotificationAccountSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired AccountSessions sessions;
    @MockBean NotificationMapper mapper;
    @MockBean AccountAccessMapper accounts;
    @MockBean BusinessCalendar calendar;
    @MockBean TemporaryUserPassword passwords;
    @MockBean AccountLoginCompletion completion;
    @MockBean CommonMfaService mfa;
    @MockBean PlatformTransactionManager transactions;
    NotificationTestData data;
    MockHttpSession session;
    AccountSessions.Binding binding;
    final Cookie csrf = new Cookie("XSRF-TOKEN", "notification-contract-token");

    @BeforeEach void setup() {
        when(calendar.now()).thenReturn(NOW);
        data = new NotificationTestData(mapper);
        var account = new AccountAccessMapper.Account();
        account.userId = "reader";
        account.status = "USE";
        account.temporary = "N";
        account.hash = "test-credential-fingerprint";
        when(accounts.account("reader")).thenReturn(account);
        when(accounts.roles("reader")).thenReturn(List.of());
        when(accounts.permissions("reader")).thenReturn(List.of());
        session = new MockHttpSession();
        binding = sessions.bind(session, "reader", account.hash, false, "127.0.0.1");
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    private MockHttpServletRequestBuilder write(String path) {
        return put(path).servletPath(path).session(session).cookie(csrf)
            .header("X-XSRF-TOKEN", csrf.getValue());
    }
    private MockHttpServletRequestBuilder listing() {
        return get("/api/common/notifications").servletPath("/api/common/notifications").session(session);
    }

    @Test void zeroRoleAccountListsOwnNotificationsThroughEffectiveChain() throws Exception {
        mvc.perform(listing()).andExpect(status().isOk())
            .andExpect(response("GET", "/api/common/notifications"))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].notificationId").value(2))
            .andExpect(jsonPath("$.data.unreadCount").value(2))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Referrer-Policy", "same-origin"))
            .andExpect(header().string("Cache-Control", "no-store"));
        verify(accounts).account("reader");
        verify(accounts).permissions("reader");
    }

    @Test void readAllRereadsBothOwnRowsAsRead() throws Exception {
        mvc.perform(write("/api/common/notifications/read-all")).andExpect(status().isNoContent());
        mvc.perform(listing()).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].readYn").value("Y"))
            .andExpect(jsonPath("$.data.items[1].readYn").value("Y"))
            .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test void anonymousReadAllIs401AndAllRowsStayUnchanged() throws Exception {
        mvc.perform(put("/api/common/notifications/read-all").servletPath("/api/common/notifications/read-all"))
            .andExpect(status().isUnauthorized());
        verify(mapper, never()).readAll(any());
        assertThat(data.rows.values()).allSatisfy(row -> {
            assertThat(row.readYn).isEqualTo("N");
            assertThat(row.readAt).isNull();
        });
    }

    @Test void readAllWithoutCsrfIs403BeforeWrites() throws Exception {
        mvc.perform(put("/api/common/notifications/read-all").servletPath("/api/common/notifications/read-all")
            .session(session)).andExpect(status().isForbidden());
        verify(accounts).permissions("reader");
        verify(mapper, never()).readAll(any());
        assertThat(data.rows.get(1L).readYn).isEqualTo("N");
    }

    @Test void readAllLeavesOtherRecipientUnchanged() throws Exception {
        mvc.perform(write("/api/common/notifications/read-all")).andExpect(status().isNoContent());
        assertThat(data.rows.get(3L).readYn).isEqualTo("N");
        assertThat(data.rows.get(3L).readAt).isNull();
    }

    @Test void readAllSetsOwnReadTimeWithoutRedirect() throws Exception {
        mvc.perform(write("/api/common/notifications/read-all")).andExpect(status().isNoContent())
            .andExpect(header().doesNotExist("Location")).andExpect(content().string(""));
        assertThat(data.rows.get(1L).readAt).isEqualTo(NOW);
        assertThat(data.rows.get(2L).readAt).isEqualTo(NOW);
    }

    @Test void singleReadPersistsReadStateAndDecreasesCountWithoutDetail() throws Exception {
        mvc.perform(write("/api/common/notifications/1/read")).andExpect(status().isOk())
            .andExpect(response("PUT", "/api/common/notifications/{notificationId}/read"))
            .andExpect(jsonPath("$.data.readAt").value(NOW.toString()))
            .andExpect(jsonPath("$.meta.navigation.screenId").value("UI-13"))
            .andExpect(jsonPath("$.meta.navigation.targetKey").doesNotExist());
        mvc.perform(listing()).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[1].readYn").value("Y"))
            .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test void anotherRecipientCannotBeMarkedRead() throws Exception {
        mvc.perform(write("/api/common/notifications/3/read")).andExpect(status().isForbidden());
        verify(mapper, never()).read(any());
        assertThat(data.rows.get(3L).readYn).isEqualTo("N");
        assertThat(data.rows.get(3L).readAt).isNull();
    }

    @Test void nonIntegerIdIs400FieldErrorWithoutWrites() throws Exception {
        mvc.perform(write("/api/common/notifications/invalid/read")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("notificationId"));
        verify(mapper, never()).read(any());
        verify(mapper, never()).lockNotification(any());
    }

    @Test void revokedPermissionStillMarksReadButNeverDisclosesDestinationOrText() throws Exception {
        data.access.get(1L).readAllowed = false;
        mvc.perform(write("/api/common/notifications/1/read")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.readYn").value("Y"))
            .andExpect(jsonPath("$.meta.navigation.status").value("DENIED"))
            .andExpect(jsonPath("$.meta.navigation.screenId").doesNotExist());
        mvc.perform(listing()).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[1].content").value(NotificationService.HIDDEN))
            .andExpect(jsonPath("$.data.items[1].targetKey").doesNotExist())
            .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test void temporaryPasswordRestrictionIsEnforcedByAccountChain() throws Exception {
        binding.passwordChangeRequired = true;
        mvc.perform(write("/api/common/notifications/read-all")).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("비밀번호를 먼저 변경해 주세요."));
        verify(mapper, never()).readAll(any());
    }
}
