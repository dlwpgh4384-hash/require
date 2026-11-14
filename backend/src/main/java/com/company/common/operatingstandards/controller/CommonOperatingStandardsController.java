package com.company.common.operatingstandards.controller;

import com.company.common.operatingstandards.dto.OperatingStandardsContracts.Result;
import com.company.common.users.dto.UserContracts.Response;
import com.company.common.operatingstandards.service.CommonOperatingStandardsService;
import com.company.common.operatingstandards.service.OperatingStandardsRules;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonOperatingStandardsController {
    private final CommonOperatingStandardsService service;

    public CommonOperatingStandardsController(CommonOperatingStandardsService service) {
        this.service = service;
    }
    @GetMapping("/api/common/operating-standards")
    public Response<Result> getCommonOperatingStandards() {
        return Response.of(service.getCommonOperatingStandards());
    }
    @PutMapping(value = "/api/common/operating-standards", consumes = "application/json")
    public Response<Result> putCommonOperatingStandards(@RequestBody JsonNode body) {
        return Response.of(service.putCommonOperatingStandards(OperatingStandardsRules.parse(body)));
    }
}
