package com.company.common.commonops.controller;

import com.company.common.commonops.dto.PartSettlementCloseContracts.Check;
import com.company.common.commonops.service.PartSettlementCloseService;
import com.company.common.users.dto.UserContracts.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PartSettlementCloseController {
    private final PartSettlementCloseService service;

    public PartSettlementCloseController(PartSettlementCloseService service) {
        this.service = service;
    }

    @GetMapping("/api/common/part-settlement-close-checks")
    public Response<Check> getCommonPartSettlementCloseCheck(@RequestParam("yearMonth") String yearMonth) {
        return Response.of(service.check(yearMonth));
    }
}
