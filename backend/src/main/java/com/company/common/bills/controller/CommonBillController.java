package com.company.common.bills.controller;

import com.company.common.bills.dto.BillContracts.*;
import com.company.common.bills.service.BillDunningService;
import com.company.common.bills.service.CommonBillService;
import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces="application/json")
public class CommonBillController {
    private final CommonBillService service;
    private final BillDunningService dunning;
    public CommonBillController(CommonBillService service, BillDunningService dunning) {
        this.service=service;
        this.dunning=dunning;
    }
    @GetMapping("/api/common/bills")
    public Response<BillList> getCommonBills(@ModelAttribute Filter filter) {
        return Response.of(service.getCommonBills(filter));
    }
    private static final String XLSX="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** SFR-011 엑셀 다운로드: 목록과 같은 조건의 전체 결과. 오류는 목록과 같은 JSON(400). */
    @GetMapping(value="/api/common/bills/excel",produces={XLSX,"application/json"})
    public ResponseEntity<byte[]> getCommonBillsExcel(@ModelAttribute Filter filter) {
        var file=service.getCommonBillsExcel(filter);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment()
                .filename(file.fileName(),StandardCharsets.UTF_8).build().toString())
            .header("X-Total-Count",Integer.toString(file.count())).body(file.bytes());
    }
    /** 메뉴 옆 미수 건수 배지(SFR-001-04). */
    @GetMapping("/api/common/bills/unpaid-count")
    public Response<UnpaidCount> getCommonBillUnpaidCount() {
        return Response.of(service.getCommonBillUnpaidCount());
    }
    @GetMapping("/api/common/bills/{billId}")
    public Response<Detail> getCommonBill(@PathVariable String billId) {
        return Response.of(service.getCommonBill(billId));
    }
    @PostMapping("/api/common/bills")
    public SaveResponse postCommonBill(@RequestBody JsonNode body) {
        return service.postCommonBill(body);
    }
    @PutMapping("/api/common/bills/{billId}")
    public Response<Detail> putCommonBill(@PathVariable String billId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonBill(billId,body));
    }
    @PostMapping("/api/common/bills/{billId}/cancel")
    public Response<Detail> postCommonBillCancel(@PathVariable String billId, @RequestBody JsonNode body) {
        return Response.of(service.postCommonBillCancel(billId,body));
    }
    @PostMapping("/api/common/bills/{billId}/next-month")
    public Response<Detail> postCommonBillNextMonth(@PathVariable String billId) {
        return Response.of(service.postCommonBillNextMonth(billId));
    }
    @PostMapping("/api/common/bills/{billId}/dunning-resend")
    public Response<SendResult> postCommonBillDunningResend(@PathVariable String billId) {
        return Response.of(dunning.postCommonBillDunningResend(billId));
    }
}
