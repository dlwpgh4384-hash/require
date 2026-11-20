package com.company.common.partsettlements.controller;

import com.company.common.partsettlements.dto.PartSettlementContracts.*;
import com.company.common.partsettlements.service.*;
import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value="/api/common/part-settlements",produces="application/json")
public class CommonPartSettlementController {
    private final CommonPartSettlementService service;
    private final PartSettlementCloseService close;
    private final PartSettlementExcelService excel;
    public CommonPartSettlementController(CommonPartSettlementService service,PartSettlementCloseService close,PartSettlementExcelService excel) {
        this.service=service;this.close=close;this.excel=excel;
    }
    @GetMapping
    public Response<Tabs> getCommonPartSettlements(@RequestParam String yearMonth) { return service.getCommonPartSettlements(yearMonth); }
    @GetMapping("/{partId}/{yearMonth}")
    public Response<Settlement> getCommonPartSettlement(@PathVariable String partId,@PathVariable String yearMonth) {
        return service.detailResponse(partId,yearMonth);
    }
    @GetMapping("/{partId}/{yearMonth}/trends")
    public Response<Trends> getCommonPartSettlementTrends(@PathVariable String partId,@PathVariable String yearMonth) {
        return Response.of(service.getCommonPartSettlementTrends(partId,yearMonth));
    }
    @PutMapping("/{partId}/{yearMonth}/card")
    public Response<Settlement> putCommonPartSettlementCard(@PathVariable String partId,@PathVariable String yearMonth,@RequestBody JsonNode body) {
        return Response.of(service.putCommonPartSettlementCard(partId,yearMonth,PartSettlementInput.card(body)));
    }
    @PutMapping("/{partId}/{yearMonth}/confirm")
    public Response<Settlement> putCommonPartSettlementConfirm(@PathVariable String partId,@PathVariable String yearMonth,@RequestBody JsonNode body) {
        PartSettlementInput.fields(body,"modifiedAt");
        return Response.of(service.putCommonPartSettlementConfirm(partId,yearMonth,PartSettlementInput.version(body)));
    }
    @PutMapping("/{partId}/{yearMonth}/cancel")
    public Response<Settlement> putCommonPartSettlementCancel(@PathVariable String partId,@PathVariable String yearMonth,@RequestBody JsonNode body) {
        return Response.of(service.putCommonPartSettlementCancel(partId,yearMonth,PartSettlementInput.cancel(body)));
    }
    @PutMapping("/close-confirm")
    public Response<CloseResult> putCommonPartSettlementCloseConfirm(@RequestBody JsonNode body) {
        return Response.of(close.putCommonPartSettlementCloseConfirm(PartSettlementInput.close(body)));
    }
    @GetMapping(value="/excel",produces={"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/json"})
    public ResponseEntity<byte[]> getCommonPartSettlementsExcel(@RequestParam String partId,@RequestParam String yearMonth,@RequestParam String view) {
        var file=excel.getCommonPartSettlementsExcel(partId,yearMonth,view);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.fileName(),StandardCharsets.UTF_8).build().toString())
            .body(file.bytes());
    }
}
