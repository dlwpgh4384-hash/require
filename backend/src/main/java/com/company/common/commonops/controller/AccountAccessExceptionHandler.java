package com.company.common.commonops.controller;

import com.company.common.commonops.service.AccountAccessFailure;
import java.util.List;
import java.util.Map;
import com.company.common.mfa.controller.CommonMfaController;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 순서를 주지 않으면 공통 처리기(ApiExceptionHandler, 가장 뒤)와 같은 순위가 되어, 앱 묶음(jar)에서는
 * 공통 처리기가 먼저 잡아 429·401이 500으로 바뀌었다. 0으로 두어 늘 먼저 받는다.
 * 인증번호 확인도 로그인 마무리(계정 상태 확인)에서 이 실패를 던질 수 있어 함께 받는다(인증번호 자체 실패는 MfaExceptionHandler가 먼저). */
@Order(0)
@RestControllerAdvice(assignableTypes = {AccountAccessController.class, CommonMfaController.class})
public class AccountAccessExceptionHandler {
    @ExceptionHandler(AccountAccessFailure.class)
    public ResponseEntity<?> failure(AccountAccessFailure failure) {
        return ResponseEntity.status(failure.status).body(Map.of("success", false, "meta", Map.of(),
            "message", failure.getMessage(), "errors", List.of(Map.of("field", failure.field, "message", failure.getMessage()))));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> malformed(HttpMessageNotReadableException failure) {
        return failure(new AccountAccessFailure(400, "body", "입력 형식을 확인해 주세요."));
    }
}
