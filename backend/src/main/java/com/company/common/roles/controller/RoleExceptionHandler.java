package com.company.common.roles.controller;

import com.company.common.roles.service.RoleFailure;

import com.company.common.support.controller.ApiExceptionHandler;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = CommonRoleController.class)
public class RoleExceptionHandler {
    @ExceptionHandler(RoleFailure.class)
    public ResponseEntity<ApiExceptionHandler.Error> rejected(RoleFailure failure) {
        return ResponseEntity.status(failure.getStatus()).body(new ApiExceptionHandler.Error(
            false, Map.of(), failure.getMessage(),
            List.of(new ApiExceptionHandler.FieldError(failure.getField(), failure.getMessage()))));
    }
}
