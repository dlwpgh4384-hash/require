package com.company.common.controller;

import com.company.common.service.CodeFailure;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CodeExceptionHandler {
    @ExceptionHandler(CodeFailure.class)
    public ResponseEntity<ApiExceptionHandler.Error> invalid(CodeFailure failure) {
        return ApiExceptionHandler.of(HttpStatus.BAD_REQUEST, failure.getMessage(),
            List.of(new ApiExceptionHandler.FieldError(failure.getField(), failure.getMessage())));
    }
}
