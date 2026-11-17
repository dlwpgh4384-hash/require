package com.company.common.assignments.controller;

import com.company.common.assignments.dto.AssignmentContracts.*;
import com.company.common.assignments.service.CommonAssignmentService;
import com.company.common.support.service.ExcelExportService;
import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces="application/json")
public class CommonAssignmentController {
    private final CommonAssignmentService service;
    private final ExcelExportService excel;
    public CommonAssignmentController(CommonAssignmentService service,ExcelExportService excel) { this.service=service; this.excel=excel; }
    @GetMapping("/api/common/assignments")
    public Response<Items<Assignment>> getCommonAssignments(@ModelAttribute Filter filter) { return Response.of(service.getCommonAssignments(filter)); }
    @GetMapping("/api/common/assignments/candidates")
    public Response<Items<Candidate>> getCommonAssignmentCandidates() { return Response.of(service.getCommonAssignmentCandidates()); }
    @GetMapping("/api/common/assignments/contract-developers")
    public Response<Items<ContractDeveloper>> getCommonAssignmentContractDevelopers(@RequestParam(required=false) String projectId,
            @RequestParam(required=false) String developerId,@RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate) {
        return Response.of(service.getCommonAssignmentContractDevelopers(projectId,developerId,startDate));
    }
    @GetMapping("/api/common/assignments/{assignmentId}")
    public Response<Assignment> getCommonAssignment(@PathVariable String assignmentId) { return Response.of(service.getCommonAssignment(assignmentId)); }
    @GetMapping("/api/common/assignments/{assignmentId}/history")
    public Response<Items<History>> getCommonAssignmentHistory(@PathVariable String assignmentId,@RequestParam(required=false) String changeTypeCode) { return Response.of(service.getCommonAssignmentHistory(assignmentId,changeTypeCode)); }
    @PostMapping("/api/common/assignments")
    public Response<Assignment> postCommonAssignment(@RequestBody JsonNode body) { return Response.of(service.postCommonAssignment(body)); }
    @PostMapping("/api/common/assignments/candidates/{candidateId}/remove")
    public Response<Removed> postCommonAssignmentCandidateRemove(@PathVariable String candidateId) { return Response.of(service.postCommonAssignmentCandidateRemove(candidateId)); }
    @PostMapping("/api/common/assignments/{assignmentId}/changes")
    public Response<Assignment> postCommonAssignmentChange(@PathVariable String assignmentId,@RequestBody JsonNode body) { return Response.of(service.postCommonAssignmentChange(assignmentId,body)); }
    @PostMapping("/api/common/assignments/{assignmentId}/history/{historyId}/corrections")
    public Response<History> postCommonAssignmentHistoryCorrection(@PathVariable String assignmentId,@PathVariable String historyId,@RequestBody JsonNode body) { return Response.of(service.postCommonAssignmentHistoryCorrection(assignmentId,historyId,body)); }
    @PostMapping("/api/common/assignments/cancel")
    public Response<Items<Assignment>> postCommonAssignmentCancel(@RequestBody JsonNode body) { return Response.of(service.postCommonAssignmentCancel(body)); }
    @PutMapping("/api/common/assignments/extension-possibility")
    public Response<Items<Assignment>> putCommonAssignmentExtensionPossibility(@RequestBody JsonNode body) { return Response.of(service.putCommonAssignmentExtensionPossibility(body)); }
    @GetMapping(value="/api/common/assignments/excel",produces={"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/json"})
    public ResponseEntity<byte[]> getCommonAssignmentsExcel(@ModelAttribute Filter filter) {
        var items=service.getCommonAssignments(filter).items();
        var rows=items.stream().map(r -> List.of(String.join(" / ",r.warnings()),r.projectName(),r.name(),r.jobName(),r.startDate().toString(),r.endDate().toString(),
            r.billingPrice()==null?"":r.billingPrice().toPlainString(),r.monthlyMm().toPlainString(),r.yearMm().toPlainString(),r.extensionPossibilityName(),r.inputTypeName())).toList();
        var now=service.now(); int year=filter.year()==null?now.atZone(java.time.ZoneId.of("Asia/Seoul")).getYear():filter.year();
        byte[] bytes=excel.write("투입 배정",List.of("경고","프로젝트","이름","직무","투입일","철수일","월 단가","월 MM","그 해 MM","연장 가능성","투입 구분"),rows,service.userId(),now);
        service.recordDownload(items.size(),year);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(excel.fileName("투입 배정",year+"년",now),java.nio.charset.StandardCharsets.UTF_8).build().toString()).body(bytes);
    }
}
