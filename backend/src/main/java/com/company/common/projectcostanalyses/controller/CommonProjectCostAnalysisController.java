package com.company.common.projectcostanalyses.controller;

import com.company.common.projectcostanalyses.dto.ProjectCostAnalysisContracts.*;
import com.company.common.projectcostanalyses.service.CommonProjectCostAnalysisService;
import com.company.common.projectcostanalyses.service.ProjectCostAnalysisExcelService;
import com.company.common.users.dto.UserContracts.Response;
import com.company.common.support.controller.ApiExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/common/project-cost-analyses", produces = "application/json")
public class CommonProjectCostAnalysisController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final CommonProjectCostAnalysisService service;
    private final ProjectCostAnalysisExcelService excel;
    public CommonProjectCostAnalysisController(CommonProjectCostAnalysisService service, ProjectCostAnalysisExcelService excel) {
        this.service = service;
        this.excel = excel;
    }
    @GetMapping
    public Response<CommonProjectCostAnalysisResult> getCommonProjectCostAnalyses(@ModelAttribute Filter filter) {
        return Response.of(service.getCommonProjectCostAnalyses(filter));
    }
    @GetMapping(value = "/excel", produces = {XLSX, "application/json"})
    public ResponseEntity<byte[]> getCommonProjectCostAnalysesExcel(@ModelAttribute Filter filter) {
        var file = excel.getCommonProjectCostAnalysesExcel(filter);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Total-Count", Integer.toString(file.count())).body(file.bytes());
    }
    @GetMapping("/{projectId}/monthly-costs")
    public Response<CommonProjectCostAnalysisMonthlyCosts> getCommonProjectCostAnalysisMonthlyCosts(
        @PathVariable String projectId, @ModelAttribute Period period) {
        return Response.of(service.getCommonProjectCostAnalysisMonthlyCosts(projectId, period));
    }
    @GetMapping("/{projectId}/workforce")
    public Response<CommonProjectCostAnalysisWorkforce> getCommonProjectCostAnalysisWorkforce(
        @PathVariable String projectId, @ModelAttribute Period period) {
        return Response.of(service.getCommonProjectCostAnalysisWorkforce(projectId, period));
    }
    @GetMapping("/{projectId}/simulation")
    public Response<CommonProjectCostAnalysisSimulation> getCommonProjectCostAnalysisSimulation(
        @PathVariable String projectId, @ModelAttribute SimulationInput input) {
        return Response.of(service.getCommonProjectCostAnalysisSimulation(projectId, input));
    }
    @GetMapping("/{projectId}/causes")
    public Response<CommonProjectCostAnalysisCauses> getCommonProjectCostAnalysisCauses(
        @PathVariable String projectId, @ModelAttribute Period period) {
        return Response.of(service.getCommonProjectCostAnalysisCauses(projectId, period));
    }
    @ExceptionHandler(org.springframework.validation.BindException.class)
    public ResponseEntity<ApiExceptionHandler.Error> invalidQuery(org.springframework.validation.BindException failure) {
        var errors = failure.getFieldErrors().stream().map(e ->
            new ApiExceptionHandler.FieldError(e.getField(), "입력 값의 형식을 확인해 주세요.")).toList();
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(
            new ApiExceptionHandler.Error(false, Map.of(), "입력 값의 형식을 확인해 주세요.", errors));
    }
}
