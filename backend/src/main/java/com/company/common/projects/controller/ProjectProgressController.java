package com.company.common.projects.controller;

import com.company.common.projects.dto.ProjectProgressContracts.*;
import com.company.common.projects.service.ProjectProgressService;
import com.company.common.projects.service.ProjectProgressRulesInput;
import com.company.common.projects.service.ProjectStageService;
import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/common/projects/{projectId}", produces = "application/json")
public class ProjectProgressController {
    private final ProjectProgressService service;
    private final ProjectStageService stages;
    public ProjectProgressController(ProjectProgressService service, ProjectStageService stages) {
        this.service = service;
        this.stages = stages;
    }
    @GetMapping("/weekly-memos")
    public Response<MemoList> getCommonProjectBoardWeeklyMemos(@PathVariable String projectId) {
        return Response.of(service.getCommonProjectBoardWeeklyMemos(projectId));
    }
    @PostMapping(value = "/weekly-memos", consumes = "application/json")
    public Response<Memo> postCommonProjectBoardWeeklyMemo(@PathVariable String projectId, @RequestBody JsonNode body) {
        return Response.of(service.postCommonProjectBoardWeeklyMemo(projectId, body));
    }
    @PutMapping(value = "/weekly-memos/{memoId}", consumes = "application/json")
    public Response<Memo> putCommonProjectBoardWeeklyMemo(@PathVariable String projectId,
        @PathVariable String memoId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonProjectBoardWeeklyMemo(projectId, memoId, body));
    }
    @GetMapping("/steps")
    public Response<StepList> getCommonProjectBoardSteps(@PathVariable String projectId) {
        return Response.of(stages.getCommonProjectBoardSteps(projectId));
    }
    @PutMapping(value = "/steps", consumes = "application/json")
    public Response<StepList> putCommonProjectBoardSteps(@PathVariable String projectId, @RequestBody JsonNode body) {
        return Response.of(stages.putCommonProjectBoardSteps(projectId, ProjectProgressRulesInput.dates(body)));
    }
    @PostMapping("/steps/ensure")
    public Response<StepList> postCommonProjectBoardStepsEnsure(@PathVariable String projectId) {
        return Response.of(stages.postCommonProjectBoardStepsEnsure(projectId));
    }
    @PostMapping("/steps/{stepId}/complete")
    public Response<Step> postCommonProjectBoardStepComplete(@PathVariable String projectId, @PathVariable String stepId) {
        return Response.of(stages.postCommonProjectBoardStepComplete(projectId, stepId));
    }
}
