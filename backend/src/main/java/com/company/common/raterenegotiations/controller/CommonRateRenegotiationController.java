package com.company.common.raterenegotiations.controller;

import com.company.common.raterenegotiations.dto.RateRenegotiationContracts.*;
import com.company.common.raterenegotiations.service.CommonRateRenegotiationService;
import com.company.common.raterenegotiations.service.RateRenegotiationExcelService;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.users.dto.UserContracts.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonRateRenegotiationController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final CommonRateRenegotiationService service;
    private final RateRenegotiationExcelService excel;

    public CommonRateRenegotiationController(CommonRateRenegotiationService service, RateRenegotiationExcelService excel) {
        this.service = service;
        this.excel = excel;
    }
    @GetMapping("/api/common/rate-renegotiations")
    public Response<Result> getCommonRateRenegotiations(@ModelAttribute Filter filter) {
        return Response.of(service.getCommonRateRenegotiations(filter));
    }
    @GetMapping(value = "/api/common/rate-renegotiations/excel", produces = {XLSX, "application/json"})
    public ResponseEntity<byte[]> getCommonRateRenegotiationsExcel(@ModelAttribute Filter filter,
        @RequestParam(required = false) List<String> columns) {
        var file = excel.getCommonRateRenegotiationsExcel(filter, columns);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Total-Count", Integer.toString(file.count())).body(file.bytes());
    }
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiExceptionHandler.Error> invalidQuery(BindException failure) {
        var errors = failure.getFieldErrors().stream().map(error -> new ApiExceptionHandler.FieldError(
            error.getField(), switch (error.getField()) {
                case "increaseRate" -> "인상률은 숫자로 입력해 주세요.";
                case "renewalMonths" -> "갱신 시점은 3개월, 6개월, 12개월 중 선택해 주세요.";
                case "profitRateThreshold" -> "이익률은 10%, 15%, 20% 중 선택해 주세요.";
                default -> "입력 값의 형식을 확인해 주세요.";
            })).toList();
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(
            new ApiExceptionHandler.Error(false, Map.of(), "조회 조건을 확인해 주세요.", errors));
    }
}
