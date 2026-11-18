package com.company.common.manpowercomparisons.controller;

import com.company.common.manpowercomparisons.dto.ManpowerComparisonContracts.*;
import com.company.common.manpowercomparisons.service.CommonManpowerComparisonService;
import com.company.common.users.dto.UserContracts.Response;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonManpowerComparisonController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final CommonManpowerComparisonService service;

    public CommonManpowerComparisonController(CommonManpowerComparisonService service) {
        this.service = service;
    }

    @GetMapping("/api/common/manpower-comparisons")
    public Response<Result> getCommonManpowerComparisons(@RequestParam Integer year,
        @RequestParam(required = false) String projectId,
        @RequestParam(required = false) String differenceStatus) {
        return Response.of(service.getCommonManpowerComparisons(new Filter(year, projectId, differenceStatus)));
    }

    /** SFR-011 엑셀 다운로드: 목록과 같은 조건의 전체 결과. 오류는 목록과 같은 JSON(400). */
    @GetMapping(value = "/api/common/manpower-comparisons/excel", produces = {XLSX, "application/json"})
    public ResponseEntity<byte[]> getCommonManpowerComparisonsExcel(@RequestParam Integer year,
        @RequestParam(required = false) String projectId,
        @RequestParam(required = false) String differenceStatus) {
        var file = service.getCommonManpowerComparisonsExcel(new Filter(year, projectId, differenceStatus));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Total-Count", Integer.toString(file.count())).body(file.bytes());
    }
}
