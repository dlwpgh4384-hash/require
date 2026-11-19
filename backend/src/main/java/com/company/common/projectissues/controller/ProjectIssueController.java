package com.company.common.projectissues.controller;

import com.company.common.projectissues.dto.ProjectIssueContracts.*;
import com.company.common.projectissues.service.ProjectIssueRules;
import com.company.common.projectissues.service.ProjectIssueService;
import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class ProjectIssueController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final ProjectIssueService service;

    public ProjectIssueController(ProjectIssueService service) {
        this.service = service;
    }

    @GetMapping("/api/common/project-issues")
    public Response<IssueList> getCommonProjectBoardIssues(@ModelAttribute Filter filter) {
        return Response.of(service.getCommonProjectBoardIssues(filter));
    }

    /** 이슈 모아 보기 엑셀(SFR-011): 목록과 같은 조건의 전체 결과. 오류는 목록과 같은 JSON(400). */
    @GetMapping(value = "/api/common/project-issues/excel", produces = {XLSX, "application/json"})
    public ResponseEntity<byte[]> getCommonProjectBoardIssuesExcel(@ModelAttribute Filter filter) {
        var file = service.getCommonProjectBoardIssuesExcel(filter);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Total-Count", Integer.toString(file.count())).body(file.bytes());
    }

    @PostMapping(value = "/api/common/project-issues", consumes = "application/json")
    public Response<Issue> postCommonProjectBoardIssue(@RequestBody JsonNode body) {
        return Response.of(service.postCommonProjectBoardIssue(ProjectIssueRules.input(body)));
    }

    @PutMapping(value = "/api/common/project-issues/{issueId}", consumes = "application/json")
    public Response<Issue> putCommonProjectBoardIssue(@PathVariable String issueId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonProjectBoardIssue(issueId, ProjectIssueRules.input(body, true)));
    }
}
