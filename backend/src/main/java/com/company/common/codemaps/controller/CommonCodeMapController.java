package com.company.common.codemaps.controller;

import com.company.common.codemaps.dto.CodeMapContracts.*;
import com.company.common.users.dto.UserContracts.Response;
import com.company.common.codemaps.service.CodeMapRules;
import com.company.common.codemaps.service.CommonCodeMapService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonCodeMapController {
    private final CommonCodeMapService service;

    public CommonCodeMapController(CommonCodeMapService service) { this.service = service; }

    @GetMapping("/api/common/code-maps")
    public Response<Result> getCommonCodeMaps() {
        return Response.of(service.getCommonCodeMaps());
    }

    @PostMapping(value = "/api/common/code-maps", consumes = "application/json")
    public Response<Item> postCommonCodeMap(@RequestBody JsonNode body) {
        return Response.of(service.postCommonCodeMap(CodeMapRules.parseItem(body)));
    }

    @PostMapping(value = "/api/common/code-maps/delete", consumes = "application/json")
    public Response<DeleteResult> postCommonCodeMapDelete(@RequestBody JsonNode body) {
        return Response.of(service.postCommonCodeMapDelete(CodeMapRules.parseDelete(body)));
    }
}
