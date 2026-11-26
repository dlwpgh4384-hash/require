package com.company.common.commonops.controller;

import com.company.common.commonops.dto.AccountAccessContracts.*;
import com.company.common.commonops.service.AccountAccessService;
import com.company.common.commonops.service.AccountAccessFailure;
import com.company.common.commonops.service.LoginFailureDelay;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common")
public class AccountAccessController {
    private final AccountAccessService service;
    private final LoginFailureDelay delay;
    public AccountAccessController(AccountAccessService service, LoginFailureDelay delay) {
        this.service = service;
        this.delay = delay;
    }
    @PostMapping("/login")
    public Response<LoginResult> postCommonLogin(@RequestBody LoginInput input, HttpServletRequest request) {
        try { return service.login(input, request); }
        catch (AccountAccessFailure failure) {
            if (failure.status == 401) delay.failed(request.getRemoteAddr());
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
