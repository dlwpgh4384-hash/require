package com.company.common.developers.controller;

import com.company.common.clients.service.ClientRules;
import com.company.common.developers.dto.DeveloperContracts.*;
import com.company.common.developers.mapper.DeveloperRows.*;
import com.company.common.developers.service.CommonDeveloperService;
import com.company.common.developers.service.DeveloperRules;
import com.company.common.support.service.ExcelExportService;
import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces="application/json")
public class CommonDeveloperController {
    private final CommonDeveloperService service;
    private final ExcelExportService excel;
    public CommonDeveloperController(CommonDeveloperService service,ExcelExportService excel) { this.service=service; this.excel=excel; }
    @GetMapping("/api/common/developers")
    public Response<Items<ListRow>> getCommonDevelopers(@ModelAttribute Filter filter) {
        return Response.of(service.getCommonDevelopers(filter));
    }
    @GetMapping("/api/common/developers/areas")
    public Response<Items<Area>> getCommonDeveloperAreas(@RequestParam(required=false) String parentAreaCode) {
        return Response.of(service.getCommonDeveloperAreas(parentAreaCode));
    }
    @GetMapping(value="/api/common/developers/excel",produces={"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/json"})
    public ResponseEntity<byte[]> getCommonDevelopersExcel(@ModelAttribute Filter filter) {
        var rows=service.getCommonDevelopersForDownload(filter).stream().map(r -> List.of(
            value(r.name),value(r.managementNumber),value(r.organizationName),value(r.employeeTypeName),value(r.gradeName),
            value(r.primaryJobName),value(r.workStatusName)+(r.retirementScheduled?" · 퇴사 예정":""),
            value(r.currentProjectName)+(r.additionalProjectCount>0?" 외 "+r.additionalProjectCount+"건":""),
            value(r.currentMonthMm)+(r.overOneMm?" · 1.0 초과":""),value(r.withdrawalDate),value(r.monthlyRate))).toList();
        var now=service.now();
        byte[] bytes=excel.write("개발자 풀",List.of("이름","관리번호","소속","직원구분","등급","주 직무","상태","현재 프로젝트","이번 달 MM","철수 예정일","월 단가"),rows,service.currentUserId(),now);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(excel.fileName("개발자 풀",null,now),StandardCharsets.UTF_8).build().toString()).body(bytes);
    }
    private static String value(Object o) { return o==null?"":o.toString(); }
    @GetMapping("/api/common/developers/{developerId}")
    public Response<Developer> getCommonDeveloper(@PathVariable String developerId) { return Response.of(service.getCommonDeveloper(developerId)); }
    @GetMapping("/api/common/developers/{developerId}/assignments")
    public Response<Items<Assignment>> getCommonDeveloperAssignments(@PathVariable String developerId) { return Response.of(service.getCommonDeveloperAssignments(developerId)); }
    @GetMapping("/api/common/developers/{developerId}/change-history")
    public Response<Items<Change>> getCommonDeveloperChangeHistory(@PathVariable String developerId) { return Response.of(service.getCommonDeveloperChangeHistory(developerId)); }
    @GetMapping("/api/common/developers/{developerId}/statuses")
    public Response<Items<Status>> getCommonDeveloperStatuses(@PathVariable String developerId) { return Response.of(service.getCommonDeveloperStatuses(developerId)); }
    @PostMapping(value="/api/common/developers",consumes="application/json")
    public Response<Developer> postCommonDeveloper(@RequestBody JsonNode body) { return Response.of(service.postCommonDeveloper(DeveloperRules.input(body,false))); }
    @PutMapping(value="/api/common/developers/{developerId}",consumes="application/json")
    public Response<Developer> putCommonDeveloper(@PathVariable String developerId,@RequestBody JsonNode body) { return Response.of(service.putCommonDeveloper(developerId,DeveloperRules.input(body,true))); }
    @PutMapping(value="/api/common/developers/use-status",consumes="application/json")
    public Response<UseResult> putCommonDeveloperUseStatus(@RequestBody JsonNode body) { return Response.of(service.putCommonDeveloperUseStatus(DeveloperRules.use(body))); }
    @PostMapping(value="/api/common/developers/{developerId}/statuses",consumes="application/json")
    public Response<Items<Status>> postCommonDeveloperStatus(@PathVariable String developerId,@RequestBody JsonNode body) { return Response.of(service.postCommonDeveloperStatus(developerId,DeveloperRules.status(body))); }
    @PutMapping(value="/api/common/developers/{developerId}/statuses/{statusId}/end-date",consumes="application/json")
    public Response<Items<Status>> putCommonDeveloperStatusEndDate(@PathVariable String developerId,@PathVariable String statusId,@RequestBody JsonNode body) {
        ClientRules.object(body,Set.of("endDate"));
        if (!body.has("endDate")) throw DeveloperRules.invalid("endDate");
        return Response.of(service.putCommonDeveloperStatusEndDate(developerId,statusId,DeveloperRules.date(body,"endDate",false)));
    }
    @PostMapping("/api/common/developers/{developerId}/statuses/{statusId}/return-cancel")
    public Response<Items<Status>> postCommonDeveloperReturnCancel(@PathVariable String developerId,@PathVariable String statusId) {
        return Response.of(service.postCommonDeveloperReturnCancel(developerId,statusId));
    }
    @PostMapping(value="/api/common/developers/{developerId}/statuses/{statusId}/retirement-cancel",consumes="application/json")
    public Response<Items<Status>> postCommonDeveloperRetirementCancel(@PathVariable String developerId,@PathVariable String statusId,@RequestBody JsonNode body) {
        ClientRules.object(body,Set.of("reason"));
        return Response.of(service.postCommonDeveloperRetirementCancel(developerId,statusId,DeveloperRules.text(body,"reason",300,true)));
    }
}
