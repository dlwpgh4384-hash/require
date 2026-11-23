package com.company.common.candidates.controller;

import com.company.common.candidates.dto.CandidateContracts.*;
import com.company.common.candidates.service.CandidateRules;
import com.company.common.candidates.service.CommonCandidateService;
import com.company.common.clients.service.ClientRules;
import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/common/candidates", produces = "application/json")
public class CommonCandidateController {
    private final CommonCandidateService service;
    public CommonCandidateController(CommonCandidateService service) { this.service = service; }

    @GetMapping
    public Response<Items<Candidate>> getCommonCandidates(@ModelAttribute Filter filter) {
        return Response.of(service.getCommonCandidates(filter));
    }
    @GetMapping("/{candidateId}")
    public Response<Candidate> getCommonCandidate(@PathVariable String candidateId) {
        return Response.of(service.getCommonCandidate(candidateId));
    }
    @GetMapping("/{candidateId}/contacts")
    public Response<Items<Contact>> getCommonCandidateContacts(@PathVariable String candidateId) {
        return Response.of(service.getCommonCandidateContacts(candidateId));
    }
    @GetMapping("/{candidateId}/matches")
    public Response<Items<Match>> getCommonCandidateMatches(@PathVariable String candidateId) {
        return Response.of(service.getCommonCandidateMatches(candidateId));
    }
    @PostMapping(consumes = "application/json")
    public Response<Candidate> postCommonCandidate(@RequestBody JsonNode body) {
        return Response.of(service.postCommonCandidate(CandidateRules.input(body, false)));
    }
    @PutMapping(value = "/{candidateId}", consumes = "application/json")
    public Response<Candidate> putCommonCandidate(@PathVariable String candidateId, @RequestBody JsonNode body) {
        var input = CandidateRules.input(body, true);
        return Response.of(service.putCommonCandidate(candidateId, input, ClientRules.token(body)));
    }
    @PostMapping(value = "/{candidateId}/contacts", consumes = "application/json")
    public Response<Candidate> postCommonCandidateContact(@PathVariable String candidateId, @RequestBody JsonNode body) {
        return Response.of(service.postCommonCandidateContact(candidateId, CandidateRules.contact(body)));
    }
}
