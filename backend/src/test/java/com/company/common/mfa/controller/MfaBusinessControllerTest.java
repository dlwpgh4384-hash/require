package com.company.common.mfa.controller;

import com.company.common.bills.service.BillLocalMailbox;
import com.company.common.mfa.mapper.MfaMapper;
import com.company.common.mfa.mapper.MfaRows.Account;
import com.company.common.mfa.service.CommonMfaService;
import com.company.common.support.auth.LoginCompletion;
import com.company.common.support.service.BusinessCalendar;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import static com.company.common.support.controller.OpenApiResponseContract.response;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real controller and service; only persistence, delivery and the trusted login continuation are mocked. */
class MfaBusinessControllerTest {
    MfaMapper mapper;
    BillLocalMailbox mailbox;
    LoginCompletion completion;
    CommonMfaService service;
    MockMvc mvc;
    Account account;
    Instant now;
    String deliveredCode;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        mapper = mock(MfaMapper.class);
        mailbox = mock(BillLocalMailbox.class);
        var calendar = mock(BusinessCalendar.class);
        now = Instant.parse("2026-01-02T00:00:00Z");
        when(calendar.now()).thenAnswer(call -> now);
        completion = mock(LoginCompletion.class);
        when(completion.prepare(anyString(), anyBoolean())).thenReturn(
            new LoginCompletion.Prepared("UI-34", Map.of("modifiedAt", now.toString())));
        ObjectProvider<LoginCompletion> completions = mock(ObjectProvider.class);
        when(completions.getIfAvailable()).thenReturn(completion);
        var transactions = new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
            @Override protected void doCommit(DefaultTransactionStatus status) { }
            @Override protected void doRollback(DefaultTransactionStatus status) { }
        };
        service = new CommonMfaService(mapper, mailbox, calendar, completions, transactions);
        mvc = MockMvcBuilders.standaloneSetup(new CommonMfaController(service))
            .setControllerAdvice(new MfaExceptionHandler())
            // 앱(Spring Boot) 기본과 같게 시각을 글자(ISO)로 쓴다.
            .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json()
                    .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
            .build();
        account = new Account();
        account.userId = "mfa-contract-admin";
        account.name = "인증 관리자";
        account.email = "mfa-contract@example.invalid";
        account.status = "USE";
        account.temporary = "N";
        account.administrator = true;
        when(mapper.lockAccount(account.userId)).thenReturn(account);
        doAnswer(call -> {
            account.codeHash = call.getArgument(1);
            account.expiresAt = call.getArgument(2);
            return null;
        }).when(mapper).challenge(eq(account.userId), anyString(), nullable(Instant.class));
        when(mailbox.deliver(anyList(), anyString())).thenAnswer(call -> {
            var match = Pattern.compile("인증번호: ([0-9]{6})").matcher((String) call.getArgument(1));
            assertThat(match.find()).isTrue();
            deliveredCode = match.group(1);
            return Path.of("mfa-contract-delivery");
        });
    }

    MockHttpServletRequest start() {
        var request = new MockHttpServletRequest();
        service.startAfterPasswordVerified(request, account.userId);
        return request;
    }

    @Test
    @DisplayName("business condition: MFA_USE_YN 관리자 인증 재발송은 로그인당 5회 초과 시 추가 발송·해시변경0")
    void sixthResendPreservesLastChallengeAndCannotCompleteLogin() throws Exception {
        var pending = start();
        for (int attempt = 0; attempt < 5; attempt++) {
            now = now.plusSeconds(60);
            service.postCommonMfaResend(pending);
        }
        String hash = account.codeHash;
        now = now.plusSeconds(60);
        clearInvocations(mapper, mailbox);
        mvc.perform(post("/api/common/mfa/resend").session((MockHttpSession) pending.getSession(false)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("code"))
            .andExpect(jsonPath("$.errors[0].message").value(CommonMfaService.LOGIN_LIMIT))
            .andExpect(jsonPath("$.meta.nextScreenId").value("UI-22"));
        assertThat(account.codeHash).isEqualTo(hash);
        verifyNoInteractions(mailbox, completion);
        verify(mapper, never()).challenge(anyString(), anyString(), any());
        verify(mapper, never()).complete(anyString(), any());
    }

    @Test
    @DisplayName("business condition: MFA_USE_YN 관리자 최신 인증이 이전 기기를 대체하면 이전 인증은 업무 세션 생성0")
    void latestDeviceMakesEarlierVerificationUnauthorized() throws Exception {
        var earlier = start();
        start();
        clearInvocations(mapper, mailbox);
        mvc.perform(post("/api/common/mfa/verify").session((MockHttpSession) earlier.getSession(false))
            .contentType("application/json").content("{\"code\":\"" + deliveredCode + "\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        verify(mapper, never()).complete(anyString(), any());
        verify(mapper, never()).loginAccess(anyString(), anyBoolean(), anyString(), any());
        verifyNoInteractions(completion, mailbox);
    }

    @Test
    @DisplayName("happy side-effect condition: MFA_USE_YN 관리자 인증 재발송 후 이전 번호 거부·최신 번호만 LOGIN 완료")
    void resendReplacesChallengeAndOnlyNewCodeCompletes() throws Exception {
        var pending = start();
        String previous = deliveredCode;
        String hash = account.codeHash;
        now = now.plusSeconds(60);
        mvc.perform(post("/api/common/mfa/resend").session((MockHttpSession) pending.getSession(false)))
            .andExpect(status().isOk()).andExpect(response("POST", "/api/common/mfa/resend"))
            .andExpect(jsonPath("$.data.maskedEmail").value("m***@example.invalid"))
            .andExpect(jsonPath("$.data.expiresAt").value(now.plusSeconds(300).toString()));
        assertThat(account.codeHash).isNotEqualTo(hash);
        mvc.perform(post("/api/common/mfa/verify").session((MockHttpSession) pending.getSession(false))
            .contentType("application/json").content("{\"code\":\"" + previous + "\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("code"));
        verifyNoInteractions(completion);
        mvc.perform(post("/api/common/mfa/verify").session((MockHttpSession) pending.getSession(false))
            .contentType("application/json").content("{\"code\":\"" + deliveredCode + "\"}"))
            .andExpect(status().isOk()).andExpect(response("POST", "/api/common/mfa/verify"))
            .andExpect(jsonPath("$.data.nextScreenId").value("UI-34")).andExpect(jsonPath("$.data.userName").value("인증 관리자"));
        verify(mapper).complete(account.userId, now);
        verify(mapper).loginAccess(eq(account.userId), eq(false), anyString(), eq(now));
        verify(completion).establish(any(), eq(account.userId), eq(false));
        assertThat(pending.getSession(false).getAttribute(CommonMfaService.PENDING_ATTRIBUTE)).isNull();
    }
}
