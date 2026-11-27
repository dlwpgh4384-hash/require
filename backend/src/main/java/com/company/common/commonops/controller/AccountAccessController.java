package com.company.common.commonops.controller;

import com.company.common.commonops.dto.AccountAccessContracts.*;
import com.company.common.commonops.service.AccountAccessService;
import com.company.common.commonops.service.AccountAccessFailure;
import com.company.common.commonops.service.LoginAttemptLimit;
import com.company.common.support.auth.LoginCsrfToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common")
public class AccountAccessController {
    private final AccountAccessService service;
    private final LoginAttemptLimit limit;
    public AccountAccessController(AccountAccessService service, LoginAttemptLimit limit) {
        this.service = service;
        this.limit = limit;
    }
    @PostMapping("/login")
    public Response<LoginResult> postCommonLogin(@RequestBody LoginInput input, HttpServletRequest request,
        HttpServletResponse response) {
        String ip = request.getRemoteAddr();
        // 같은 IP 실패가 많으면 기다리게 하지 않고 바로 거절한다(비밀번호 확인·DB 잠금도 하지 않음).
        if (limit.blocked(ip)) throw new AccountAccessFailure(429, "userId", LoginAttemptLimit.TOO_MANY);
        try {
            var result = service.login(input, request);
            limit.succeeded(ip);
            // 비밀번호 단계(관리자는 인증번호 대기)와 로그인 완료 모두 요청 토큰을 새로 만든다.
            LoginCsrfToken.rotate(request, response);
            return result;
        } catch (AccountAccessFailure failure) {
            if (failure.status == 401) limit.failed(ip);
            throw failure;
        }
    }
    @GetMapping("/my-info")
    public Response<MyInfo> getCommonMyInfo(HttpServletRequest request) { return service.myInfo(request); }
    @PostMapping("/logout")
    public ResponseEntity<Void> postCommonLogout(HttpServletRequest request) {
        service.logout(request);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/password-find")
    public Response<FindResult> postCommonPasswordFind(@RequestBody FindInput input) { return service.find(input); }
    @PutMapping("/my-password")
    public Response<PasswordResult> putCommonMyPassword(@RequestBody PasswordInput input, HttpServletRequest request) {
        return service.change(input, request);
    }
}
