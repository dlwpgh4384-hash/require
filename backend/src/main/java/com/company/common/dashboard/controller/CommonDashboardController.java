package com.company.common.dashboard.controller;

import com.company.common.dashboard.dto.DashboardContracts.Result;
import com.company.common.dashboard.service.CommonDashboardService;
import com.company.common.dashboard.service.DashboardExcelService;
import com.company.common.users.dto.UserContracts.Response;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonDashboardController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final CommonDashboardService dashboard;
    private final DashboardExcelService excel;
    public CommonDashboardController(CommonDashboardService dashboard, DashboardExcelService excel) {
        this.dashboard = dashboard; this.excel = excel;
    }
    @GetMapping("/api/common/dashboard")
    public Response<Result> getCommonDashboard(@RequestParam(required = false) String yearMonth) {
        CommonDashboardService.month(yearMonth);
        return dashboard.getCommonDashboard(yearMonth);
    }
    @GetMapping(value = "/api/common/dashboard/excel", produces = {XLSX, "application/json"})
    public ResponseEntity<byte[]> getCommonDashboardExcel(@RequestParam(required = false) String yearMonth) {
        CommonDashboardService.month(yearMonth);
        var file = excel.getCommonDashboardExcel(yearMonth);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Total-Count", Integer.toString(file.count())).body(file.bytes());
    }
}
