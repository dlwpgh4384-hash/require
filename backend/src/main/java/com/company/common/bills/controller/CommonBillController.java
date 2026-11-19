package com.company.common.bills.controller;

import com.company.common.bills.dto.BillContracts.*;
import com.company.common.bills.service.BillDunningService;
import com.company.common.bills.service.CommonBillService;
import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value="/api/common/bills",produces="application/json")
public class CommonBillController {
    private final CommonBillService service;
    private final BillDunningService dunning;
    public CommonBillController(CommonBillService service, BillDunningService dunning) {
        this.service=service;
        this.dunning=dunning;
    }
    @GetMapping
    public Response<BillList> getCommonBills(@ModelAttribute Filter filter) {
        return Response.of(service.getCommonBills(filter));
    }
    @GetMapping("/{billId}")
    public Response<Detail> getCommonBill(@PathVariable String billId) {
        return Response.of(service.getCommonBill(billId));
    }
    @PostMapping
    public SaveResponse postCommonBill(@RequestBody JsonNode body) {
        return service.postCommonBill(body);
    }
    @PutMapping("/{billId}")
    public Response<Detail> putCommonBill(@PathVariable String billId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonBill(billId,body));
    }
    @PostMapping("/{billId}/cancel")
    public Response<Detail> postCommonBillCancel(@PathVariable String billId, @RequestBody JsonNode body) {
        return Response.of(service.postCommonBillCancel(billId,body));
    }
    @PostMapping("/{billId}/next-month")
    public Response<Detail> postCommonBillNextMonth(@PathVariable String billId) {
        return Response.of(service.postCommonBillNextMonth(billId));
    }
    @PostMapping("/{billId}/dunning-resend")
    public Response<SendResult> postCommonBillDunningResend(@PathVariable String billId) {
        return Response.of(dunning.postCommonBillDunningResend(billId));
    }
}
