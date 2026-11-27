package com.company.common.personalcostprofits.controller;

import com.company.common.personalcostprofits.dto.PersonalCostProfitContracts.*;
import com.company.common.personalcostprofits.service.CommonPersonalCostProfitService;
import com.company.common.personalcostprofits.service.PersonalCostProfitExcelService;
import com.company.common.users.dto.UserContracts.Response;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonPersonalCostProfitController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final CommonPersonalCostProfitService service;
    private final PersonalCostProfitExcelService excel;

    public CommonPersonalCostProfitController(CommonPersonalCostProfitService service, PersonalCostProfitExcelService excel) {
        this.service = service;
        this.excel = excel;
    }
    @GetMapping("/api/common/personal-cost-profits")
    public Response<Result> getCommonPersonalCostProfits(@ModelAttribute Filter filter) {
        var result = service.getCommonPersonalCostProfits(filter);
        // 급여·개인별 원가를 본 조회만 민감 항목 보기로 남긴다(명세 ⑤-4). 읽기 전용 조회 밖의 따로 저장.
        service.recordSensitiveView(filter);
        return Response.of(result);
    }
    @ExceptionHandler(org.springframework.validation.BindException.class)
    public ResponseEntity<com.company.common.support.controller.ApiExceptionHandler.Error> invalidQuery(
        org.springframework.validation.BindException failure) {
        var errors = failure.getFieldErrors().stream().map(error ->
            new com.company.common.support.controller.ApiExceptionHandler.FieldError(error.getField(), "입력 값의 형식을 확인해 주세요.")).toList();
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(
            new com.company.common.support.controller.ApiExceptionHandler.Error(false, java.util.Map.of(), "입력 값의 형식을 확인해 주세요.", errors));
    }
    @GetMapping(value = "/api/common/personal-cost-profits/excel", produces = {XLSX, "application/json"})
    public ResponseEntity<byte[]> getCommonPersonalCostProfitsExcel(@ModelAttribute Filter filter) {
        var file = excel.getCommonPersonalCostProfitsExcel(filter);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Total-Count", Integer.toString(file.count())).body(file.bytes());
    }
}
