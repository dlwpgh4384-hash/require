package com.company.common.candidates.controller;

import com.company.common.candidates.dto.CandidateContracts.*;
import com.company.common.candidates.service.CandidateRules;
import com.company.common.candidates.service.CommonCandidateService;
import com.company.common.clients.service.ClientRules;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.support.service.ExcelExportService;
import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonCandidateController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String TITLE = "외부 인력 후보";
    private final CommonCandidateService service;
    private final ExcelExportService excel;
    private final CurrentBusinessActor actor;
    public CommonCandidateController(CommonCandidateService service, ExcelExportService excel,
        CurrentBusinessActor actor) {
        this.service = service;
        this.excel = excel;
        this.actor = actor;
    }

    /** 목록 엑셀(SFR-011): 지금 조건의 전체 결과, 열은 화면 목록 순서(시안 UI-33). */
    @GetMapping(value = "/api/common/candidates/excel", produces = {XLSX, "application/json"})
    public ResponseEntity<byte[]> getCommonCandidatesExcel(@ModelAttribute Filter filter) {
        var items = service.getCommonCandidates(filter).items();
        var rows = items.stream().map(r -> List.of(text(r.name()),
            text(r.employeeTypeName()) + (r.companyName() == null || r.companyName().isBlank() ? "" : " · " + r.companyName()),
            text(r.primaryJobName()), text(r.gradeName()), String.join(", ", r.skills()), text(r.homeAddress()),
            amount(r.desiredMonthlyRate()), text(r.availableDate()), text(r.contactStatusName()),
            text(r.lastContactDate()), text(r.sourceName()))).toList();
        var now = service.now();
        service.recordDownload(actor.userId(), items.size());
        byte[] bytes = excel.write(TITLE, List.of("이름", "구분", "직무", "등급", "기술", "거주지", "희망 단가",
            "투입 가능일", "연락 상태", "최근 연락", "출처"), rows, actor.userId(), now);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(excel.fileName(TITLE, null, now), StandardCharsets.UTF_8).build().toString())
            .header("X-Total-Count", Integer.toString(items.size())).body(bytes);
    }
    private static String text(Object value) { return value == null ? "" : Objects.toString(value); }
    private static String amount(BigDecimal value) { return value == null ? "" : value.stripTrailingZeros().toPlainString(); }

    @GetMapping("/api/common/candidates")
    public Response<Items<Candidate>> getCommonCandidates(@ModelAttribute Filter filter) {
        return Response.of(service.getCommonCandidates(filter));
    }
    @GetMapping("/api/common/candidates/{candidateId}")
    public Response<Candidate> getCommonCandidate(@PathVariable String candidateId) {
        return Response.of(service.getCommonCandidate(candidateId));
    }
    @GetMapping("/api/common/candidates/{candidateId}/contacts")
    public Response<Items<Contact>> getCommonCandidateContacts(@PathVariable String candidateId) {
        return Response.of(service.getCommonCandidateContacts(candidateId));
    }
    @GetMapping("/api/common/candidates/{candidateId}/matches")
    public Response<Items<Match>> getCommonCandidateMatches(@PathVariable String candidateId) {
        return Response.of(service.getCommonCandidateMatches(candidateId));
    }
    @PostMapping(value = "/api/common/candidates", consumes = "application/json")
    public Response<Candidate> postCommonCandidate(@RequestBody JsonNode body) {
        return Response.of(service.postCommonCandidate(CandidateRules.input(body, false)));
    }
    @PutMapping(value = "/api/common/candidates/{candidateId}", consumes = "application/json")
    public Response<Candidate> putCommonCandidate(@PathVariable String candidateId, @RequestBody JsonNode body) {
        var input = CandidateRules.input(body, true);
        return Response.of(service.putCommonCandidate(candidateId, input, ClientRules.token(body)));
    }
    @PostMapping(value = "/api/common/candidates/{candidateId}/contacts", consumes = "application/json")
    public Response<Candidate> postCommonCandidateContact(@PathVariable String candidateId, @RequestBody JsonNode body) {
        return Response.of(service.postCommonCandidateContact(candidateId, CandidateRules.contact(body)));
    }
}
