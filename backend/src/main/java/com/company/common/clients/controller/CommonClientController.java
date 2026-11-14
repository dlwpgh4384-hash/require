package com.company.common.clients.controller;

import com.company.common.clients.dto.ClientContracts.*;
import com.company.common.users.dto.UserContracts.Response;
import com.company.common.clients.mapper.ClientRows;
import com.company.common.clients.service.ClientRules;
import com.company.common.clients.service.CommonClientService;
import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.ExcelExportService;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonClientController {
    private final CommonClientService service;
    private final ExcelExportService excel;
    private final CodeGroupMapper codes;
    public CommonClientController(CommonClientService service, ExcelExportService excel, CodeGroupMapper codes) {
        this.service = service; this.excel = excel; this.codes = codes;
    }
    /** 엑셀 열: 화면 목록 열과 같은 key·이름·값(시안). */
    private static final Map<String, Map.Entry<String, BiFunction<Item, Map<String, String>, String>>> EXCEL_COLUMNS =
        new LinkedHashMap<>();
    static {
        EXCEL_COLUMNS.put("type", Map.entry("구분", (item, names) -> names.getOrDefault("CLNT_TYPE:" + item.clientTypeCode(), item.clientTypeCode())));
        EXCEL_COLUMNS.put("name", Map.entry("상호", (item, names) -> item.clientName()));
        EXCEL_COLUMNS.put("businessNumber", Map.entry("사업자번호", (item, names) -> item.businessNumber()));
        EXCEL_COLUMNS.put("representative", Map.entry("대표자", (item, names) -> dash(item.representativeName())));
        EXCEL_COLUMNS.put("industry", Map.entry("업종", (item, names) -> item.industryCode() == null || item.industryCode().isBlank()
            ? "–" : names.getOrDefault("INDUTY:" + item.industryCode(), item.industryCode())));
        EXCEL_COLUMNS.put("contact", Map.entry("담당자", (item, names) -> dash(item.contactName())));
        EXCEL_COLUMNS.put("phone", Map.entry("연락처", (item, names) -> dash(item.contactPhone())));
        EXCEL_COLUMNS.put("taxEmail", Map.entry("세금계산서 이메일", (item, names) -> dash(item.taxEmail())));
        EXCEL_COLUMNS.put("account", Map.entry("지급 계좌", (item, names) -> dash(item.paymentAccountSummary())));
        EXCEL_COLUMNS.put("projects", Map.entry("진행 프로젝트", (item, names) -> item.projectCount() + "건"));
        EXCEL_COLUMNS.put("people", Map.entry("투입 인력", (item, names) -> item.personCount() + "명"));
        EXCEL_COLUMNS.put("use", Map.entry("사용 여부", (item, names) -> "Y".equals(item.useYn()) ? "사용" : "중지"));
    }
    private static String dash(String value) { return value == null || value.isBlank() ? "–" : value; }

    @GetMapping("/api/common/clients")
    public Response<Items<Item>> getCommonClients(
        @RequestParam(required = false) String clientTypeCode,
        @RequestParam(defaultValue = "Y") String useYn,
        @RequestParam(required = false) String keyword) {
        return Response.of(service.getCommonClients(ClientRules.filter(clientTypeCode, useYn, keyword)));
    }
    /** SFR-011 엑셀 다운로드: 지금 조건의 전체 결과, 보이는 열(columns)·정렬 그대로. 파일에 받은 사람·시각(SER-007). */
    @GetMapping(value = "/api/common/clients/excel",
        produces = {"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/json"})
    public ResponseEntity<byte[]> getCommonClientsExcel(
        @RequestParam(required = false) String clientTypeCode,
        @RequestParam(defaultValue = "Y") String useYn,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String sort, @RequestParam(required = false) String dir,
        @RequestParam(required = false) List<String> columns) {
        var keys = columns == null || columns.isEmpty() ? List.copyOf(EXCEL_COLUMNS.keySet()) : columns;
        for (String key : keys) if (!EXCEL_COLUMNS.containsKey(key))
            throw CodeFailure.invalid("columns", "내려받을 열을 확인해 주세요.");
        if (sort != null && !EXCEL_COLUMNS.containsKey(sort)) throw CodeFailure.invalid("sort", "정렬 열을 확인해 주세요.");
        var names = new HashMap<String, String>();
        for (String group : List.of("CLNT_TYPE", "INDUTY"))
            for (var code : codes.selectCodes(group)) names.put(group + ":" + code.code, code.codeName);
        var items = new ArrayList<>(service.getCommonClientsForDownload(ClientRules.filter(clientTypeCode, useYn, keyword)));
        if (sort != null) {
            var value = EXCEL_COLUMNS.get(sort).getValue();
            Comparator<Item> order = switch (sort) {
                case "projects" -> Comparator.comparingLong(Item::projectCount);
                case "people" -> Comparator.comparingLong(Item::personCount);
                default -> Comparator.comparing(item -> value.apply(item, names));
            };
            items.sort("desc".equals(dir) ? order.reversed() : order);
        }
        var rows = new ArrayList<List<String>>();
        for (var item : items) rows.add(keys.stream().map(key -> EXCEL_COLUMNS.get(key).getValue().apply(item, names)).toList());
        var now = Instant.now();
        byte[] body = excel.write("거래처", keys.stream().map(key -> EXCEL_COLUMNS.get(key).getKey()).toList(),
            rows, service.currentUserId(), now);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(excel.fileName("거래처", null, now), StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(body);
    }
    @GetMapping("/api/common/clients/{clientId}")
    public Response<Item> getCommonClient(@PathVariable String clientId) {
        return Response.of(service.getCommonClient(clientId));
    }
    @GetMapping("/api/common/clients/{clientId}/assignments")
    public Response<Items<ClientRows.Assignment>> getCommonClientAssignments(@PathVariable String clientId) {
        return Response.of(service.getCommonClientAssignments(clientId));
    }
    @GetMapping("/api/common/clients/{clientId}/change-history")
    public Response<Items<ClientRows.Change>> getCommonClientChangeHistory(@PathVariable String clientId) {
        return Response.of(service.getCommonClientChangeHistory(clientId));
    }
    @GetMapping("/api/common/clients/{clientId}/payment-account")
    public Response<Account> getCommonClientPaymentAccount(@PathVariable String clientId) {
        return Response.of(service.getCommonClientPaymentAccount(clientId));
    }
    @PostMapping(value = "/api/common/clients", consumes = "application/json")
    public Response<Save> postCommonClient(@RequestBody JsonNode body) {
        return Response.of(service.postCommonClient(ClientRules.parse(body, false)));
    }
    @PutMapping(value = "/api/common/clients/{clientId}", consumes = "application/json")
    public Response<Save> putCommonClient(@PathVariable String clientId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonClient(clientId, ClientRules.update(body)));
    }
    @PutMapping(value = "/api/common/clients/use-status", consumes = "application/json")
    public Response<StateResult> putCommonClientUseStatus(@RequestBody JsonNode body) {
        return Response.of(service.putCommonClientUseStatus(ClientRules.state(body)));
    }
}
