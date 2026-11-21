package com.company.common.financeitems.controller;

import com.company.common.financeitems.dto.FinanceItemContracts.*;
import com.company.common.financeitems.service.*;
import com.company.common.support.service.CodeFailure;
import com.company.common.users.dto.UserContracts.Response;
import com.company.common.clients.service.ClientRules;
import com.fasterxml.jackson.databind.*;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value="/api/common/finance-items",produces="application/json")
public class CommonFinanceItemController {
    private final CommonFinanceItemService service;
    private final FinanceTaxService taxes;
    private final ObjectMapper json;
    public CommonFinanceItemController(CommonFinanceItemService service,FinanceTaxService taxes,ObjectMapper json) {
        this.service=service; this.taxes=taxes; this.json=json;
    }
    @GetMapping("/{yearMonth}")
    public Response<Month> getCommonFinanceItems(@PathVariable String yearMonth) { return service.getCommonFinanceItems(yearMonth); }
    @PutMapping("/{yearMonth}")
    public Response<Month> putCommonFinanceItems(@PathVariable String yearMonth,@RequestBody JsonNode body) {
        ClientRules.token(body);
        for (String collection:List.of("items","loans","adjustments","transfers","cards","taxPayments")) {
            if (!body.has(collection) || !body.get(collection).isArray()) throw CodeFailure.invalid(collection,"목록을 보내 주세요.");
            int index=0;
            for (var row:body.get(collection)) {
                if (!row.isObject()) throw CodeFailure.invalid(collection+"["+index+"]","행 입력을 확인해 주세요.");
                try { ClientRules.token(row); }
                catch (CodeFailure failure) { throw CodeFailure.invalid(collection+"["+index+"].modifiedAt",failure.getMessage()); }
                index++;
            }
        }
        return Response.of(service.putCommonFinanceItems(yearMonth,decode(body,MonthInput.class)));
    }
    @PostMapping("/tax-years/{taxYear}")
    public Response<TaxYear> postCommonFinanceTaxYear(@PathVariable String taxYear) { return Response.of(taxes.postCommonFinanceTaxYear(taxYear)); }
    @PostMapping("/tax-payments/{paymentId}/confirm")
    public Response<TaxPayment> postCommonFinanceTaxPaymentConfirm(@PathVariable String paymentId,@RequestBody JsonNode body) {
        ClientRules.token(body);
        return Response.of(taxes.postCommonFinanceTaxPaymentConfirm(paymentId,decode(body,ConfirmInput.class)));
    }
    @GetMapping("/{yearMonth}/previous-items")
    public Response<PreviousItems> getCommonFinancePreviousItems(@PathVariable String yearMonth,@RequestParam(required=false) List<String> existingKeys) {
        return Response.of(service.getCommonFinancePreviousItems(yearMonth,existingKeys));
    }
    private <T> T decode(JsonNode body,Class<T> type) {
        try { return json.treeToValue(body,type); }
        catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw CodeFailure.invalid("body","입력 형식을 확인해 주세요."); }
    }
}
