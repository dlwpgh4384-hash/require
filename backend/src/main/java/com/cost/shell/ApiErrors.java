package com.cost.shell;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.ErrorResponse;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception exception) {
        HttpStatusCode status = HttpStatusCode.valueOf(500);
        if (exception instanceof ErrorResponse response) {
            status = response.getStatusCode();
        } else if (exception instanceof ResponseStatusException response) {
            status = response.getStatusCode();
        }
        // Neither the exception text nor SQL/connection details are serialized or logged.
        return ResponseEntity.status(status).body(Map.of("success", false, "meta", Map.of()));
    }
}
