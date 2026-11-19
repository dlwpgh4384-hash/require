package com.company.common.projectboard.controller;

import com.company.common.projectboard.dto.CommonProjectBoardContracts.*;
import com.company.common.projectboard.service.CommonProjectBoardService;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.support.service.ExcelExportService;
import com.company.common.users.dto.UserContracts.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommonProjectBoardController {
    private final CommonProjectBoardService service;
    private final ExcelExportService excel;
    private final CurrentBusinessActor actor;
    private final BusinessCalendar calendar;

    public CommonProjectBoardController(CommonProjectBoardService service, ExcelExportService excel,
            CurrentBusinessActor actor, BusinessCalendar calendar) {
        this.service = service;
        this.excel = excel;
        this.actor = actor;
        this.calendar = calendar;
    }

    @GetMapping(value = "/api/common/project-board", produces = "application/json")
    public Response<Result> getCommonProjectBoard(@ModelAttribute Filter filter) {
        return Response.of(service.getCommonProjectBoard(filter));
    }

    @GetMapping(value = "/api/common/project-board/excel", produces = {
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/json"})
    public ResponseEntity<byte[]> getCommonProjectBoardExcel(@ModelAttribute Filter filter) {
        var result = service.getCommonProjectBoard(filter);
        var rows = result.items().stream().map(row -> List.of(row.signal(), row.projectId(),
            row.projectName(), row.contractTypeCode(), row.reasonSummary(), value(row.pmName()),
            value(row.currentStepName()), row.stepDelayed() ? "지연" : "",
            value(row.plannedProgress()), value(row.actualProgress()), value(row.costUsageRate()),
            value(row.costProgressGap()), value(row.openIssueCount()), value(row.overdueIssueCount()),
            value(row.latestMemoDate()), value(row.daysWithoutMemo()))).toList();
        var now = calendar.now();
        var bytes = excel.write("프로젝트 현황판", List.of("신호등", "프로젝트ID", "프로젝트명", "계약유형",
            "이유 요약", "PM", "현재 단계", "단계 지연", "계획 진척률(%)", "실적 진척률(%)",
            "원가 사용률(%)", "진척 대비 원가(%p)", "열린 이슈", "기한 지난 이슈", "마지막 업데이트",
            "업데이트 없는 일수"), rows, actor.userId(), now);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(excel.fileName("프로젝트 현황판", null, now), StandardCharsets.UTF_8).build().toString())
            .header("X-Total-Count", Integer.toString(result.items().size()))
            .body(bytes);
    }

    private static String value(Object value) { return value == null ? "–" : value.toString(); }
}
