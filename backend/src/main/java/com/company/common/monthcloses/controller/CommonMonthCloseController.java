package com.company.common.monthcloses.controller;

import com.company.common.monthcloses.dto.MonthCloseContracts.*;
import com.company.common.monthcloses.service.CommonMonthCloseService;
import com.company.common.monthcloses.service.MonthCloseRules;
import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/common/month-closes", produces = "application/json")
public class CommonMonthCloseController {
    private final CommonMonthCloseService service;
    public CommonMonthCloseController(CommonMonthCloseService service) { this.service = service; }
    @GetMapping("/{yearMonth}")
    public Response<MonthClose> getCommonMonthClose(@PathVariable String yearMonth) {
        return Response.of(service.getCommonMonthClose(yearMonth));
    }
    @GetMapping("/{yearMonth}/reopen-impact")
    public Response<ReopenImpact> getCommonMonthCloseReopenImpact(@PathVariable String yearMonth) {
        return Response.of(service.getCommonMonthCloseReopenImpact(yearMonth));
    }
    @PutMapping("/{yearMonth}/confirm")
    public Response<MonthClose> putCommonMonthCloseConfirm(@PathVariable String yearMonth, @RequestBody JsonNode body) {
        return Response.of(service.putCommonMonthCloseConfirm(yearMonth, new ConfirmInput(MonthCloseRules.version(body))));
    }
    @PutMapping("/{yearMonth}/reopen")
    public Response<MonthClose> putCommonMonthCloseReopen(@PathVariable String yearMonth, @RequestBody JsonNode body) {
        return Response.of(service.putCommonMonthCloseReopen(yearMonth,
            new ReopenInput(MonthCloseRules.version(body), MonthCloseRules.reason(body))));
    }
}
