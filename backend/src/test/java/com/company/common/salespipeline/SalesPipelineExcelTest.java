package com.company.common.salespipeline;

import com.company.common.salespipeline.dto.SalesPipelineContracts.*;
import com.company.common.salespipeline.mapper.SalesPipelineMapper;
import com.company.common.salespipeline.service.*;
import com.company.common.support.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesPipelineExcelTest {
    Forecast forecast() {
        return new Forecast(java.util.stream.IntStream.range(0, 6).mapToObj(i -> new ForecastMonth(
            YearMonth.of(2026, 12).plusMonths(i).toString(), new BigDecimal("30000000"),
            new BigDecimal("15000000"), new BigDecimal("80000000"), new BigDecimal("3.4"),
            new BigDecimal("1.7"))).toList(), List.of());
    }
    @Test void writesRealWorkbookWithMetadataSixMonthRowsAndLogsAfterGeneration() throws Exception {
        var service = mock(SalesPipelineForecastService.class);
        var mapper = mock(SalesPipelineMapper.class);
        when(service.getCommonSalesPipelineForecast()).thenReturn(forecast());
        var time = new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC));
        var export = new SalesPipelineExcelService(service, mapper, new ExcelExportService(), new CurrentBusinessActor(), time);
        var file = export.getCommonSalesPipelineForecastExcel();
        assertThat(file.count()).isEqualTo(6);
        assertThat(file.fileName()).contains("매출·인력 예측_2026-12~2027-05_").endsWith(".xlsx");
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(file.bytes()))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("admin", "2026-11-15 09:00");
            assertThat(sheet.getLastRowNum()).isEqualTo(8);
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("2026-12");
            assertThat(sheet.getRow(8).getCell(0).getStringCellValue()).isEqualTo("2027-05");
            assertThat(sheet.getRow(3).getCell(1).getNumericCellValue()).isEqualTo(30000000);
            assertThat(sheet.getRow(3).getCell(3).getNumericCellValue()).isEqualTo(80000000);
        }
        verify(service, times(1)).getCommonSalesPipelineForecast();
        verify(mapper).insertAccess("admin", 6);
    }
    @Test void serializationFailureDoesNotWriteDownloadLog() {
        var service = mock(SalesPipelineForecastService.class); var mapper = mock(SalesPipelineMapper.class);
        var writer = mock(ExcelExportService.class);
        when(service.getCommonSalesPipelineForecast()).thenReturn(forecast());
        when(writer.write(anyString(), anyList(), anyList(), anyString(), any())).thenThrow(new IllegalStateException("serialization failed"));
        var export = new SalesPipelineExcelService(service, mapper, writer, new CurrentBusinessActor(), new BusinessCalendar());
        assertThatThrownBy(export::getCommonSalesPipelineForecastExcel).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(mapper);
    }
    @Test void readFailureDoesNotGenerateOrLogWorkbook() {
        var service = mock(SalesPipelineForecastService.class); var mapper = mock(SalesPipelineMapper.class);
        var writer = mock(ExcelExportService.class);
        when(service.getCommonSalesPipelineForecast()).thenThrow(CodeFailure.invalid("calcId", "계산 정의를 확인해 주세요."));
        var export = new SalesPipelineExcelService(service, mapper, writer, new CurrentBusinessActor(), new BusinessCalendar());
        assertThatThrownBy(export::getCommonSalesPipelineForecastExcel).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper, writer);
    }
}
