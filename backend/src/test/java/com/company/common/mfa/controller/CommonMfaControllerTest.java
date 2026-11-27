package com.company.common.mfa.controller;

import com.company.common.mfa.dto.MfaContracts.*;
import com.company.common.mfa.service.CommonMfaService;
import com.company.common.mfa.service.MfaFailure;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonMfaController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {CommonMfaController.class, MfaExceptionHandler.class})
class CommonMfaControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean CommonMfaService service;

    @Test void verifyReturnsCompletionMetadata() throws Exception {
        when(service.postCommonMfaVerify(any(), eq("123456"))).thenReturn(
            new Response<>(true, Map.of("modifiedAt", "2026-01-02T00:00:00Z"), new Login("UI-40", "시험 사용자")));
        mvc.perform(post("/api/common/mfa/verify").contentType("application/json")
            .content(json.writeValueAsString(Map.of("code", "123456"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nextScreenId").value("UI-40")).andExpect(jsonPath("$.data.userName").value("시험 사용자"))
            .andExpect(com.company.common.support.controller.OpenApiResponseContract.response("POST", "/api/common/mfa/verify"))
            .andExpect(jsonPath("$.meta.modifiedAt").value("2026-01-02T00:00:00Z"));
    }
    @Test void resendHasNoRequestBodyAndExposesOnlyDeliveryMetadata() throws Exception {
        var expiry = Instant.parse("2026-01-02T00:05:00Z");
        when(service.postCommonMfaResend(any())).thenReturn(Response.of(new Delivery("a***@example.invalid", expiry, expiry.minusSeconds(240))));
        mvc.perform(post("/api/common/mfa/resend")).andExpect(status().isOk())
            .andExpect(com.company.common.support.controller.OpenApiResponseContract.response("POST", "/api/common/mfa/resend"))
            .andExpect(jsonPath("$.data.maskedEmail").value("a***@example.invalid"))
            .andExpect(jsonPath("$.data.expiresAt").value(expiry.toString()))
            .andExpect(jsonPath("$.data.code").doesNotExist());
    }
    @Test void verifyWithoutPendingContextIsUnauthorized() throws Exception {
        when(service.postCommonMfaVerify(any(), any())).thenThrow(MfaFailure.unauthorized());
        mvc.perform(post("/api/common/mfa/verify").contentType("application/json").content("{\"code\":\"123456\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
    }
    @Test void resendWithoutPendingContextIsUnauthorized() throws Exception {
        when(service.postCommonMfaResend(any())).thenThrow(MfaFailure.unauthorized());
        mvc.perform(post("/api/common/mfa/resend")).andExpect(status().isUnauthorized());
    }
    @Test void fiveDigitsUsesApprovedErrorArray() throws Exception {
        when(service.postCommonMfaVerify(any(), eq("12345"))).thenThrow(MfaFailure.invalid(CommonMfaService.CODE_LENGTH));
        mvc.perform(post("/api/common/mfa/verify").contentType("application/json").content("{\"code\":\"12345\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("code"))
            .andExpect(jsonPath("$.errors[0].message").value("인증번호 6자리를 넣어 주세요."));
    }
    @Test void malformedJsonUsesApprovedErrorEnvelope() throws Exception {
        mvc.perform(post("/api/common/mfa/verify").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("code"));
        verifyNoInteractions(service);
    }
    @Test void fifthMistakeReturnsRestartDestination() throws Exception {
        when(service.postCommonMfaVerify(any(), any())).thenThrow(new MfaFailure(400, "code", "인증번호를 5번 틀렸습니다. 처음부터 다시 로그인해 주세요.", true));
        mvc.perform(post("/api/common/mfa/verify").contentType("application/json").content("{\"code\":\"123456\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.meta.nextScreenId").value("UI-22"));
    }
}
