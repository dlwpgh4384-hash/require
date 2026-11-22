package com.company.common.netprofitstatements.controller;

import com.company.common.netprofitstatements.dto.NetProfitStatementContracts.*;
import com.company.common.netprofitstatements.service.*;
import com.company.common.users.dto.UserContracts.Response;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class CommonNetProfitStatementController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final CommonNetProfitStatementService reports;
    private final NetProfitStatementExcelService excel;

    public CommonNetProfitStatementController(CommonNetProfitStatementService reports,
        NetProfitStatementExcelService excel) {
        this.reports = reports;
        this.excel = excel;
    }

    @GetMapping(value = "/api/common/net-profit-statements", produces = "application/json")
    public Response<Result> getCommonNetProfitStatements(@RequestParam Integer year,
        @RequestParam String baseMonth) {
        return reports.getCommonNetProfitStatements(new Filter(year, baseMonth));
    }

    @GetMapping(value = "/api/common/net-profit-statements/excel", produces = {XLSX, "application/json"})
    public ResponseEntity<byte[]> getCommonNetProfitStatementsExcel(@RequestParam Integer year,
        @RequestParam String baseMonth, @RequestParam String amountUnit) {
        var file = excel.getCommonNetProfitStatementsExcel(new Filter(year, baseMonth), amountUnit);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .body(file.bytes());
    }
}
