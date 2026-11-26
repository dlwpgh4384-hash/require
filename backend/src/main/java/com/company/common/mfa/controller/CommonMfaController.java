package com.company.common.mfa.controller;

import com.company.common.mfa.dto.MfaContracts.*;
import com.company.common.mfa.service.CommonMfaService;
import jakarta.servlet.http.HttpServletRequest;
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
    public Response<Login> postCommonMfaVerify(HttpServletRequest request, @RequestBody Verify input) {
        return service.postCommonMfaVerify(request, input == null ? null : input.code());
    }
}
