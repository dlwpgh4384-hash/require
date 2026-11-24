package com.company.common.manpowerboard.controller;

import com.company.common.manpowerboard.dto.ManpowerBoardContracts.*;
import com.company.common.manpowerboard.service.CommonManpowerBoardService;
import com.company.common.users.dto.UserContracts.Response;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/manpower-board")
public class CommonManpowerBoardController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final CommonManpowerBoardService service;

    public CommonManpowerBoardController(CommonManpowerBoardService service) {
        this.service = service;
    }

    @GetMapping(produces = "application/json")
    public Response<Result> getCommonManpowerBoard(
        @RequestParam Integer year,
        @RequestParam(required = false) String jobCode,
        @RequestParam(required = false) String gradeCode,
        @RequestParam(required = false) String employeeTypeCode,
        @RequestParam(defaultValue = "false") boolean onlyAvailable) {
        return Response.of(service.getCommonManpowerBoard(
            new Filter(year, jobCode, gradeCode, employeeTypeCode, onlyAvailable)));
    }

    @GetMapping(value = "/excel", produces = {XLSX, "application/json"})
    public ResponseEntity<byte[]> getCommonManpowerBoardExcel(
        @RequestParam Integer year,
        @RequestParam(required = false) String jobCode,
        @RequestParam(required = false) String gradeCode,
        @RequestParam(required = false) String employeeTypeCode,
        @RequestParam(defaultValue = "false") boolean onlyAvailable) {
        var file = service.getCommonManpowerBoardExcel(
            new Filter(year, jobCode, gradeCode, employeeTypeCode, onlyAvailable));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Total-Count", Integer.toString(file.count())).body(file.bytes());
    }
}
