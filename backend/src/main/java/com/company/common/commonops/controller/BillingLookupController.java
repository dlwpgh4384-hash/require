package com.company.common.commonops.controller;

import com.company.common.commonops.dto.BillingLookupContracts.*;
import com.company.common.commonops.service.BillingLookupService;
import com.company.common.users.dto.UserContracts.Response;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces="application/json")
public class BillingLookupController {
    private final BillingLookupService service;
    public BillingLookupController(BillingLookupService service) { this.service=service; }

    @GetMapping("/api/common/bill-options")
    public Response<Options> getCommonBillOptions(@RequestParam(required=false) String projectId,
        @RequestParam(required=false) String billMonth) {
        return Response.of(service.getCommonBillOptions(projectId,billMonth));
    }

    @GetMapping("/api/common/bill-dunning")
    public Response<DunningResult> getCommonBillDunning() { return Response.of(service.getCommonBillDunning()); }
}
