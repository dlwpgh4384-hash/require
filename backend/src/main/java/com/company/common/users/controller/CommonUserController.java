package com.company.common.users.controller;

import com.company.common.users.service.CommonUserService;
import com.company.common.users.dto.UserRequests;

import com.company.common.support.service.ExcelExportService;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.company.common.users.dto.UserContracts.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonUserController {
    /** 로그인(UI-22) 전까지 서버 사용자는 admin으로 고정한다. */
    static final String CURRENT_USER = "admin";
    private static final Map<String, String> STATE_NAMES = Map.of("USE", "사용", "LOCK", "잠김", "STOP", "중지");
    /** 엑셀 열: 화면 열 key → (머리글, 값). 화면에서 보이는 열 순서대로 받는다. */
    private static final Map<String, Map.Entry<String, Function<ListItem, String>>> EXCEL_COLUMNS = new LinkedHashMap<>();
    static {
        EXCEL_COLUMNS.put("userId", Map.entry("아이디", ListItem::userId));
        EXCEL_COLUMNS.put("name", Map.entry("이름", ListItem::name));
        EXCEL_COLUMNS.put("role", Map.entry("역할", item -> String.join(", ", item.roleNames())));
        EXCEL_COLUMNS.put("part", Map.entry("담당 파트",
            item -> item.assignedParts().isEmpty() ? "–" : String.join(", ", item.assignedParts())));
        EXCEL_COLUMNS.put("state", Map.entry("상태", item -> STATE_NAMES.getOrDefault(item.userStatCd(), item.userStatCd())));
        EXCEL_COLUMNS.put("last", Map.entry("마지막 접속", item -> item.lastAccessAt() == null ? "–"
            : ExcelExportService.display(java.time.OffsetDateTime.parse(item.lastAccessAt())
                .toInstant())));
    }
    private final CommonUserService service;
    private final ExcelExportService excel;
    public CommonUserController(CommonUserService service, ExcelExportService excel) {
        this.service = service; this.excel = excel;
    }

    @GetMapping("/api/common/users")
    public Response<Page<ListItem>> getCommonUsers(
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(required = false) String roleCd, @RequestParam(required = false) String userStatCd,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "false") boolean includeStopped,
        @RequestParam(required = false) String sort, @RequestParam(required = false) String dir) {
        return Response.of(service.getCommonUsers(
            UserRequests.filter(page, pageSize, roleCd, userStatCd, keyword, includeStopped, sort, dir)));
    }
    /** SFR-011 엑셀 다운로드: 지금 조건의 전체 결과, 보이는 열(columns) 그대로. */
    @GetMapping(value = "/api/common/users/excel",
        produces = {"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/json"})
    public ResponseEntity<byte[]> getCommonUsersExcel(
        @RequestParam(required = false) String roleCd, @RequestParam(required = false) String userStatCd,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "false") boolean includeStopped,
        @RequestParam(required = false) String sort, @RequestParam(required = false) String dir,
        @RequestParam(required = false) List<String> columns) {
        var keys = columns == null || columns.isEmpty() ? List.copyOf(EXCEL_COLUMNS.keySet()) : columns;
        for (String key : keys) if (!EXCEL_COLUMNS.containsKey(key))
            throw com.company.common.users.service.UserFailure.invalid("columns", "내려받을 열을 확인해 주세요.");
        var filter = UserRequests.filter(1, 20, roleCd, userStatCd, keyword, includeStopped, sort, dir);
        var items = service.getCommonUsersForDownload(filter, CURRENT_USER);
        var rows = new ArrayList<List<String>>();
        for (var item : items) rows.add(keys.stream().map(key -> EXCEL_COLUMNS.get(key).getValue().apply(item)).toList());
        var now = Instant.now();
        byte[] body = excel.write("사용자", keys.stream().map(key -> EXCEL_COLUMNS.get(key).getKey()).toList(),
            rows, CURRENT_USER, now);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(excel.fileName("사용자", null, now), StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(body);
    }
    @GetMapping("/api/common/users/{userId}/change-history")
    public Response<List<ChangeHistory>> getCommonUserChangeHistory(@PathVariable String userId) {
        return Response.of(service.getCommonUserChangeHistory(userId));
    }
    @GetMapping("/api/common/users/change-impact")
    public Response<Impact> getCommonUserChangeImpact(@RequestParam List<String> userIds,
        @RequestParam String action, @RequestParam(required = false) List<String> roleCds) {
        return Response.of(service.getCommonUserChangeImpact(userIds, action, roleCds));
    }
    @GetMapping("/api/common/users/{userId}")
    public Response<Account> getCommonUser(@PathVariable String userId) {
        return Response.of(service.getCommonUser(userId));
    }
    @GetMapping("/api/common/users/{userId}/access-logs")
    public Response<Page<AccessLog>> getCommonUserAccessLogs(@PathVariable String userId,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return Response.of(service.getCommonUserAccessLogs(userId, UserRequests.filter(page, pageSize, null, null, null)));
    }
    @PutMapping(value = "/api/common/users", consumes = "application/json")
    public Response<Save> putCommonUsers(@RequestBody JsonNode body) {
        return Response.of(service.putCommonUsers(UserRequests.create(body)));
    }
    @PutMapping(value = "/api/common/users/stop", consumes = "application/json")
    public Response<StopResult> putCommonUserStop(@RequestBody JsonNode body) {
        return Response.of(service.putCommonUserStop(UserRequests.stop(body)));
    }
    @PutMapping(value = "/api/common/users/{userId}", consumes = "application/json")
    public Response<Save> putCommonUser(@PathVariable String userId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonUser(userId, UserRequests.update(body)));
    }
    @PutMapping(value = "/api/common/users/{userId}/password-reset", consumes = "application/json")
    public Response<PasswordResult> putCommonUserPasswordReset(@PathVariable String userId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonUserPasswordReset(userId, UserRequests.version(body)));
    }
    @PutMapping(value = "/api/common/users/{userId}/resume", consumes = "application/json")
    public Response<Account> putCommonUserResume(@PathVariable String userId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonUserResume(userId, UserRequests.version(body)));
    }
    @PutMapping(value = "/api/common/users/{userId}/unlock", consumes = "application/json")
    public Response<UnlockResult> putCommonUserUnlock(@PathVariable String userId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonUserUnlock(userId, UserRequests.version(body)));
    }
}
