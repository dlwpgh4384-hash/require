package com.company.common.billchecks.controller;

import com.company.common.billchecks.dto.BillCheckContracts.*;
import com.company.common.billchecks.service.BillCheckService;
import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces="application/json")
public class BillCheckController {
    private final BillCheckService service;
    public BillCheckController(BillCheckService service) { this.service=service; }

    @GetMapping("/api/common/bill-checks")
    public Response<CheckResult> getCommonBillChecks(@RequestParam(required=false) String checkMonth) {
        return Response.of(service.getCommonBillChecks(checkMonth));
    }
    @PostMapping("/api/common/bill-checks/missing")
    public Response<Detail> postCommonBillCheckMissing(@RequestBody JsonNode body) {
        return Response.of(service.postCommonBillCheckMissing(body));
    }
    @PostMapping("/api/common/bill-checks/additional")
    public Response<Detail> postCommonBillCheckAdditional(@RequestBody JsonNode body) {
        return Response.of(service.postCommonBillCheckAdditional(body));
    }
    @PostMapping("/api/common/bill-checks/correction")
    public Response<Detail> postCommonBillCheckCorrection(@RequestBody JsonNode body) {
        return Response.of(service.postCommonBillCheckCorrection(body));
    }
}
