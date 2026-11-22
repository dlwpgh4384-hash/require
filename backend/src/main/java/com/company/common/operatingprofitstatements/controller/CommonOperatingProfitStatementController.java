package com.company.common.operatingprofitstatements.controller;

import com.company.common.operatingprofitstatements.dto.OperatingProfitStatementContracts.*;
import com.company.common.operatingprofitstatements.service.*;
import com.company.common.users.dto.UserContracts.Response;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class CommonOperatingProfitStatementController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final CommonOperatingProfitStatementService reports;
    private final OperatingProfitStatementExcelService excel;
    public CommonOperatingProfitStatementController(CommonOperatingProfitStatementService reports,
        OperatingProfitStatementExcelService excel) { this.reports = reports; this.excel = excel; }
    @GetMapping(value = "/api/common/operating-profit-statements", produces = "application/json")
    public Response<Result> getCommonOperatingProfitStatements(@RequestParam Integer year, @RequestParam String baseMonth) {
        return reports.getCommonOperatingProfitStatements(new Filter(year, baseMonth));
    }
    @GetMapping(value = "/api/common/operating-profit-statements/excel", produces = {XLSX, "application/json"})
    public ResponseEntity<byte[]> getCommonOperatingProfitStatementsExcel(@RequestParam Integer year,
        @RequestParam String baseMonth, @RequestParam String amountUnit) {
        var file = excel.getCommonOperatingProfitStatementsExcel(new Filter(year, baseMonth), amountUnit);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .body(file.bytes());
    }
}
