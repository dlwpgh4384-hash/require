package com.company.common.controller;

import com.company.common.dto.ClientContracts.*;
import com.company.common.dto.UserContracts.Response;
import com.company.common.mapper.ClientRows;
import com.company.common.service.ClientRules;
import com.company.common.service.CommonClientService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonClientController {
    private final CommonClientService service;
    public CommonClientController(CommonClientService service) { this.service = service; }

    @GetMapping("/api/common/clients")
    public Response<Items<Item>> getCommonClients(
        @RequestParam(required = false) String clientTypeCode,
        @RequestParam(defaultValue = "Y") String useYn,
        @RequestParam(required = false) String keyword) {
        return Response.of(service.getCommonClients(ClientRules.filter(clientTypeCode, useYn, keyword)));
    }
    @GetMapping("/api/common/clients/{clientId}")
    public Response<Item> getCommonClient(@PathVariable String clientId) {
        return Response.of(service.getCommonClient(clientId));
    }
    @GetMapping("/api/common/clients/{clientId}/assignments")
    public Response<Items<ClientRows.Assignment>> getCommonClientAssignments(@PathVariable String clientId) {
        return Response.of(service.getCommonClientAssignments(clientId));
    }
    @GetMapping("/api/common/clients/{clientId}/change-history")
    public Response<Items<ClientRows.Change>> getCommonClientChangeHistory(@PathVariable String clientId) {
        return Response.of(service.getCommonClientChangeHistory(clientId));
    }
    @GetMapping("/api/common/clients/{clientId}/payment-account")
    public Response<Account> getCommonClientPaymentAccount(@PathVariable String clientId) {
        return Response.of(service.getCommonClientPaymentAccount(clientId));
    }
    @PostMapping(value = "/api/common/clients", consumes = "application/json")
    public Response<Save> postCommonClient(@RequestBody JsonNode body) {
        return Response.of(service.postCommonClient(ClientRules.parse(body, false)));
    }
    @PutMapping(value = "/api/common/clients/{clientId}", consumes = "application/json")
    public Response<Save> putCommonClient(@PathVariable String clientId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonClient(clientId, ClientRules.update(body)));
    }
    @PutMapping(value = "/api/common/clients/use-status", consumes = "application/json")
    public Response<StateResult> putCommonClientUseStatus(@RequestBody JsonNode body) {
        return Response.of(service.putCommonClientUseStatus(ClientRules.state(body)));
    }
}
