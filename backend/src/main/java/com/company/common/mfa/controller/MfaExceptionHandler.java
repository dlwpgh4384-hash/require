package com.company.common.mfa.controller;

import com.company.common.mfa.service.MfaFailure;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CommonMfaController.class)
public class MfaExceptionHandler {
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<?> unreadable() {
        return failure(MfaFailure.invalid(com.company.common.mfa.service.CommonMfaService.CODE_LENGTH));
    }

    @ExceptionHandler(MfaFailure.class)
    public ResponseEntity<?> failure(MfaFailure failure) {
        Map<String, Object> meta = failure.restart() ? Map.of("nextScreenId", "UI-22") : Map.of();
        return ResponseEntity.status(failure.status()).body(Map.of(
            "success", false, "meta", meta, "message", failure.getMessage(),
            "errors", List.of(Map.of("field", failure.field(), "message", failure.getMessage()))));
    }
}
