package com.company.common.manpowercomparisons.controller;

import com.company.common.manpowercomparisons.dto.ManpowerComparisonContracts.*;
import com.company.common.manpowercomparisons.service.CommonManpowerComparisonService;
import com.company.common.users.dto.UserContracts.Response;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonManpowerComparisonController {
    private final CommonManpowerComparisonService service;

    public CommonManpowerComparisonController(CommonManpowerComparisonService service) {
        this.service = service;
    }

    @GetMapping("/api/common/manpower-comparisons")
    public Response<Result> getCommonManpowerComparisons(@RequestParam Integer year,
        @RequestParam(required = false) String projectId,
        @RequestParam(required = false) String differenceStatus) {
        return Response.of(service.getCommonManpowerComparisons(new Filter(year, projectId, differenceStatus)));
    }
}
