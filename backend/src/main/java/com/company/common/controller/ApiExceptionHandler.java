package com.company.common.controller;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 API의 공통 오류 응답(2026-11-12 사용자 결정).
 * 형식은 { success:false, meta, message, errors:[{field,message}] } 하나로 통일한다.
 * 없는 주소 404, 허용 안 된 방식 405, 잘못된 값 400, 서버 오류 500(내부 내용은 숨김).
 * 화면별 처리기(예: UserExceptionHandler)가 있으면 그쪽이 먼저 받고, 나머지를 여기서 받는다.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    public record FieldError(String field, String message) {}
    public record Error(boolean success, Map<String, Object> meta, String message, List<FieldError> errors) {}

    static ResponseEntity<Error> of(HttpStatus status, String message, List<FieldError> errors) {
        return ResponseEntity.status(status).body(new Error(false, Map.of(), message, errors));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Error> notFound(Exception failure) {
        return of(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다.", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Error> method(HttpRequestMethodNotSupportedException failure) {
        return of(HttpStatus.METHOD_NOT_ALLOWED, "이 주소에서 쓸 수 없는 요청 방식입니다.", List.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Error> missing(MissingServletRequestParameterException failure) {
        return of(HttpStatus.BAD_REQUEST, "필수 입력 값이 없습니다.",
            List.of(new FieldError(failure.getParameterName(), "필수 입력 값이 없습니다.")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Error> type(MethodArgumentTypeMismatchException failure) {
        return of(HttpStatus.BAD_REQUEST, "입력 값의 형식을 확인해 주세요.",
            List.of(new FieldError(failure.getName(), "입력 값의 형식을 확인해 주세요.")));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<Error> unreadable(Exception failure) {
        return of(HttpStatus.BAD_REQUEST, "입력 형식을 확인해 주세요.", List.of(new FieldError("body", "입력 형식을 확인해 주세요.")));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Error> uploadTooLarge(MaxUploadSizeExceededException failure) {
        String message = "10,000,000 bytes 이하의 엑셀 파일을 선택해 주세요.";
        return of(HttpStatus.BAD_REQUEST, message, List.of(new FieldError("file", message)));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Error> notAcceptable(HttpMediaTypeNotAcceptableException failure) {
        return of(HttpStatus.NOT_ACCEPTABLE, "요청한 응답 형식을 줄 수 없습니다.", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Error> failed(Exception failure) {
        // 내부 내용(SQL·경로 등)은 응답에 넣지 않고 서버 기록에만 남긴다.
        log.error("API 처리 실패", failure);
        return of(HttpStatus.INTERNAL_SERVER_ERROR, "처리하지 못했습니다. 잠시 후 다시 시도해 주세요.", List.of());
    }
}
