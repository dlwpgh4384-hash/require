package com.company.common.salespipeline.controller;

import com.company.common.salespipeline.dto.SalesPipelineContracts.*;
import com.company.common.salespipeline.service.CommonSalesPipelineService;
import com.company.common.salespipeline.service.SalesPipelineForecastService;
import com.company.common.salespipeline.service.SalesPipelineExcelService;
import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/common/sales-pipeline", produces = "application/json")
public class CommonSalesPipelineController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final CommonSalesPipelineService service;
    private final SalesPipelineForecastService forecast;
    private final SalesPipelineExcelService excel;

    public CommonSalesPipelineController(CommonSalesPipelineService service,
        SalesPipelineForecastService forecast, SalesPipelineExcelService excel) {
        this.service = service;
        this.forecast = forecast;
        this.excel = excel;
    }

    @GetMapping
    public Response<Pipeline> getCommonSalesPipeline() {
        return Response.of(service.getCommonSalesPipeline());
    }
    @GetMapping("/closed")
    public Response<Items<Closed>> getCommonSalesPipelineClosed(
        @RequestParam(required = false) String result,
        @RequestParam(defaultValue = "false") boolean includeStopped) {
        return Response.of(service.getCommonSalesPipelineClosed(result, includeStopped));
    }
    @GetMapping("/forecast")
    public Response<Forecast> getCommonSalesPipelineForecast(@RequestParam(required = false) String startMonth) {
        return Response.of(forecast.getCommonSalesPipelineForecast(startMonth));
    }
    @GetMapping(value = "/forecast/excel", produces = {XLSX, "application/json"})
    public ResponseEntity<byte[]> getCommonSalesPipelineForecastExcel() {
        var workbook = excel.getCommonSalesPipelineForecastExcel();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(workbook.fileName(), StandardCharsets.UTF_8).build().toString())
            .header("X-Total-Count", Integer.toString(workbook.count())).body(workbook.bytes());
    }
    @PostMapping(value = "/quote", consumes = "application/json")
    public Response<Quote> postCommonSalesPipelineQuote(@RequestBody JsonNode body) {
        return Response.of(service.postCommonSalesPipelineQuote(body));
    }
}
