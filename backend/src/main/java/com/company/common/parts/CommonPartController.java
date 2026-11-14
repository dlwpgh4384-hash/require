package com.company.common.parts;

import com.company.common.dto.UserContracts.Response;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.common.service.CodeFailure;
import com.company.common.service.ExcelExportService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.company.common.parts.PartContracts.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonPartController {
    private final CommonPartService service;
    private final ObjectMapper json;
    private final ExcelExportService excel;

    public CommonPartController(CommonPartService service, ObjectMapper json, ExcelExportService excel) {
        this.service = service;
        this.json = json;
        this.excel = excel;
    }
    /** 엑셀 열: 화면 목록 열과 같은 key·이름·값(시안). */
    private static final Map<String, Map.Entry<String, Function<Part, String>>> EXCEL_COLUMNS = new LinkedHashMap<>();
    static {
        EXCEL_COLUMNS.put("partName", Map.entry("파트명", Part::partName));
        EXCEL_COLUMNS.put("leaderName", Map.entry("파트장", Part::leaderName));
        EXCEL_COLUMNS.put("distributionRate", Map.entry("이익 배분율", part -> part.distributionRate().stripTrailingZeros().toPlainString() + "%"));
        EXCEL_COLUMNS.put("salesExpense", Map.entry("공통관리비(1인·월)", part -> new DecimalFormat("#,##0").format(part.salesExpense())));
        EXCEL_COLUMNS.put("effectiveMonth", Map.entry("적용 시작", Part::effectiveMonth));
        EXCEL_COLUMNS.put("scheduledChange", Map.entry("예정된 변경", part -> part.scheduledChange() == null ? "–" : part.scheduledChange()));
        EXCEL_COLUMNS.put("personCount", Map.entry("소속 인력", part -> part.personCount() + "명"));
        EXCEL_COLUMNS.put("projectCount", Map.entry("진행 프로젝트", part -> part.projectCount() + "건"));
        EXCEL_COLUMNS.put("useYn", Map.entry("사용 여부", part -> "Y".equals(part.useYn()) ? "사용" : "중지"));
    }
    /** SFR-011 엑셀 다운로드: 보이는 열·정렬 그대로. 파일에 받은 사람·시각(SER-007). */
    @GetMapping(value = "/api/common/parts/excel",
        produces = {"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/json"})
    public ResponseEntity<byte[]> getCommonPartsExcel(
        @RequestParam(required = false) String sort, @RequestParam(required = false) String dir,
        @RequestParam(required = false) List<String> columns) {
        var keys = columns == null || columns.isEmpty() ? List.copyOf(EXCEL_COLUMNS.keySet()) : columns;
        for (String key : keys) if (!EXCEL_COLUMNS.containsKey(key))
            throw CodeFailure.invalid("columns", "내려받을 열을 확인해 주세요.");
        if (sort != null && !EXCEL_COLUMNS.containsKey(sort)) throw CodeFailure.invalid("sort", "정렬 열을 확인해 주세요.");
        var items = new ArrayList<>(service.getCommonPartsForDownload());
        if (sort != null) {
            Comparator<Part> order = switch (sort) {
                case "distributionRate" -> Comparator.comparing(Part::distributionRate);
                case "salesExpense" -> Comparator.comparing(Part::salesExpense);
                case "personCount" -> Comparator.comparingLong(Part::personCount);
                case "projectCount" -> Comparator.comparingLong(Part::projectCount);
                default -> Comparator.comparing(part -> EXCEL_COLUMNS.get(sort).getValue().apply(part));
            };
            items.sort("desc".equals(dir) ? order.reversed() : order);
        }
        var rows = new ArrayList<List<String>>();
        for (var item : items) rows.add(keys.stream().map(key -> EXCEL_COLUMNS.get(key).getValue().apply(item)).toList());
        var now = Instant.now();
        byte[] body = excel.write("영업 파트", keys.stream().map(key -> EXCEL_COLUMNS.get(key).getKey()).toList(),
            rows, service.currentUserId(), now);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(excel.fileName("영업 파트", null, now), StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(body);
    }
    @GetMapping("/api/common/parts")
    public Response<Items<Part>> getCommonParts() { return Response.of(service.getCommonParts()); }
    @GetMapping("/api/common/parts/options")
    public Response<Options> getCommonPartOptions() { return Response.of(service.getCommonPartOptions()); }
    @GetMapping("/api/common/parts/{partId}/standards")
    public Response<Items<Standard>> getCommonPartStandards(@PathVariable String partId) {
        return Response.of(service.getCommonPartStandards(partId));
    }
    @GetMapping("/api/common/parts/{partId}/change-history")
    public Response<Items<Change>> getCommonPartChangeHistory(@PathVariable String partId) {
        return Response.of(service.getCommonPartChangeHistory(partId));
    }
    @PostMapping(value = "/api/common/parts", consumes = "application/json")
    public Response<Part> postCommonPart(@RequestBody String body) {
        return Response.of(service.postCommonPart(PartRules.create(parse(body))));
    }
    @PutMapping(value = "/api/common/parts/{partId}", consumes = "application/json")
    public Response<Part> putCommonPart(@PathVariable String partId, @RequestBody String body) {
        return Response.of(service.putCommonPart(partId, PartRules.rename(parse(body))));
    }
    @PutMapping(value = "/api/common/parts/{partId}/standards", consumes = "application/json")
    public Response<Items<Standard>> putCommonPartStandards(@PathVariable String partId, @RequestBody String body) {
        return Response.of(service.putCommonPartStandards(partId, PartRules.standard(parse(body))));
    }
    @PutMapping(value = "/api/common/parts/{partId}/stop", consumes = "application/json")
    public Response<Part> putCommonPartStop(@PathVariable String partId, @RequestBody String body) {
        return Response.of(service.putCommonPartStop(partId, PartRules.version(parse(body))));
    }
    private JsonNode parse(String body) {
        try {
            return json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(body);
        } catch (JsonProcessingException failure) {
            throw PartRules.invalid("body", "입력 형식을 확인해 주세요.");
        }
    }
}
