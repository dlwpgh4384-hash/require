package com.company.common.financeitems.controller;

import com.company.common.financeitems.dto.FinanceItemContracts.*;
import com.company.common.financeitems.service.*;
import com.company.common.support.service.CodeFailure;
import com.company.common.uploads.service.CommonUploadService;
import com.company.common.users.dto.UserContracts.Response;
import com.company.common.clients.service.ClientRules;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(value="/api/common/finance-items",produces="application/json")
public class CommonFinanceItemController {
    private final CommonFinanceItemService service;
    private final FinanceTaxService taxes;
    private final FinanceItemExcelService excel;
    private final FinanceItemUploadService uploads;
    private final CommonUploadService commonUploads;
    private final ObjectMapper json;
    public CommonFinanceItemController(CommonFinanceItemService service,FinanceTaxService taxes,
        FinanceItemExcelService excel,FinanceItemUploadService uploads,CommonUploadService commonUploads,ObjectMapper json) {
        this.service=service; this.taxes=taxes; this.excel=excel; this.uploads=uploads; this.commonUploads=commonUploads; this.json=json;
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
    @GetMapping(value="/{yearMonth}/excel",produces={"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/json"})
    public ResponseEntity<byte[]> getCommonFinanceItemsExcel(@PathVariable String yearMonth,@RequestParam List<String> columns) {
        var file=excel.getCommonFinanceItemsExcel(yearMonth,columns);
        return file(file.bytes(),file.fileName());
    }
    @GetMapping(value="/upload-template",produces={"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/json"})
    public ResponseEntity<byte[]> getCommonFinanceItemsTemplate() {
        return file(commonUploads.getCommonUploadTemplate("B2"),commonUploads.templateFileName("B2"));
    }
    @PostMapping(value="/{yearMonth}/uploads",consumes="multipart/form-data")
    public Response<UploadResult> postCommonFinanceItemsUpload(@PathVariable String yearMonth,@RequestPart MultipartFile file,
        @RequestParam String modifiedAt) throws java.io.IOException {
        Instant version;
        try { version=modifiedAt.isBlank() || "null".equals(modifiedAt) ? null : Instant.parse(modifiedAt); }
        catch (RuntimeException failure) { throw CodeFailure.invalid("modifiedAt","수정 시각을 확인해 주세요."); }
        return Response.of(uploads.postCommonFinanceItemsUpload(yearMonth,version,file.getOriginalFilename(),file.getBytes()));
    }
    private <T> T decode(JsonNode body,Class<T> type) {
        try { return json.treeToValue(body,type); }
        catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw CodeFailure.invalid("body","입력 형식을 확인해 주세요."); }
    }
    private ResponseEntity<byte[]> file(byte[] bytes,String name) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name,StandardCharsets.UTF_8).build().toString()).body(bytes);
    }
}
