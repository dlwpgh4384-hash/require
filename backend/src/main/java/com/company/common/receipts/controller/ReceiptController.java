package com.company.common.receipts.controller;

import com.company.common.receipts.dto.ReceiptContracts.Result;
import com.company.common.receipts.service.ReceiptService;
import com.company.common.users.dto.UserContracts.Response;
import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value="/api/common/receipts", produces="application/json")
public class ReceiptController {
    private final ReceiptService service;
    private final ObjectMapper json;

    public ReceiptController(ReceiptService service, ObjectMapper json) {
        this.service=service;
        this.json=json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
            DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @PostMapping
    public Response<Result> postCommonReceipt(@RequestBody String body) {
        return Response.of(service.postCommonReceipt(parse(body)));
    }

    @PostMapping("/{receiptId}/cancel")
    public Response<Result> postCommonReceiptCancel(@PathVariable String receiptId, @RequestBody String body) {
        return Response.of(service.postCommonReceiptCancel(receiptId,parse(body)));
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (JsonProcessingException exception) {
            throw CodeFailure.invalid("body","입력 형식을 확인해 주세요.");
        }
    }
}
