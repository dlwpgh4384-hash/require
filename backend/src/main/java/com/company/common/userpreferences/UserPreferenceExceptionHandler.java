package com.company.common.userpreferences;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CommonUserPreferenceController.class)
public class UserPreferenceExceptionHandler {
    public record FieldError(String field, String message) {}
    public record UserPreferenceError(boolean success, Map<String, Object> meta,
        String message, List<FieldError> errors) {}

    @ExceptionHandler(UserPreferenceFailure.class)
    public ResponseEntity<UserPreferenceError> rejected(UserPreferenceFailure failure) {
        return ResponseEntity.status(failure.getStatus()).body(new UserPreferenceError(false, Map.of(),
            failure.getMessage(), List.of(new FieldError(failure.getField(), failure.getMessage()))));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<UserPreferenceError> unreadable(HttpMessageNotReadableException failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof UserPreferenceFailure rejected) return rejected(rejected);
            cause = cause.getCause();
        }
        return rejected(UserPreferenceFailure.invalid("prefVal", "입력 형식을 확인해 주세요."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<UserPreferenceError> failed(Exception failure) {
        return ResponseEntity.internalServerError().body(new UserPreferenceError(false, Map.of(),
            "설정을 저장하거나 불러오지 못했습니다. 다시 시도해 주세요.", List.of()));
    }
}
