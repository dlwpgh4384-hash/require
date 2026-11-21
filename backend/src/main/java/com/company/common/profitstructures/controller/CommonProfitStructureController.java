package com.company.common.profitstructures.controller;

import com.company.common.profitstructures.dto.ProfitStructureContracts.*;
import com.company.common.profitstructures.service.CommonProfitStructureService;
import com.company.common.profitstructures.service.ProfitStructureExcelService;
import com.company.common.users.dto.UserContracts.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class CommonProfitStructureController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final CommonProfitStructureService service;
    private final ProfitStructureExcelService excel;
    public CommonProfitStructureController(CommonProfitStructureService service, ProfitStructureExcelService excel) {
        this.service = service; this.excel = excel;
    }
    @GetMapping(value = "/api/common/profit-structures", produces = "application/json")
    public Response<Result> getCommonProfitStructures(@RequestParam Integer year, @RequestParam String periodUnit,
        @RequestParam String baseMonth) {
        return service.getCommonProfitStructures(new Filter(year, periodUnit, baseMonth));
    }
    @GetMapping(value = "/api/common/profit-structures/excel", produces = {XLSX, "application/json"})
    public ResponseEntity<byte[]> getCommonProfitStructuresExcel(@RequestParam Integer year, @RequestParam String periodUnit,
        @RequestParam String baseMonth, @RequestParam List<String> columns) {
        var file = excel.getCommonProfitStructuresExcel(new Filter(year, periodUnit, baseMonth), columns);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Total-Count", Integer.toString(file.count())).body(file.bytes());
    }
}
