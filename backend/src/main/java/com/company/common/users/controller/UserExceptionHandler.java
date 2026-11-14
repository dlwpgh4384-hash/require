package com.company.common.users.controller;

import com.company.common.users.service.UserFailure;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@org.springframework.core.annotation.Order(0)
@RestControllerAdvice(assignableTypes = CommonUserController.class)
public class UserExceptionHandler {
    public record FieldError(String field, String message) {}
    public record Error(boolean success, Map<String, Object> meta, String message, List<FieldError> errors) {}
    @ExceptionHandler(UserFailure.class)
    public ResponseEntity<Error> rejected(UserFailure failure) {
        return ResponseEntity.status(failure.getStatus()).body(new Error(false, Map.of(), failure.getMessage(),
            List.of(new FieldError(failure.getField(), failure.getMessage()))));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Error> unreadable(HttpMessageNotReadableException failure) {
        return rejected(UserFailure.invalid("body", "입력 형식을 확인해 주세요."));
    }
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Error> missing(MissingServletRequestParameterException failure) {
        return rejected(UserFailure.invalid(failure.getParameterName(), "필수 조회 조건을 입력해 주세요."));
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Error> type(MethodArgumentTypeMismatchException failure) {
        return rejected(UserFailure.invalid(failure.getName(), "조회 조건 형식을 확인해 주세요."));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Error> failed(Exception failure) {
        return ResponseEntity.internalServerError().body(new Error(false, Map.of(),
            "사용자 정보를 처리하지 못했습니다. 다시 시도해 주세요.", List.of()));
    }
}
