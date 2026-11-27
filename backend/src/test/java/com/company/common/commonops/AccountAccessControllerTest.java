package com.company.common.commonops;

import com.company.common.commonops.controller.*;
import com.company.common.commonops.mapper.AccountAccessMapper;
import com.company.common.commonops.mapper.AccountAccessMapper.Account;
import com.company.common.commonops.service.*;
import com.company.common.mfa.service.CommonMfaService;
import com.company.common.support.auth.AccountSessions;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.users.mapper.UserMapper;
import com.company.common.users.service.TemporaryUserPassword;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.company.common.support.controller.OpenApiResponseContract.response;

@WebMvcTest(AccountAccessController.class)
@ContextConfiguration(classes = {AccountAccessController.class, AccountAccessExceptionHandler.class,
    AccountAccessService.class, AccountLoginCompletion.class, AccountSessions.class,
    LoginAttemptLimit.class, AccountApiPolicy.class, AccountAccessConfiguration.class, AccountAccessControllerTest.Transactions.class})
class AccountAccessControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountSessions sessions;
    @MockBean AccountAccessMapper mapper;
    @MockBean UserMapper users;
    @MockBean TemporaryUserPassword passwords;
    @MockBean BusinessCalendar calendar;
    @MockBean CommonMfaService mfa;
    @Autowired AccountAccessService service;
    @Autowired LoginAttemptLimit limit;
    @Autowired FindWork findWork;
    Account account;
    final Instant now = Instant.parse("2026-01-02T00:00:00Z");
    final String secret = java.util.UUID.randomUUID() + "a1!";
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
    final Cookie csrf = new Cookie("XSRF-TOKEN", "contract-csrf-token");
    @BeforeEach void setup() {
        // 시험끼리 실패 횟수가 섞이지 않게 비운다(같은 문맥을 함께 씀).
        for (String ip : List.of("127.0.0.1", "192.0.2.8", "192.0.2.9")) limit.succeeded(ip);
        findWork.queued.clear(); findWork.deferred = false;
        account = new Account(); account.userId = "access-test"; account.name = "시험 사용자";
        account.email = "access@example.invalid"; account.hash = encoder.encode(secret);
        account.status = "USE"; account.temporary = "N"; account.modifiedAt = now.minusSeconds(100);
        when(calendar.now()).thenReturn(now);
        when(mapper.account(account.userId)).thenAnswer(call -> account);
        when(mapper.lockAccount(account.userId)).thenAnswer(call -> account);
        when(mapper.roles(account.userId)).thenReturn(List.of());
        when(mapper.permissions(account.userId)).thenReturn(List.of());
        when(users.selectParts(account.userId)).thenReturn(List.of());
        when(mapper.standard("LOCK_FAIL_CNT")).thenReturn(5);
        when(mapper.standard("SESSION_MIN")).thenReturn(30);
        when(mapper.standard("PWD_CHG_DAY")).thenReturn(90);
        doAnswer(call -> { account.failures++; if (account.failures >= (int) call.getArgument(1)) account.status = "LOCK"; return null; })
            .when(mapper).failed(anyString(), anyInt());
        doAnswer(call -> { account.failures = 0; account.lastLoginAt = call.getArgument(1); return null; })
            .when(mapper).complete(anyString(), any());
        when(mapper.password(anyString(), anyString(), anyBoolean(), any())).thenAnswer(call -> {
            account.hash = call.getArgument(1); account.temporary = (boolean) call.getArgument(2) ? "Y" : "N";
            account.passwordChangedAt = call.getArgument(3); account.modifiedAt = now; account.failures = 0; return 1;
        });
    }
    @AfterEach void clearContext() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJson(String path, Object input) throws Exception {
        return post(path).servletPath(path).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json").content(json.writeValueAsString(input));
    }
    MockHttpSession login() throws Exception {
        return (MockHttpSession) mvc.perform(postJson("/api/common/login", Map.of("userId", account.userId, "password", secret)))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    void info(MockHttpSession session, int status) throws Exception {
        var request = get("/api/common/my-info").servletPath("/api/common/my-info");
        if (!session.isInvalid()) request.session(session);
        mvc.perform(request).andExpect(status().is(status));
    }
    @Test
    @DisplayName("happy side-effect condition: BCrypt 로그인은 LAST_LOGIN_DTTM·횟수0·TB_SY_ACCS_LOG LOGIN1행과 세션ID 교체")
    void successfulLoginResetsDatabaseCountersAndRotatesSessionOnce() throws Exception {
        account.failures = 3; var original = new MockHttpSession(); String old = original.getId();
        mvc.perform(post("/api/common/login").servletPath("/api/common/login").session(original)
            .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("userId", account.userId, "password", secret))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nextScreenId").value("UI-34"))
            .andExpect(response("POST", "/api/common/login"))
            .andExpect(jsonPath("$.meta.modifiedAt").exists()).andExpect(header().string("Cache-Control", "no-store"));
        assertThat(original.getId()).isNotEqualTo(old);
        assertThat(mapper.account(account.userId).failures).isZero();
        assertThat(mapper.account(account.userId).lastLoginAt).isEqualTo(now);
        assertThat(encoder.matches(secret, account.hash)).isTrue();
        verify(mapper).complete(account.userId, now);
        verify(mapper).access(eq(account.userId), eq("LOGIN"), eq("로그인"), anyString(), eq(now));
        info(original, 200);
    }
    @Test void absentAndIncorrectCredentialsHaveIdenticalMessage() throws Exception {
        for (String id : List.of("not-found", account.userId)) mvc.perform(postJson("/api/common/login", Map.of("userId", id, "password", "wrong")))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(AccountAccessService.INVALID));
    }
    @Test void emptyPasswordHasFieldErrorWithoutMutation() throws Exception {
        mvc.perform(postJson("/api/common/login", Map.of("userId", account.userId, "password", "")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("password"))
            .andExpect(jsonPath("$.errors[0].message").value("비밀번호를 입력해 주세요."));
        verify(mapper, never()).failed(anyString(), anyInt());
    }
    @Test
    @DisplayName("business condition: LOCK_FAIL_CNT번째 실패는 LOCK 저장 후 업무 API401, 로그인 완료 없음")
    void thresholdLocksAccountAndTerminatesExistingDevice() throws Exception {
        var device = login(); account.failures = 4;
        clearInvocations(mapper);
        mvc.perform(post("/api/common/login").servletPath("/api/common/login")
            .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("userId", account.userId, "password", "wrong"))))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(AccountAccessService.LOCKED));
        assertThat(account.status).isEqualTo("LOCK"); info(device, 401);
        assertThat(account.failures).isEqualTo(5);
        verify(mapper, never()).complete(anyString(), any());
        verify(mapper).access(eq(account.userId), eq("LOGIN_FAIL"), eq("계정 잠김"), anyString(), eq(now));
    }
    @Test void stoppedAccountDoesNotCompleteLogin() throws Exception {
        account.status = "STOP";
        mvc.perform(postJson("/api/common/login", Map.of("userId", account.userId, "password", secret)))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(AccountAccessService.STOPPED));
        verify(mapper, never()).complete(anyString(), any());
    }
    @Test void failuresBelowConfiguredThresholdCommitOneCounterAndAuditPerAttempt() throws Exception {
        when(mapper.standard("LOCK_FAIL_CNT")).thenReturn(3);
        mvc.perform(postJson("/api/common/login", Map.of("userId", account.userId, "password", "wrong")))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(AccountAccessService.INVALID));
        assertThat(account.failures).isEqualTo(1);
        assertThat(account.status).isEqualTo("USE");
        verify(mapper).failed(account.userId, 3);
        verify(mapper).access(eq(account.userId), eq("LOGIN_FAIL"), eq("비밀번호 오류 (1/3)"), anyString(), eq(now));
    }
    @Test void unknownLoginAuditUsesAttemptedIdAndRemoteAddressNotForwardedHeaders() throws Exception {
        mvc.perform(postJson("/api/common/login", Map.of("userId", "unknown-user", "password", "wrong"))
                .header("X-User-Id", "forged").header("X-Forwarded-For", "203.0.113.9")
                .with(request -> { request.setRemoteAddr("192.0.2.8"); return request; }))
            .andExpect(status().isUnauthorized());
        verify(mapper).access("unknown-user", "LOGIN_FAIL", "비밀번호 오류", "192.0.2.8", now);
        verify(mapper, never()).failed(anyString(), anyInt());
    }
    @Test void oversizedLoginPasswordIsAFieldErrorNotAnEncoderException() throws Exception {
        mvc.perform(postJson("/api/common/login", Map.of("userId", account.userId, "password", "가".repeat(25))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("password"));
        verify(mapper, never()).lockAccount(anyString());
    }
    @Test void effectiveSecurityChainAddsHeadersToSuccessfulAndRejectedResponses() throws Exception {
        var device = login();
        mvc.perform(get("/api/common/my-info").servletPath("/api/common/my-info").session(device))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Referrer-Policy", "same-origin"));
        mvc.perform(post("/api/common/logout").servletPath("/api/common/logout").session(device))
            .andExpect(status().isForbidden()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Referrer-Policy", "same-origin"));
        info(device, 200);
    }
    @Test void missingCsrfCannotLogin() throws Exception {
        mvc.perform(post("/api/common/login").servletPath("/api/common/login").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden()); verifyNoInteractions(mapper);
    }
    @Test
    @DisplayName("happy side-effect condition: TB_SY_ACCS_LOG LOGOUT 한 줄·현재기기만 종료·Cache-Control no-store")
    void logoutEndsOnlyCurrentDeviceAndAuditsOnce() throws Exception {
        var first = login(); var second = login();
        mvc.perform(post("/api/common/logout").servletPath("/api/common/logout").session(first)
            .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
            .andExpect(status().isNoContent()).andExpect(content().string(""))
            .andExpect(response("POST", "/api/common/logout"))
            .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(first.isInvalid()).isTrue();
        assertThat(second.isInvalid()).isFalse();
        info(first, 401); info(second, 200);
        verify(mapper).access(eq(account.userId), eq("LOGOUT"), eq("직접 로그아웃"), anyString(), eq(now));
    }
    @Test void anonymousLogoutDoesNotAffectAnotherDevice() throws Exception {
        var other = login(); mvc.perform(postJson("/api/common/logout", Map.of())).andExpect(status().isUnauthorized()); info(other, 200);
    }
    @Test
    @DisplayName("business validation: CSRF 없는 로그아웃은 세션·자격·저장을 보존한다; dirty 취소는 UI 소유")
    void missingCsrfLogoutPreservesSession() throws Exception {
        var device = login(); mvc.perform(post("/api/common/logout").servletPath("/api/common/logout").session(device))
            .andExpect(status().isForbidden()).andExpect(header().string("Cache-Control", "no-store")); info(device, 200);
        verify(mapper, never()).access(anyString(), eq("LOGOUT"), anyString(), anyString(), any());
    }
    @Test void findMismatchDoesNotSendOrWrite() throws Exception {
        mvc.perform(postJson("/api/common/password-find", Map.of("userId", account.userId, "email", "other@example.invalid")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value(AccountAccessService.FIND_MESSAGE));
        verifyNoInteractions(passwords); verify(mapper, never()).password(anyString(), anyString(), anyBoolean(), any());
    }
    @Test void findInvalidEmailIsFieldError() throws Exception {
        mvc.perform(postJson("/api/common/password-find", Map.of("userId", account.userId, "email", "invalid")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("email"))
            .andExpect(jsonPath("$.errors[0].message").value("이메일 형식이 맞지 않습니다."));
        verifyNoInteractions(passwords);
    }
    @Test void findDoesNotOverwriteAPasswordChangedWhileWaitingForTheAccountLock() throws Exception {
        var observed = new Account(); observed.modifiedAt = account.modifiedAt;
        when(mapper.account(account.userId)).thenReturn(observed);
        account.modifiedAt = now;
        String hash = account.hash;
        mvc.perform(postJson("/api/common/password-find", Map.of("userId", account.userId, "email", account.email)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value(AccountAccessService.FIND_MESSAGE));
        assertThat(account.hash).isEqualTo(hash);
        verifyNoInteractions(passwords);
        verify(mapper, never()).password(anyString(), anyString(), anyBoolean(), any());
    }
    @Test void findDeliveryFailurePreservesHashAndSessions() throws Exception {
        var device = login(); var hash = account.hash;
        when(passwords.hashAndDeliver(account.email)).thenThrow(new IllegalStateException("transport"));
        mvc.perform(postJson("/api/common/password-find", Map.of("userId", account.userId, "email", account.email)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value(AccountAccessService.FIND_MESSAGE));
        assertThat(account.hash).isEqualTo(hash); info(device, 200);
    }
    @Test
    @DisplayName("happy business side-effect condition: TEMP_PWD_YN=Y·PWD_ERR_CNT=0·기존 기기 API401, 10분내 재발송0")
    void findSuccessInvalidatesAllDevicesAndCooldownPreventsSecondDelivery() throws Exception {
        var first = login(); var second = login(); when(passwords.hashAndDeliver(account.email)).thenReturn(encoder.encode(java.util.UUID.randomUUID().toString()));
        var input = Map.of("userId", account.userId, "email", account.email);
        mvc.perform(post("/api/common/password-find").servletPath("/api/common/password-find")
            .cookie(csrf).header("X-XSRF-TOKEN",csrf.getValue()).contentType("application/json").content(json.writeValueAsString(input)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value(AccountAccessService.FIND_MESSAGE))
            .andExpect(com.company.common.support.controller.OpenApiResponseContract.response("POST", "/api/common/password-find"));
        assertThat(account.temporary).isEqualTo("Y"); assertThat(account.failures).isZero(); info(first, 401); info(second, 401);
        String storedHash = account.hash;
        mvc.perform(post("/api/common/password-find").servletPath("/api/common/password-find")
            .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json").content(json.writeValueAsString(input)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value(AccountAccessService.FIND_MESSAGE));
        assertThat(account.hash).isEqualTo(storedHash);
        verify(passwords, times(1)).hashAndDeliver(account.email);
        verify(mapper, times(1)).password(eq(account.userId), anyString(), eq(true), eq(now));
    }
    @Test void lockedFindDoesNotSend() throws Exception {
        account.status = "LOCK"; mvc.perform(postJson("/api/common/password-find", Map.of("userId", account.userId, "email", account.email)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value(AccountAccessService.FIND_MESSAGE)); verifyNoInteractions(passwords);
    }
    @Test void stoppedFindDoesNotSend() throws Exception {
        account.status = "STOP"; mvc.perform(postJson("/api/common/password-find", Map.of("userId", account.userId, "email", account.email)))
            .andExpect(status().isOk()); verifyNoInteractions(passwords);
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder change(String next, Instant token) throws Exception {
        return put("/api/common/my-password").servletPath("/api/common/my-password").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
            .contentType("application/json").content(json.writeValueAsString(Map.of("currentPassword", secret, "newPassword", next, "modifiedAt", token)));
    }
    @Test
    @DisplayName("happy side-effect condition: PWD_CHG_DTTM 갱신·TEMP_PWD_YN 해소·PWD_CHG_DAY 권고·다른기기 종료")
    void changedPasswordKeepsCurrentDeviceAndEndsOtherDevices() throws Exception {
        var first = login(); var second = login(); String next = java.util.UUID.randomUUID() + "b2!";
        account.temporary = "Y";
        mvc.perform(put("/api/common/my-password").servletPath("/api/common/my-password").session(first)
            .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("currentPassword", secret, "newPassword", next, "modifiedAt", account.modifiedAt))))
            .andExpect(status().isOk()).andExpect(response("PUT", "/api/common/my-password"))
            .andExpect(jsonPath("$.data.passwordChangedAt").value(now.toString()))
            .andExpect(jsonPath("$.meta.passwordChangeDays").value(90));
        assertThat(account.temporary).isEqualTo("N");
        assertThat(account.passwordChangedAt).isEqualTo(now);
        verify(mapper).password(eq(account.userId), anyString(), eq(false), eq(now));
        assertThat(encoder.matches(next, account.hash)).isTrue(); assertThat(account.hash).startsWith("$2a$10$");
        info(first, 200); info(second, 401);
        mvc.perform(postJson("/api/common/login", Map.of("userId", account.userId, "password", next))).andExpect(status().isOk());
    }
    @Test
    @DisplayName("auth: 만료 세션의 내 비밀번호 변경은 401·해시와 TEMP_PWD_YN 불변")
    void expiredChangeDoesNotWrite() throws Exception {
        String hash = account.hash;
        mvc.perform(put("/api/common/my-password").servletPath("/api/common/my-password")
            .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("currentPassword", secret, "newPassword", "Different8!", "modifiedAt", account.modifiedAt))))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        assertThat(account.hash).isEqualTo(hash);
        verify(mapper, never()).password(anyString(), anyString(), anyBoolean(), any());
    }
    @Test void sevenCharacterPasswordIsRejected() throws Exception {
        var device = login(); String hash = account.hash;
        mvc.perform(change("Abcd1!x", account.modifiedAt).session(device)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("newPassword"));
        assertThat(account.hash).isEqualTo(hash);
    }
    @Test void unchangedPasswordIsRejected() throws Exception {
        var device = login(); String hash = account.hash;
        mvc.perform(change(secret, account.modifiedAt).session(device)).andExpect(status().isBadRequest()); assertThat(account.hash).isEqualTo(hash);
    }
    @Test void wrongCurrentPasswordDoesNotChangeHashOrEndOtherDevice() throws Exception {
        var first = login(); var second = login(); String hash = account.hash;
        mvc.perform(put("/api/common/my-password").servletPath("/api/common/my-password").session(first)
                .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("currentPassword", "wrong", "newPassword", "Changed8!", "modifiedAt", account.modifiedAt))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("currentPassword"));
        assertThat(account.hash).isEqualTo(hash); info(first, 200); info(second, 200);
        verify(mapper, never()).password(anyString(), anyString(), anyBoolean(), any());
    }
    @Test void missingVersionIsAFieldErrorWithoutPasswordWrite() throws Exception {
        var device = login();
        mvc.perform(put("/api/common/my-password").servletPath("/api/common/my-password").session(device)
                .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("currentPassword", secret, "newPassword", "Changed8!"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("modifiedAt"));
        verify(mapper, never()).password(anyString(), anyString(), anyBoolean(), any());
    }
    @Test void oversizedCurrentPasswordHasFieldErrorAndNoWrite() throws Exception {
        var device = login();
        mvc.perform(put("/api/common/my-password").servletPath("/api/common/my-password").session(device)
                .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("currentPassword", "가".repeat(25), "newPassword", "Changed8!", "modifiedAt", account.modifiedAt))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("currentPassword"));
        verify(mapper, never()).password(anyString(), anyString(), anyBoolean(), any());
    }
    @Test void temporaryPasswordCannotBeReusedAndDoesNotExpireByAge() throws Exception {
        account.temporary = "Y"; account.passwordChangedAt = now.minusSeconds(99999999);
        var device = login();
        mvc.perform(change(secret, account.modifiedAt).session(device))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("임시 비밀번호와 다르게 정해 주세요."));
        verify(mapper, never()).password(anyString(), anyString(), anyBoolean(), any());
        mvc.perform(change("Changed8!", account.modifiedAt).session(device)).andExpect(status().isOk());
        info(device, 200);
    }
    @Test
    @DisplayName("business condition: stale modifiedAt은 409·PWD_CHG_DTTM·TEMP_PWD_YN·해시 불변")
    void staleVersionIsConflictWithoutWrites() throws Exception {
        var device = login(); String hash = account.hash;
        mvc.perform(put("/api/common/my-password").servletPath("/api/common/my-password").session(device)
            .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("currentPassword", secret, "newPassword", "Different8!", "modifiedAt", now.minusSeconds(999)))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));
        assertThat(account.hash).isEqualTo(hash);
        assertThat(account.passwordChangedAt).isNull();
        assertThat(account.temporary).isEqualTo("N");
        verify(mapper, never()).password(anyString(), anyString(), anyBoolean(), any());
    }
    @Test void temporaryLoginIsRecordedBeforePasswordChangeAndRestrictedUntilChange() throws Exception {
        account.temporary = "Y"; account.passwordChangedAt = now.minusSeconds(99999999);
        var device = login(); info(device, 403);
        mvc.perform(change("Changed8!", account.modifiedAt).session(device)).andExpect(status().isOk()); info(device, 200);
        verify(mapper).access(eq(account.userId), eq("LOGIN"), eq("임시 비밀번호 로그인"), anyString(), eq(now));
    }
    @Test void myInfoIncludesOnlyPublicIdentityFieldsAndPasswordMetadata() throws Exception {
        var device = login(); mvc.perform(get("/api/common/my-info").servletPath("/api/common/my-info").session(device))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(account.userId)).andExpect(jsonPath("$.data.roles").isArray())
            .andExpect(jsonPath("$.data.hash").doesNotExist()).andExpect(jsonPath("$.data.passwordChangedAt").doesNotExist())
            .andExpect(jsonPath("$.meta.passwordChangeDays").value(90));
    }
    @Test
    @DisplayName("내 정보: 역할 ID(roles)와 같은 순서로 역할 이름(roleNames)을 함께 준다")
    void myInfoListsRoleNamesInRoleOrder() throws Exception {
        when(mapper.roles(account.userId)).thenReturn(List.of("FIN", "PM"));
        when(mapper.roleNames(account.userId)).thenReturn(List.of("재무", "PM"));
        var device = login(); mvc.perform(get("/api/common/my-info").servletPath("/api/common/my-info").session(device))
            .andExpect(status().isOk()).andExpect(response("GET", "/api/common/my-info"))
            .andExpect(jsonPath("$.data.roles[0]").value("FIN")).andExpect(jsonPath("$.data.roles[1]").value("PM"))
            .andExpect(jsonPath("$.data.roleNames[0]").value("재무")).andExpect(jsonPath("$.data.roleNames[1]").value("PM"))
            .andExpect(jsonPath("$.data.name").value("시험 사용자"));
    }
    @Test
    @DisplayName("로그인 응답에 사용자 이름(userName): 일반 로그인·임시 비밀번호(UI-40)·관리자 비밀번호 단계(UI-39)")
    void loginResponsesCarryUserName() throws Exception {
        mvc.perform(postJson("/api/common/login", Map.of("userId", account.userId, "password", secret)))
            .andExpect(status().isOk()).andExpect(response("POST", "/api/common/login"))
            .andExpect(jsonPath("$.data.userName").value("시험 사용자"));
        account.temporary = "Y";
        mvc.perform(postJson("/api/common/login", Map.of("userId", account.userId, "password", secret)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nextScreenId").value("UI-40"))
            .andExpect(jsonPath("$.data.userName").value("시험 사용자"));
        account.temporary = "N";
        when(mapper.roles(account.userId)).thenReturn(List.of("ADMIN"));
        mvc.perform(postJson("/api/common/login", Map.of("userId", account.userId, "password", secret)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nextScreenId").value("UI-39"))
            .andExpect(jsonPath("$.data.userName").value("시험 사용자"));
    }
    @Test void pendingAdministratorMfaDoesNotCompleteLoginOrResetCounters() throws Exception {
        when(mapper.roles(account.userId)).thenReturn(List.of("ADMIN")); account.failures = 2;
        mvc.perform(postJson("/api/common/login", Map.of("userId", account.userId, "password", secret)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nextScreenId").value("UI-39"));
        verify(mfa).startAfterPasswordVerified(any(), eq(account.userId));
        verify(mapper, never()).complete(anyString(), any());
        verify(mapper, never()).access(anyString(), eq("LOGIN"), anyString(), anyString(), any());
        assertThat(account.failures).isEqualTo(2);
    }
    @Test void historicalPasswordCompositionDoesNotPreventLogin() throws Exception {
        account.hash = encoder.encode("legacy");
        mvc.perform(postJson("/api/common/login", Map.of("userId", account.userId, "password", "legacy"))).andExpect(status().isOk());
    }
    @Test void nonexistentFindHasSameResultAndNoWrites() throws Exception {
        mvc.perform(postJson("/api/common/password-find", Map.of("userId", "not-found", "email", account.email)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value(AccountAccessService.FIND_MESSAGE));
        verifyNoInteractions(passwords); verify(mapper, never()).password(anyString(), anyString(), anyBoolean(), any());
    }
    @Test void eightCharacterCompositionIsAccepted() throws Exception {
        var device = login(); mvc.perform(change("Abcd12!x", account.modifiedAt).session(device)).andExpect(status().isOk());
        assertThat(encoder.matches("Abcd12!x", account.hash)).isTrue();
    }
    @Test void actualServerSessionDestructionRecordsAutomaticLogout() throws Exception {
        var device = login(); device.invalidate();
        verify(mapper).access(eq(account.userId), eq("LOGOUT"), eq(AccountSessions.AUTOMATIC_LOGOUT), anyString(), eq(now));
    }
    @Test void stoppedAccountNextRequestTerminatesAllDevices() throws Exception {
        var first = login(); var second = login(); account.status = "STOP";
        info(first, 401); info(second, 401);
    }
    @Test
    @DisplayName("중지 계정의 다음 요청은 401과 「사용할 수 없는 계정입니다…」, LOGOUT 사유는 명세 문구")
    void stoppedAccountRequestShowsUnusableMessageAndSpecLogoutReason() throws Exception {
        var device = login(); account.status = "STOP";
        mvc.perform(get("/api/common/my-info").servletPath("/api/common/my-info").session(device))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(AccountAccessService.UNUSABLE));
        verify(mapper, atLeastOnce()).access(eq(account.userId), eq("LOGOUT"), eq(AccountSessions.ACCOUNT_ENDED), anyString(), eq(now));
    }
    @Test
    @DisplayName("같은 IP 실패 20번 뒤 21번째는 비밀번호 확인 없이 바로 429, 5번째 잠김 문구는 가리지 않고 다른 IP는 그대로")
    void attemptAfterTwentyFailuresFromOneAddressIsRejectedImmediatelyWith429() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++)
            mvc.perform(postJson("/api/common/login", Map.of("userId", attempt < 10 ? "not-found" : "ghost", "password", "wrong")))
                .andExpect(status().isUnauthorized());
        long started = System.nanoTime();
        mvc.perform(postJson("/api/common/login", Map.of("userId", "unknown-user", "password", "wrong")))
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.message").value(LoginAttemptLimit.TOO_MANY))
            .andExpect(response("POST", "/api/common/login"));
        assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(1));
        verify(mapper, times(10)).lockAccount("not-found");
        verify(mapper, never()).lockAccount("unknown-user");
        mvc.perform(postJson("/api/common/login", Map.of("userId", "not-found", "password", "wrong"))
                .with(request -> { request.setRemoteAddr("192.0.2.9"); return request; }))
            .andExpect(status().isUnauthorized());
    }
    @Test
    @DisplayName("잠김·중지 계정은 비밀번호가 틀리면 일반 문구만, 기록은 「계정 잠김」「사용 중지 계정」")
    void lockedOrStoppedAccountWithWrongPasswordGetsGenericMessage() throws Exception {
        account.status = "LOCK";
        mvc.perform(postJson("/api/common/login", Map.of("userId", account.userId, "password", "wrong")))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(AccountAccessService.INVALID));
        mvc.perform(postJson("/api/common/login", Map.of("userId", account.userId, "password", secret)))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(AccountAccessService.LOCKED));
        verify(mapper, times(2)).access(eq(account.userId), eq("LOGIN_FAIL"), eq("계정 잠김"), anyString(), eq(now));
        account.status = "STOP";
        mvc.perform(postJson("/api/common/login", Map.of("userId", account.userId, "password", "wrong")))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(AccountAccessService.INVALID));
        verify(mapper).access(eq(account.userId), eq("LOGIN_FAIL"), eq("사용 중지 계정"), anyString(), eq(now));
        verify(mapper, never()).failed(anyString(), anyInt());
    }
    @Test
    @DisplayName("옛 비밀번호 자격으로 온 다른 세션 요청은 그 세션만 끝내고 현재 세션은 남긴다")
    void staleCredentialEndsOnlyThatSession() throws Exception {
        var current = login(); var stale = login();
        sessions.current(stale).credentialHash = encoder.encode("old-password");
        info(stale, 401);
        assertThat(stale.isInvalid()).isTrue();
        info(current, 200);
        verify(mapper).access(eq(account.userId), eq("LOGOUT"), eq(AccountSessions.ACCOUNT_ENDED), anyString(), eq(now));
    }
    @Test
    @DisplayName("비밀번호 변경 커밋 중 같은 세션(다른 탭) 요청은 끊기지 않는다")
    void sameSessionRequestDuringPasswordChangeKeepsSession() throws Exception {
        var current = login(); String next = java.util.UUID.randomUUID() + "c3!";
        var during = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call -> {
            account.hash = call.getArgument(1); account.modifiedAt = now;
            var tab = new org.springframework.mock.web.MockHttpServletRequest(); tab.setSession(current);
            service.authenticated(tab); during.set(true); return 1;
        }).when(mapper).password(anyString(), anyString(), anyBoolean(), any());
        mvc.perform(change(next, account.modifiedAt).session(current)).andExpect(status().isOk());
        assertThat(during).isTrue(); assertThat(current.isInvalid()).isFalse();
        assertThat(sessions.current(current).pendingHash).isNull();
        info(current, 200);
    }
    @Test
    @DisplayName("로그인과 관리자 비밀번호 단계는 세션 번호와 요청 토큰을 새로 만든다")
    void loginRotatesSessionIdAndCsrfTokenAtEachStep() throws Exception {
        var plain = new MockHttpSession(); String before = plain.getId();
        var result = mvc.perform(postJson("/api/common/login", Map.of("userId", account.userId, "password", secret)).session(plain))
            .andExpect(status().isOk()).andExpect(cookie().exists("XSRF-TOKEN")).andReturn();
        assertThat(plain.getId()).isNotEqualTo(before);
        assertThat(result.getResponse().getCookie("XSRF-TOKEN").getValue()).isNotBlank().isNotEqualTo(csrf.getValue());
        when(mapper.roles(account.userId)).thenReturn(List.of("ADMIN"));
        var pending = new MockHttpSession(); String pendingBefore = pending.getId();
        var admin = mvc.perform(postJson("/api/common/login", Map.of("userId", account.userId, "password", secret)).session(pending))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nextScreenId").value("UI-39")).andReturn();
        assertThat(pending.getId()).isNotEqualTo(pendingBefore);
        assertThat(admin.getResponse().getCookie("XSRF-TOKEN").getValue()).isNotEqualTo(csrf.getValue());
    }
    @Test
    @DisplayName("비밀번호 찾기는 계정이 맞아도 메일·저장 전에 같은 응답을 돌려주고, 처리는 응답 뒤에 한다")
    void passwordFindRespondsBeforeMatchingWork() throws Exception {
        findWork.deferred = true;
        when(passwords.hashAndDeliver(account.email)).thenReturn(encoder.encode(java.util.UUID.randomUUID().toString()));
        clearInvocations(mapper);
        mvc.perform(postJson("/api/common/password-find", Map.of("userId", account.userId, "email", account.email)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value(AccountAccessService.FIND_MESSAGE));
        verifyNoInteractions(passwords);
        verify(mapper, never()).lockAccount(anyString());
        assertThat(findWork.queued).hasSize(1);
        findWork.queued.forEach(Runnable::run);
        verify(passwords).hashAndDeliver(account.email);
        assertThat(account.temporary).isEqualTo("Y");
    }
    @Test void spoofedActorHeadersCannotChangeIdentity() throws Exception {
        var device = login(); mvc.perform(get("/api/common/my-info").servletPath("/api/common/my-info").session(device)
            .header("X-User-Id", "other").header("X-Roles", "ADMIN").header("X-Forwarded-For", "untrusted"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(account.userId));
    }
    /** 비밀번호 찾기 처리기: 보통은 같은 스레드에서 바로, deferred면 쌓아 두었다가 시험이 직접 돌린다. */
    static class FindWork implements java.util.concurrent.Executor {
        final java.util.List<Runnable> queued = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile boolean deferred;
        @Override public void execute(Runnable work) { if (deferred) queued.add(work); else work.run(); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class Transactions {
        @Bean FindWork accountFindExecutor() { return new FindWork(); }
        @Bean AbstractPlatformTransactionManager accountTestTransactions() {
            return new AbstractPlatformTransactionManager() {
                @Override protected Object doGetTransaction() { return new Object(); }
                @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
                @Override protected void doCommit(DefaultTransactionStatus status) { }
                @Override protected void doRollback(DefaultTransactionStatus status) { }
            };
        }
    }
}
