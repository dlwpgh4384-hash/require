package com.company.common.projects.controller;

import com.company.common.clients.service.ClientRules;
import com.company.common.projects.dto.ProjectContracts.*;
import com.company.common.projects.service.CommonProjectService;
import com.company.common.projects.service.ProjectRules;
import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonProjectController {
    private final CommonProjectService service;
    public CommonProjectController(CommonProjectService service) { this.service = service; }
    @GetMapping("/api/common/projects")
    public Response<ProjectList> getCommonProjects() { return Response.of(service.getCommonProjects()); }
    @GetMapping("/api/common/projects/{projectId}")
    public Response<Project> getCommonProject(@PathVariable String projectId) {
        return Response.of(service.getCommonProject(projectId));
    }
    @GetMapping("/api/common/projects/{projectId}/change-history")
    public Response<Items<History>> getCommonProjectChangeHistory(@PathVariable String projectId) {
        return Response.of(service.getCommonProjectChangeHistory(projectId));
    }
    @GetMapping("/api/common/projects/{projectId}/sales-parts")
    public Response<Items<SalesPart>> getCommonProjectSalesParts(@PathVariable String projectId) {
        return Response.of(service.getCommonProjectSalesParts(projectId));
    }
    @GetMapping("/api/common/projects/{projectId}/manpower-plan")
    public Response<ManpowerList> getCommonProjectManpowerPlan(@PathVariable String projectId) {
        return Response.of(service.getCommonProjectManpowerPlan(projectId));
    }
    @GetMapping("/api/common/projects/{projectId}/direct-expenses")
    public Response<Items<Expense>> getCommonProjectDirectExpenses(@PathVariable String projectId) {
        return Response.of(service.getCommonProjectDirectExpenses(projectId));
    }
    @GetMapping("/api/common/projects/{projectId}/lose-impact")
    public Response<LoseImpact> getCommonProjectLoseImpact(@PathVariable String projectId) {
        return Response.of(service.getCommonProjectLoseImpact(projectId));
    }
    @GetMapping("/api/common/projects/{projectId}/distribution")
    public Response<Distribution> getCommonProjectDistribution(@PathVariable String projectId) {
        return Response.of(service.getCommonProjectDistribution(projectId));
    }
    @GetMapping(value = "/api/common/projects/{projectId}/distribution/template", produces = {
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/json"})
    public ResponseEntity<byte[]> getCommonProjectDistributionTemplate(@PathVariable String projectId) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=distribution.xlsx")
            .body(service.getCommonProjectDistributionTemplate(projectId));
    }
    @PostMapping(value = "/api/common/projects", consumes = "application/json")
    public Response<Project> postCommonProject(@RequestBody JsonNode body) {
        return Response.of(service.postCommonProject(ProjectRules.input(body, false)));
    }
    @PutMapping(value = "/api/common/projects/{projectId}", consumes = "application/json")
    public Response<Project> putCommonProject(@PathVariable String projectId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonProject(projectId, ProjectRules.input(body, true)));
    }
    @PutMapping(value = "/api/common/projects/{projectId}/budget", consumes = "application/json")
    public Response<Project> putCommonProjectBudget(@PathVariable String projectId, @RequestBody JsonNode body) {
        ClientRules.object(body, Set.of("budgetAmount"));
        return Response.of(service.putCommonProjectBudget(projectId, ProjectRules.money(body, "budgetAmount", true)));
    }
    @PutMapping(value = "/api/common/projects/{projectId}/sales-part", consumes = "application/json")
    public Response<Items<SalesPart>> putCommonProjectSalesPart(@PathVariable String projectId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonProjectSalesPart(projectId, ProjectRules.part(body)));
    }
    @PutMapping(value = "/api/common/projects/{projectId}/manpower-plan", consumes = "application/json")
    public Response<ManpowerList> putCommonProjectManpowerPlan(@PathVariable String projectId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonProjectManpowerPlan(projectId, ProjectRules.manpower(body)));
    }
    @PostMapping(value = "/api/common/projects/{projectId}/direct-expenses", consumes = "application/json")
    public Response<Expense> postCommonProjectDirectExpense(@PathVariable String projectId, @RequestBody JsonNode body) {
        return Response.of(service.postCommonProjectDirectExpense(projectId, ProjectRules.expense(body)));
    }
    @PostMapping("/api/common/projects/{projectId}/direct-expenses/{expenseId}/delete")
    public Response<DeletedExpense> postCommonProjectDirectExpenseDelete(@PathVariable String projectId, @PathVariable String expenseId) {
        return Response.of(service.postCommonProjectDirectExpenseDelete(projectId, expenseId));
    }
    @PostMapping("/api/common/projects/{projectId}/start")
    public Response<Project> postCommonProjectStart(@PathVariable String projectId) {
        return Response.of(service.postCommonProjectStart(projectId));
    }
    @PostMapping(value = "/api/common/projects/{projectId}/lose", consumes = "application/json")
    public Response<Project> postCommonProjectLose(@PathVariable String projectId, @RequestBody JsonNode body) {
        return Response.of(service.postCommonProjectLose(projectId, ProjectRules.lose(body)));
    }
}
