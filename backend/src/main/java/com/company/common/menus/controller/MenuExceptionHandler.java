package com.company.common.menus.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@org.springframework.core.annotation.Order(0)
@RestControllerAdvice(assignableTypes = CommonMenuController.class)
public class MenuExceptionHandler {
    public record MenuFailure(boolean success, Map<String, Object> meta) {}

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MenuFailure> readFailure(Exception failure) {
        return ResponseEntity.internalServerError().body(new MenuFailure(false, Map.of()));
    }
}
