package com.company.common.codehistory.controller;

import com.company.common.codehistory.dto.CodeHistoryContracts.Result;
import com.company.common.users.dto.UserContracts.Response;
import com.company.common.codehistory.service.CommonCodeHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = "application/json")
public class CommonCodeHistoryController {
    private final CommonCodeHistoryService service;

    public CommonCodeHistoryController(CommonCodeHistoryService service) {
        this.service = service;
    }

    @GetMapping("/api/common/code-change-history")
    public Response<Result> getCommonCodeChangeHistory() {
        return Response.of(service.getCommonCodeChangeHistory());
    }
}
