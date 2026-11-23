package com.company.common.cashflowstatements.controller;

import com.company.common.cashflowstatements.dto.CashFlowStatementContracts.*;
import com.company.common.cashflowstatements.service.*;
import com.company.common.users.dto.UserContracts.Response;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class CommonCashFlowStatementController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final CommonCashFlowStatementService reports;
    private final CashFlowStatementExcelService excel;
    public CommonCashFlowStatementController(CommonCashFlowStatementService reports, CashFlowStatementExcelService excel) {
        this.reports = reports;
        this.excel = excel;
    }
    @GetMapping(value = "/api/common/cash-flow-statements", produces = "application/json")
    public Response<Result> getCommonCashFlowStatements(@RequestParam Integer year) {
        return reports.getCommonCashFlowStatements(new Filter(year));
    }
    @GetMapping(value = "/api/common/cash-flow-statements/excel", produces = {XLSX, "application/json"})
    public ResponseEntity<byte[]> getCommonCashFlowStatementsExcel(@RequestParam Integer year, @RequestParam String amountUnit) {
        var file = excel.getCommonCashFlowStatementsExcel(new Filter(year), amountUnit);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build().toString()).body(file.bytes());
    }
}
