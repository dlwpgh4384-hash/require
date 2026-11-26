package com.company.common.notifications.controller;

import com.company.common.notifications.service.NotificationFailure;
import com.company.common.support.controller.ApiExceptionHandler;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = CommonNotificationController.class)
public class NotificationExceptionHandler {
    @ExceptionHandler(NotificationFailure.class)
    public ResponseEntity<ApiExceptionHandler.Error> rejected(NotificationFailure failure) {
        return ResponseEntity.status(failure.getStatus()).body(new ApiExceptionHandler.Error(
            false, Map.of(), failure.getMessage(),
            List.of(new ApiExceptionHandler.FieldError(failure.getField(), failure.getMessage()))));
    }
}
