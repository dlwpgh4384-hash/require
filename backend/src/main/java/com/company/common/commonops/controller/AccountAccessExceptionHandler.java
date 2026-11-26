package com.company.common.commonops.controller;

import com.company.common.commonops.service.AccountAccessFailure;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AccountAccessController.class)
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
