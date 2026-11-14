package com.company.common.roles.controller;

import com.company.common.roles.service.CommonRoleService;
import com.company.common.roles.service.RoleRules;

import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
import static com.company.common.roles.dto.RoleContracts.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonRoleController {
    private final CommonRoleService service;
    public CommonRoleController(CommonRoleService service) { this.service = service; }

    @GetMapping("/api/common/roles")
    public Response<RoleList> getCommonRoles() { return Response.of(service.getCommonRoles()); }
    @GetMapping("/api/common/roles/{roleId}")
    public Response<Settings> getCommonRole(@PathVariable String roleId) {
        return Response.of(service.getCommonRole(roleId));
    }
    @GetMapping("/api/common/roles/{roleId}/change-history")
    public Response<HistoryList> getCommonRoleChangeHistory(@PathVariable String roleId) {
        return Response.of(service.getCommonRoleChangeHistory(roleId));
    }
    @GetMapping("/api/common/roles/{roleId}/change-impact")
    public Response<Impact> getCommonRoleChangeImpact(@PathVariable String roleId, @RequestParam String dataScopeCd) {
        return Response.of(service.getCommonRoleChangeImpact(roleId, dataScopeCd));
    }
    @PostMapping(value = "/api/common/roles", consumes = "application/json")
    public Response<Settings> postCommonRoleCopy(@RequestBody JsonNode body) {
        return Response.of(service.postCommonRoleCopy(RoleRules.copy(body)));
    }
    @PutMapping(value = "/api/common/roles/{roleId}", consumes = "application/json")
    public Response<Settings> putCommonRole(@PathVariable String roleId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonRole(roleId, RoleRules.update(body)));
    }
    @PostMapping("/api/common/roles/{roleId}/stop")
    public Response<StopResult> postCommonRoleStop(@PathVariable String roleId) {
        return Response.of(service.postCommonRoleStop(roleId));
    }
}
