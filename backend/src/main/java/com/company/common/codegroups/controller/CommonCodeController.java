package com.company.common.codegroups.controller;

import com.company.common.codegroups.dto.CodeContracts.*;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonCodeController {
    private final CommonCodeService service;
    public CommonCodeController(CommonCodeService service) { this.service = service; }
    @GetMapping("/api/common/code-groups")
    public Response<Groups> getCommonCodeGroups() { return Response.of(service.getCommonCodeGroups()); }
    @GetMapping("/api/common/code-groups/{groupId}/codes")
    public Response<Codes> getCommonCodes(@PathVariable String groupId) {
        return Response.of(service.getCommonCodes(groupId));
    }
    @PostMapping(value = "/api/common/code-groups", consumes = "application/json")
    public Response<Group> postCommonCodeGroup(@RequestBody JsonNode body) {
        return Response.of(service.postCommonCodeGroup(body));
    }
    @PostMapping(value = "/api/common/code-groups/{groupId}/codes", consumes = "application/json")
    public Response<Item> postCommonCode(@PathVariable String groupId, @RequestBody JsonNode body) {
        return Response.of(service.postCommonCode(groupId, body));
    }
    @PutMapping(value = "/api/common/code-groups/{groupId}/codes", consumes = "application/json")
    public Response<Codes> putCommonCodes(@PathVariable String groupId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonCodes(groupId, body));
    }
    @PostMapping("/api/common/code-groups/{groupId}/codes/{code}/delete")
    public Response<Deleted> postCommonCodeDelete(@PathVariable String groupId, @PathVariable String code) {
        return Response.of(service.postCommonCodeDelete(groupId, code));
    }
}
