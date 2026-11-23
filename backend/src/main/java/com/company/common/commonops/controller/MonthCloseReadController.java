package com.company.common.commonops.controller;

import com.company.common.commonops.dto.MonthCloseReadContracts.Badge;
import com.company.common.commonops.dto.MonthCloseReadContracts.History;
import com.company.common.commonops.service.MonthCloseReadService;
import com.company.common.users.dto.UserContracts.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MonthCloseReadController {
    private final MonthCloseReadService service;

    public MonthCloseReadController(MonthCloseReadService service) {
        this.service = service;
    }

    @GetMapping("/api/common/month-close-badge")
    public Response<Badge> getCommonMonthCloseBadge() {
        return Response.of(service.getCommonMonthCloseBadge());
    }

    @GetMapping("/api/common/month-close-history")
    public Response<History> getCommonMonthCloseHistory(@RequestParam(value = "yearMonth", required = false) String yearMonth) {
        return Response.of(service.getCommonMonthCloseHistory(yearMonth));
    }
}
