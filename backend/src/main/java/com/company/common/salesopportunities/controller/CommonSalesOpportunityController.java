package com.company.common.salesopportunities.controller;

import com.company.common.clients.service.ClientRules;
import com.company.common.salesopportunities.dto.SalesOpportunityContracts.*;
import com.company.common.salesopportunities.service.CommonSalesOpportunityService;
import com.company.common.salesopportunities.service.SalesOpportunityRules;
import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/common/sales-opportunities", produces = "application/json")
public class CommonSalesOpportunityController {
    private final CommonSalesOpportunityService service;
    public CommonSalesOpportunityController(CommonSalesOpportunityService service) { this.service = service; }

    @GetMapping("/{opportunityId}")
    public Response<Opportunity> getCommonSalesOpportunity(@PathVariable String opportunityId) {
        return Response.of(service.getCommonSalesOpportunity(opportunityId));
    }
    @PostMapping(consumes = "application/json")
    public Response<Opportunity> postCommonSalesOpportunity(@RequestBody JsonNode body) {
        return Response.of(service.postCommonSalesOpportunity(SalesOpportunityRules.input(body, false)));
    }
    @PutMapping(value = "/{opportunityId}", consumes = "application/json")
    public Response<Opportunity> putCommonSalesOpportunity(@PathVariable String opportunityId, @RequestBody JsonNode body) {
        var version = SalesOpportunityRules.version(body);
        return Response.of(service.putCommonSalesOpportunity(opportunityId, SalesOpportunityRules.input(body, true), version));
    }
    @PutMapping(value = "/{opportunityId}/stage", consumes = "application/json")
    public Response<Opportunity> putCommonSalesOpportunityStage(@PathVariable String opportunityId, @RequestBody JsonNode body) {
        ClientRules.object(body, Set.of("modifiedAt", "stageCode"));
        return Response.of(service.putCommonSalesOpportunityStage(opportunityId, SalesOpportunityRules.version(body),
            SalesOpportunityRules.text(body, "stageCode", 10, true)));
    }
    @PutMapping(value = "/{opportunityId}/approve", consumes = "application/json")
    public Response<Opportunity> putCommonSalesOpportunityApprove(@PathVariable String opportunityId, @RequestBody JsonNode body) {
        ClientRules.object(body, Set.of("modifiedAt"));
        return Response.of(service.approve(opportunityId, SalesOpportunityRules.version(body)));
    }
    @PutMapping(value = "/{opportunityId}/reject", consumes = "application/json")
    public Response<Opportunity> putCommonSalesOpportunityReject(@PathVariable String opportunityId, @RequestBody JsonNode body) {
        ClientRules.object(body, Set.of("modifiedAt", "reason"));
        return Response.of(service.reject(opportunityId, SalesOpportunityRules.version(body),
            SalesOpportunityRules.text(body, "reason", 300, false)));
    }
    @PostMapping(value = "/{opportunityId}/tentative-assignment-check", consumes = "application/json")
    public Response<Warnings> postCommonSalesOpportunityTentativeAssignmentCheck(@PathVariable String opportunityId, @RequestBody JsonNode body) {
        return Response.of(service.check(opportunityId, SalesOpportunityRules.tentative(body)));
    }
    @PutMapping(value = "/{opportunityId}/tentative-assignment", consumes = "application/json")
    public Response<Opportunity> putCommonSalesOpportunityTentativeAssignment(@PathVariable String opportunityId, @RequestBody JsonNode body) {
        return Response.of(service.tentative(opportunityId, SalesOpportunityRules.tentative(body)));
    }
    @PostMapping(value = "/{opportunityId}/win", consumes = "application/json")
    public Response<Opportunity> postCommonSalesOpportunityWin(@PathVariable String opportunityId, @RequestBody JsonNode body) {
        return Response.of(service.win(opportunityId, SalesOpportunityRules.win(body)));
    }
    @PostMapping(value = "/{opportunityId}/lose", consumes = "application/json")
    public Response<Opportunity> postCommonSalesOpportunityLose(@PathVariable String opportunityId, @RequestBody JsonNode body) {
        ClientRules.object(body, Set.of("modifiedAt", "lossReasonCode", "memo"));
        return Response.of(service.lose(opportunityId, SalesOpportunityRules.version(body),
            SalesOpportunityRules.text(body, "lossReasonCode", 10, true), SalesOpportunityRules.text(body, "memo", 500, false)));
    }
    @PostMapping(value = "/{opportunityId}/stop", consumes = "application/json")
    public Response<Opportunity> postCommonSalesOpportunityStop(@PathVariable String opportunityId, @RequestBody JsonNode body) {
        ClientRules.object(body, Set.of("modifiedAt"));
        return Response.of(service.stop(opportunityId, SalesOpportunityRules.version(body)));
    }
    @PostMapping(value = "/{opportunityId}/resume", consumes = "application/json")
    public Response<Opportunity> postCommonSalesOpportunityResume(@PathVariable String opportunityId, @RequestBody JsonNode body) {
        ClientRules.object(body, Set.of("modifiedAt"));
        return Response.of(service.resume(opportunityId, SalesOpportunityRules.version(body)));
    }
}
