package com.company.common.mfa.controller;

import com.company.common.mfa.dto.MfaContracts.*;
import com.company.common.mfa.service.CommonMfaService;
import com.company.common.support.auth.LoginCsrfToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommonMfaController {
    private final CommonMfaService service;

    public CommonMfaController(CommonMfaService service) { this.service = service; }

    @PostMapping("/api/common/mfa/resend")
    public Response<Delivery> postCommonMfaResend(HttpServletRequest request) {
        return service.postCommonMfaResend(request);
    }

    @PostMapping("/api/common/mfa/verify")
    public Response<Login> postCommonMfaVerify(HttpServletRequest request, HttpServletResponse response,
        @RequestBody Verify input) {
        var result = service.postCommonMfaVerify(request, input == null ? null : input.code());
        // 로그인이 끝났으니 요청 토큰을 새로 만든다.
        LoginCsrfToken.rotate(request, response);
        return result;
    }
}
