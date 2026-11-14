package com.company.common.menus.controller;

import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.menus.service.MenuSettingsFailure;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = CommonMenuSettingsController.class)
public class MenuSettingsExceptionHandler {
    @ExceptionHandler(MenuSettingsFailure.class)
    public ResponseEntity<ApiExceptionHandler.Error> rejected(MenuSettingsFailure failure) {
        return ResponseEntity.badRequest().body(new ApiExceptionHandler.Error(false, Map.of(),
            failure.getMessage(), List.of(new ApiExceptionHandler.FieldError(
                failure.getField(), failure.getMessage()))));
    }
}
