package com.company.common.commonops.controller;

import com.company.common.commonops.dto.BusinessPlanComparisonContracts.*;
import com.company.common.commonops.service.CommonBusinessPlanComparisonService;
import com.company.common.users.dto.UserContracts.Response;
import org.springframework.web.bind.annotation.*;

@RestController
public class CommonBusinessPlanComparisonController {
    private final CommonBusinessPlanComparisonService comparisons;
    public CommonBusinessPlanComparisonController(CommonBusinessPlanComparisonService comparisons) {
        this.comparisons = comparisons;
    }
    @GetMapping(value = "/api/common/business-plan-comparisons", produces = "application/json")
    public Response<Result> getCommonBusinessPlanComparisons(@RequestParam Integer year,
        @RequestParam String baseMonth) {
        return comparisons.getCommonBusinessPlanComparisons(new Filter(year, baseMonth));
    }
}
