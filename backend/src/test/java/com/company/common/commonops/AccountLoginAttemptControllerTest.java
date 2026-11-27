package com.company.common.commonops;

import com.company.common.commonops.controller.AccountAccessController;
import com.company.common.commonops.controller.AccountAccessExceptionHandler;
import com.company.common.commonops.service.AccountAccessFailure;
import com.company.common.commonops.service.AccountAccessService;
import com.company.common.commonops.service.LoginAttemptLimit;
import com.company.common.mfa.controller.CommonMfaController;
import com.company.common.mfa.controller.MfaExceptionHandler;
import com.company.common.mfa.service.CommonMfaService;
import com.company.common.support.controller.ApiExceptionHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 공통 처리기(ApiExceptionHandler)를 먼저 등록해도 로그인 실패가 자기 상태(401·429)로 나가는지 본다(앱 묶음에서 500으로 바뀌던 문제). */
class AccountLoginAttemptControllerTest {
    private final Instant start = Instant.parse("2026-01-02T00:00:00Z");
    private final AtomicReference<Instant> now = new AtomicReference<>(start);
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };
    private AccountAccessService service;
    private CommonMfaService mfa;
    private MockMvc mvc;

    @BeforeEach void setup() {
        service = mock(AccountAccessService.class);
        mfa = mock(CommonMfaService.class);
        when(service.login(any(), any())).thenThrow(new AccountAccessFailure(401, "password", "아이디 또는 비밀번호가 맞지 않습니다."));
        mvc = MockMvcBuilders.standaloneSetup(new AccountAccessController(service, new LoginAttemptLimit(clock)),
                new CommonMfaController(mfa))
            // 공통 처리기를 앞에 둔다: 순서가 같으면 먼저 등록된 쪽이 Exception으로 잡아 500이 된다.
            .setControllerAdvice(new ApiExceptionHandler(), new MfaExceptionHandler(), new AccountAccessExceptionHandler())
            .build();
    }

    private org.springframework.test.web.servlet.ResultActions login(String ip) throws Exception {
        return mvc.perform(post("/api/common/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"fake-user\",\"password\":\"wrong\"}")
            .with(request -> { request.setRemoteAddr(ip); return request; }));
    }

    @Test
    @DisplayName("같은 IP 실패 20번은 401, 21번째는 429와 표준 오류 본문·문구")
    void twentyFirstFailureFromOneAddressIs429WithMessage() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++)
            login("192.0.2.20").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        login("192.0.2.20").andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(LoginAttemptLimit.TOO_MANY))
            .andExpect(jsonPath("$.errors[0].field").value("userId"))
            .andExpect(jsonPath("$.errors[0].message").value(LoginAttemptLimit.TOO_MANY));
        verify(service, times(20)).login(any(), any());
        login("192.0.2.21").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("막힌 뒤 60초가 지나면 다시 비밀번호 확인까지 간다(401)")
    void blockExpiresAfterTheWindow() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) login("192.0.2.30").andExpect(status().isUnauthorized());
        now.set(start.plusSeconds(59));
        login("192.0.2.30").andExpect(status().isTooManyRequests());
        now.set(start.plusSeconds(60));
        login("192.0.2.30").andExpect(status().isUnauthorized());
        verify(service, times(21)).login(any(), any());
    }

    @Test
    @DisplayName("성공하면 그 IP 실패 횟수를 비운다")
    void successResetsTheAddress() throws Exception {
        for (int attempt = 0; attempt < 19; attempt++) login("192.0.2.40").andExpect(status().isUnauthorized());
        doReturn(null).when(service).login(any(), any());
        login("192.0.2.40").andExpect(status().isOk());
        doThrow(new AccountAccessFailure(401, "password", "x")).when(service).login(any(), any());
        for (int attempt = 0; attempt < 20; attempt++) login("192.0.2.40").andExpect(status().isUnauthorized());
        login("192.0.2.40").andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("인증번호 확인 중 계정 실패(로그인 마무리)도 500이 아니라 401")
    void accountFailureDuringMfaVerifyKeepsItsStatus() throws Exception {
        when(mfa.postCommonMfaVerify(any(), anyString())).thenThrow(AccountAccessFailure.unauthorized());
        mvc.perform(post("/api/common/mfa/verify").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("다시 로그인해 주세요."));
    }
}
