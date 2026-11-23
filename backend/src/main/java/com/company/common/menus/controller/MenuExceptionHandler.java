package com.company.common.menus.controller;

import com.company.common.support.controller.ApiExceptionHandler;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 메뉴 읽기 실패: 공통 오류 모양(success·meta·message·errors)으로 500을 돌려주고 원인은 서버 로그에만 남긴다(내부 내용은 숨김). */
@org.springframework.core.annotation.Order(0)
@RestControllerAdvice(assignableTypes = CommonMenuController.class)
public class MenuExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(MenuExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiExceptionHandler.Error> readFailure(Exception failure) {
        log.error("메뉴 읽기 실패", failure);
        return ResponseEntity.internalServerError().body(new ApiExceptionHandler.Error(false, Map.of(),
            "처리하지 못했습니다. 잠시 후 다시 시도해 주세요.", List.of()));
    }
}
