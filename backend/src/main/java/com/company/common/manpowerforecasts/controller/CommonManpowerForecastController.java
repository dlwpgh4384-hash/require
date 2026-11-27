package com.company.common.manpowerforecasts.controller;

import com.company.common.manpowerforecasts.dto.ManpowerForecastContracts.Result;
import com.company.common.manpowerforecasts.service.CommonManpowerForecastService;
import com.company.common.manpowerforecasts.service.ManpowerForecastExcelService;
import com.company.common.users.dto.UserContracts.Response;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonManpowerForecastController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final CommonManpowerForecastService service;
    private final ManpowerForecastExcelService excel;

    public CommonManpowerForecastController(CommonManpowerForecastService service, ManpowerForecastExcelService excel) {
        this.service = service;
        this.excel = excel;
    }

    @GetMapping("/api/common/manpower-forecasts")
    public Response<Result> getCommonManpowerForecasts(@RequestParam Integer year) {
        return Response.of(service.getCommonManpowerForecasts(year));
    }

    @GetMapping(value = "/api/common/manpower-forecasts/excel", produces = {XLSX, "application/json"})
    public ResponseEntity<byte[]> getCommonManpowerForecastsExcel(@RequestParam Integer year) {
        var workbook = excel.getCommonManpowerForecastsExcel(year);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(workbook.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Total-Count", Integer.toString(workbook.count())).body(workbook.bytes());
    }
}
