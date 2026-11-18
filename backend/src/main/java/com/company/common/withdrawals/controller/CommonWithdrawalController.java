package com.company.common.withdrawals.controller;

import com.company.common.support.service.ExcelExportService;
import com.company.common.users.dto.UserContracts.Response;
import com.company.common.withdrawals.dto.WithdrawalContracts.*;
import com.company.common.withdrawals.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonWithdrawalController {
    private final CommonWithdrawalService service;
    private final WithdrawalRecommendationService recommendations;
    private final WithdrawalTentativeService tentative;
    private final ExcelExportService excel;

    public CommonWithdrawalController(CommonWithdrawalService service,
        WithdrawalRecommendationService recommendations, WithdrawalTentativeService tentative, ExcelExportService excel) {
        this.service = service;
        this.recommendations = recommendations;
        this.tentative = tentative;
        this.excel = excel;
    }

    @GetMapping("/api/common/withdrawals")
    public Response<WithdrawalList> getCommonWithdrawals(@ModelAttribute Filter filter) {
        return Response.of(service.getCommonWithdrawals(filter));
    }

    @GetMapping("/api/common/withdrawals/tentative-assignments")
    public Response<SharedList> getCommonWithdrawalAssignments(@RequestParam(required = false) String developerId,
        @RequestParam(required = false) String opportunityId) {
        return Response.of(service.getCommonWithdrawalAssignments(developerId, opportunityId));
    }

    @GetMapping("/api/common/withdrawals/{assignmentId}/recommendations")
    public Response<Recommendations> getCommonWithdrawalRecommendations(@PathVariable String assignmentId,
        @RequestParam(defaultValue = "false") boolean replaceCurrent) {
        return Response.of(recommendations.recommendations(recommendations.required(assignmentId), replaceCurrent));
    }

    @GetMapping("/api/common/withdrawals/{assignmentId}/tentative-assignment")
    public Response<TentativeResult> getCommonWithdrawalTentativeAssignment(@PathVariable String assignmentId) {
        return Response.of(tentative.current(assignmentId));
    }

    @PutMapping("/api/common/withdrawals/{assignmentId}/tentative-assignment")
    public Response<TentativeResult> putCommonWithdrawalTentativeAssignment(@PathVariable String assignmentId,
        @RequestBody JsonNode body) {
        return Response.of(tentative.save(assignmentId, body));
    }

    @PutMapping("/api/common/withdrawals/{assignmentId}/extension-possibility")
    public Response<ExtensionResult> putCommonWithdrawalExtensionPossibility(@PathVariable String assignmentId,
        @RequestBody JsonNode body) {
        return Response.of(service.putCommonWithdrawalExtensionPossibility(assignmentId, body));
    }

    @GetMapping(value = "/api/common/withdrawals/excel", produces = {"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/json"})
    public ResponseEntity<byte[]> getCommonWithdrawalsExcel(@ModelAttribute Filter filter) {
        var items = service.getCommonWithdrawals(filter).items();
        var rows = items.stream().map(r -> List.of(r.name(), r.projectName(), r.jobName(), r.endDate().toString(),
            r.withdrawalCompleted() ? "철수 완료" : "D-" + r.remainingDays(),
            "연장 " + r.extensionCount() + "회" + (r.earlyWithdrawalHistory().isEmpty() ? "" : " / " + String.join(" / ", r.earlyWithdrawalHistory())),
            r.extensionPossibilityName(), nextLabel(r))).toList();
        var now = service.now();
        byte[] bytes = excel.write("철수 리스트", List.of("이름", "프로젝트", "직무", "철수일", "남은 일수", "연장·조기 철수", "연장 가능성", "다음 배정"),
            rows, service.userId(), now);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(excel.fileName("철수 리스트", filter.year() + "년", now), StandardCharsets.UTF_8).build().toString())
            .header("X-Total-Count", Integer.toString(items.size())).body(bytes);
    }

    private static String nextLabel(Row row) {
        if (row.withdrawalCompleted()) return "–";
        var next = row.nextAssignment();
        if (next.hasTentativeAssignment()) return "가배정 · " + next.opportunityName();
        return next.recommendationCount() > 0 ? "추천 " + next.recommendationCount() + "곳" : "없음 · 맞는 자리 없음";
    }
}
