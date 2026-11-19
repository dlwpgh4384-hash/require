package com.company.common.projectissues.controller;

import com.company.common.projectissues.dto.ProjectIssueContracts.*;
import com.company.common.projectissues.service.ProjectIssueRules;
import com.company.common.projectissues.service.ProjectIssueService;
import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class ProjectIssueController {
    private final ProjectIssueService service;

    public ProjectIssueController(ProjectIssueService service) {
        this.service = service;
    }

    @GetMapping("/api/common/project-issues")
    public Response<IssueList> getCommonProjectBoardIssues(@ModelAttribute Filter filter) {
        return Response.of(service.getCommonProjectBoardIssues(filter));
    }

    @PostMapping(value = "/api/common/project-issues", consumes = "application/json")
    public Response<Issue> postCommonProjectBoardIssue(@RequestBody JsonNode body) {
        return Response.of(service.postCommonProjectBoardIssue(ProjectIssueRules.input(body)));
    }

    @PutMapping(value = "/api/common/project-issues/{issueId}", consumes = "application/json")
    public Response<Issue> putCommonProjectBoardIssue(@PathVariable String issueId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonProjectBoardIssue(issueId, ProjectIssueRules.input(body)));
    }
}
