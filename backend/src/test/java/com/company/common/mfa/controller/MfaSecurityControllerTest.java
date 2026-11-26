package com.company.common.mfa.controller;

import com.company.common.bills.service.BillLocalMailbox;
import com.company.common.commonops.AccountAccessConfiguration;
import com.company.common.commonops.mapper.AccountAccessMapper;
import com.company.common.commonops.service.AccountAccessFailure;
import com.company.common.commonops.service.AccountAccessService;
import com.company.common.commonops.service.AccountApiPolicy;
import com.company.common.mfa.mapper.MfaMapper;
import com.company.common.mfa.mapper.MfaRows.Account;
import com.company.common.mfa.service.CommonMfaService;
import com.company.common.support.auth.LoginCompletion;
import com.company.common.support.service.BusinessCalendar;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static com.company.common.support.controller.OpenApiResponseContract.response;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonMfaController.class)
@ContextConfiguration(classes = {
    CommonMfaController.class, MfaExceptionHandler.class, CommonMfaService.class,
    AccountAccessConfiguration.class, MfaSecurityControllerTest.Transactions.class
})
class MfaSecurityControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CommonMfaService service;
    @MockBean MfaMapper mapper;
    @MockBean BillLocalMailbox mailbox;
    @MockBean BusinessCalendar calendar;
    @MockBean LoginCompletion completion;
    @MockBean AccountAccessService accounts;
    @MockBean AccountAccessMapper accountMapper;
    @MockBean AccountApiPolicy policy;
    final Cookie csrf = new Cookie("XSRF-TOKEN", "mfa-contract-csrf");
    Instant now;
    Account account;
    String deliveredCode;

    @TestConfiguration(proxyBeanMethods = false)
    static class Transactions {
        @Bean AbstractPlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override protected Object doGetTransaction() { return new Object(); }
                @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
                @Override protected void doCommit(DefaultTransactionStatus status) { }
                @Override protected void doRollback(DefaultTransactionStatus status) { }
            };
        }
    }

    @BeforeEach void setup() {
        now = Instant.parse("2026-01-02T00:00:00Z");
        when(calendar.now()).thenAnswer(call -> now);
        when(accounts.authenticated(any())).thenThrow(AccountAccessFailure.unauthorized());
        when(completion.prepare(anyString(), anyBoolean())).thenReturn(
            new LoginCompletion.Prepared("UI-34", Map.of("passwordChangeRequired", false)));
        account = new Account();
        account.userId = "mfa-" + java.util.UUID.randomUUID().toString().substring(0, 12);
        account.email = "mfa-security@example.invalid";
        account.status = "USE";
        account.temporary = "N";
        account.administrator = true;
        when(mapper.lockAccount(account.userId)).thenReturn(account);
        doAnswer(call -> {
            account.codeHash = call.getArgument(1);
            account.expiresAt = call.getArgument(2);
            return null;
        }).when(mapper).challenge(eq(account.userId), anyString(), nullable(Instant.class));
        doAnswer(call -> { account.errors++; return null; }).when(mapper).wrongCode(account.userId);
        doAnswer(call -> {
            account.codeHash = null;
            account.expiresAt = null;
            return null;
        }).when(mapper).clear(account.userId);
        when(mailbox.deliver(anyList(), anyString())).thenAnswer(call -> {
            var match = Pattern.compile("인증번호: ([0-9]{6})").matcher((String) call.getArgument(1));
            assertThat(match.find()).isTrue();
            deliveredCode = match.group(1);
            return Path.of("mfa-security-delivery");
        });
    }

    MockHttpSession start() {
        var request = new MockHttpServletRequest();
        service.startAfterPasswordVerified(request, account.userId);
        return (MockHttpSession) request.getSession(false);
    }

    MockHttpServletRequestBuilder postWithCsrf(String path) {
        return post(path).servletPath(path).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue());
    }

    MockHttpServletRequestBuilder verifyCode(MockHttpSession session, String code) throws Exception {
        return postWithCsrf("/api/common/mfa/verify").session(session)
            .contentType("application/json").content(json.writeValueAsString(Map.of("code", code)));
    }

    @Test void resendRequiresCsrfEvenWithPendingAdministrator() throws Exception {
        var pending = start();
        clearInvocations(mapper, mailbox);
        mvc.perform(post("/api/common/mfa/resend").servletPath("/api/common/mfa/resend").session(pending))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(mapper, mailbox, completion);
    }

    @Test void verifyRequiresCsrfEvenWithCorrectCode() throws Exception {
        var pending = start();
        clearInvocations(mapper, mailbox);
        mvc.perform(post("/api/common/mfa/verify").servletPath("/api/common/mfa/verify")
            .session(pending).contentType("application/json")
            .content(json.writeValueAsString(Map.of("code", deliveredCode))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(mapper, mailbox, completion);
    }

    @Test void resendWithoutPendingIsUnauthorizedAndHasNoSideEffects() throws Exception {
        mvc.perform(postWithCsrf("/api/common/mfa/resend"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(mapper, mailbox, completion);
    }

    @Test void verifyWithoutPendingIsUnauthorizedAndHasNoSideEffects() throws Exception {
        mvc.perform(verifyCode(new MockHttpSession(), "123456"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.meta.nextScreenId").value("UI-22"));
        verifyNoInteractions(mapper, mailbox, completion);
    }

    @Test void pendingAdministratorCannotEnterBusinessApi() throws Exception {
        var pending = start();
        mvc.perform(get("/api/common/my-info").servletPath("/api/common/my-info").session(pending))
            .andExpect(status().isUnauthorized());
        verify(accounts).authenticated(any());
        verifyNoInteractions(completion, policy);
    }

    @Test void fiveDigitCodeUsesExactFieldMessageWithoutConsumingAttempt() throws Exception {
        var pending = start();
        clearInvocations(mapper, mailbox);
        mvc.perform(verifyCode(pending, "12345"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("code"))
            .andExpect(jsonPath("$.errors[0].message").value(CommonMfaService.CODE_LENGTH));
        verifyNoInteractions(mapper, mailbox, completion);
    }

    @Test void resendBeforeSixtySecondsPreservesChallenge() throws Exception {
        var pending = start();
        String hash = account.codeHash;
        now = now.plusSeconds(59);
        clearInvocations(mapper, mailbox);
        mvc.perform(postWithCsrf("/api/common/mfa/resend").session(pending))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(CommonMfaService.RESEND_WAIT));
        assertThat(account.codeHash).isEqualTo(hash);
        verify(mapper, never()).challenge(anyString(), anyString(), nullable(Instant.class));
        verifyNoInteractions(mailbox, completion);
    }

    @Test void resendReplacesCodeAndOnlyNewCodeCompletesLogin() throws Exception {
        var pending = start();
        String previous = deliveredCode;
        String previousHash = account.codeHash;
        now = now.plusSeconds(60);
        mvc.perform(postWithCsrf("/api/common/mfa/resend").session(pending))
            .andExpect(status().isOk()).andExpect(response("POST", "/api/common/mfa/resend"))
            .andExpect(jsonPath("$.data.maskedEmail").value("m***@example.invalid"))
            .andExpect(jsonPath("$.data.expiresAt").value(now.plusSeconds(300).toString()))
            .andExpect(jsonPath("$.data.code").doesNotExist());
        assertThat(account.codeHash).isNotEqualTo(previousHash);
        mvc.perform(verifyCode(pending, previous)).andExpect(status().isBadRequest());
        verifyNoInteractions(completion);
        mvc.perform(verifyCode(pending, deliveredCode))
            .andExpect(status().isOk()).andExpect(response("POST", "/api/common/mfa/verify"))
            .andExpect(jsonPath("$.data.nextScreenId").value("UI-34"));
        verify(mapper).complete(account.userId, now);
        verify(mapper).loginAccess(eq(account.userId), eq(false), anyString(), eq(now));
        verify(completion).establish(any(), eq(account.userId), eq(false));
        mvc.perform(verifyCode(pending, deliveredCode)).andExpect(status().isUnauthorized());
    }

    @Test void expiredCodeDoesNotEstablishBusinessSession() throws Exception {
        var pending = start();
        now = now.plusSeconds(300);
        mvc.perform(verifyCode(pending, deliveredCode))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(CommonMfaService.EXPIRED));
        verifyNoInteractions(completion);
        verify(mapper, never()).complete(anyString(), any());
    }

    @Test void fifthWrongCodeEndsOnlyPendingLogin() throws Exception {
        var pending = start();
        String wrong = deliveredCode.equals("000000") ? "000001" : "000000";
        for (int attempt = 1; attempt < 5; attempt++) {
            mvc.perform(verifyCode(pending, wrong)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("인증번호가 맞지 않습니다. (" + attempt + "/5)"));
        }
        mvc.perform(verifyCode(pending, wrong)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.meta.nextScreenId").value("UI-22"));
        assertThat(account.status).isEqualTo("USE");
        assertThat(account.errors).isEqualTo(5);
        assertThat(pending.getAttribute(CommonMfaService.PENDING_ATTRIBUTE)).isNull();
        verifyNoInteractions(completion);
    }

    @Test void newerDeviceRejectsPreviousSessionEvenWithNewestCode() throws Exception {
        var previous = start();
        var latest = start();
        mvc.perform(verifyCode(previous, deliveredCode)).andExpect(status().isUnauthorized());
        verifyNoInteractions(completion);
        mvc.perform(verifyCode(latest, deliveredCode)).andExpect(status().isOk());
    }

    @Test void failedResendPreservesHashAndPendingContextWithoutCompletingLogin() throws Exception {
        var pending = start();
        String hash = account.codeHash;
        Instant expiry = account.expiresAt;
        Object context = pending.getAttribute(CommonMfaService.PENDING_ATTRIBUTE);
        now = now.plusSeconds(60);
        clearInvocations(mapper, mailbox);
        doThrow(new IllegalStateException("delivery unavailable"))
            .when(mailbox).deliver(anyList(), anyString());
        mvc.perform(postWithCsrf("/api/common/mfa/resend").session(pending))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(CommonMfaService.DELIVERY_FAILURE));
        assertThat(account.codeHash).isEqualTo(hash);
        assertThat(account.expiresAt).isEqualTo(expiry);
        assertThat(pending.getAttribute(CommonMfaService.PENDING_ATTRIBUTE)).isSameAs(context);
        verify(mapper, never()).challenge(anyString(), anyString(), nullable(Instant.class));
        verifyNoInteractions(completion);
    }

    @Test void sixthResendRequiresNewLoginWithoutMailOrHashChange() throws Exception {
        var pending = start();
        for (int attempt = 0; attempt < 5; attempt++) {
            now = now.plusSeconds(60);
            mvc.perform(postWithCsrf("/api/common/mfa/resend").session(pending)).andExpect(status().isOk());
        }
        String hash = account.codeHash;
        clearInvocations(mapper, mailbox);
        now = now.plusSeconds(60);
        mvc.perform(postWithCsrf("/api/common/mfa/resend").session(pending))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(CommonMfaService.LOGIN_LIMIT))
            .andExpect(jsonPath("$.meta.nextScreenId").value("UI-22"));
        assertThat(account.codeHash).isEqualTo(hash);
        verifyNoInteractions(mailbox, completion);
        mvc.perform(verifyCode(pending, deliveredCode)).andExpect(status().isUnauthorized());
    }

    @Test void eleventhHourlyDeliveryIsRefusedAtResend() throws Exception {
        for (int attempt = 0; attempt < 9; attempt++) start();
        var pending = start();
        now = now.plusSeconds(60);
        String hash = account.codeHash;
        clearInvocations(mapper, mailbox);
        mvc.perform(postWithCsrf("/api/common/mfa/resend").session(pending))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("인증번호 요청이 너무 많습니다. 59분 뒤 다시 로그인해 주세요."));
        assertThat(account.codeHash).isEqualTo(hash);
        verifyNoInteractions(mailbox, completion);
        verify(mapper, never()).challenge(anyString(), anyString(), nullable(Instant.class));
    }

    @Test void temporaryAccountCompletesIntoPasswordChangeNotBusinessScreen() throws Exception {
        account.temporary = "Y";
        var pending = start();
        mvc.perform(verifyCode(pending, deliveredCode)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nextScreenId").value("UI-40"));
        verify(mapper).loginAccess(eq(account.userId), eq(true), anyString(), eq(now));
        verify(completion).prepare(account.userId, true);
        verify(completion).establish(any(), eq(account.userId), eq(true));
    }
}
