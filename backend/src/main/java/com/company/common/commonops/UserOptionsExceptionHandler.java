package com.company.common.commonops;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CommonUserOptionsController.class)
public class UserOptionsExceptionHandler {
    public record FieldError(String field, String message) {}
    public record OptionsError(boolean success, Map<String, Object> meta,
        String message, List<FieldError> errors) {}

    @ExceptionHandler(Exception.class)
    public ResponseEntity<OptionsError> failed(Exception failure) {
        return ResponseEntity.internalServerError().body(new OptionsError(false, Map.of(),
            "선택 목록을 불러오지 못했습니다. 다시 시도해 주세요.", List.of()));
    }
}
