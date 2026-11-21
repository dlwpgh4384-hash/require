package com.company.common.profitstructures;

import com.company.common.profitstructures.dto.ProfitStructureContracts.*;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.profitstructures.service.*;
import com.company.common.support.service.*;
import com.company.common.users.dto.UserContracts.Response;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.apache.poi.ss.usermodel.CellType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfitStructureExcelTest {
    private static Result result(NumericTable table) {
        var result = mock(Result.class);
        when(result.periodStart()).thenReturn("202602"); when(result.periodEnd()).thenReturn("202602");
        when(result.numericTable()).thenReturn(table);
        return result;
    }
    @Test void writesItemsByPeriodsWithTextNamesNumericAmountsAndProvisionalHeader() throws Exception {
        var service=mock(CommonProfitStructureService.class);var mapper=mock(ProfitStructureMapper.class);
        var table=new NumericTable(List.of(new NumericColumn("1월",false),new NumericColumn("2월",true)),List.of(
            new NumericLine("매출","bold","money",List.of(new BigDecimal("1000"),new BigDecimal("1200"))),
            new NumericLine("매출총이익률","plain","percent",Arrays.asList(new BigDecimal("36.7"),null)),
            new NumericLine("매출 구성 · 고객사 상위 5곳","group","money",Arrays.asList(null,null)),
            new NumericLine("123","indent","money",List.of(new BigDecimal("600"),new BigDecimal("700"))),
            new NumericLine("=HYPERLINK(\"https://invalid.example\")","indent","money",List.of(new BigDecimal("400"),new BigDecimal("500")))));
        var found=result(table);when(service.getCommonProfitStructures(any())).thenReturn(new Response<>(true,Map.of(),found));when(mapper.insertAccess(anyString(),anyString())).thenReturn(1);
        var export=new ProfitStructureExcelService(service,mapper,new ExcelExportService(),new CurrentBusinessActor(),new BusinessCalendar());
        var file=export.getCommonProfitStructuresExcel(new Filter(2026,"month","202602"));
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(file.bytes()))) {
            var sheet=book.getSheetAt(0);
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("항목");
            assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("2월 잠정");
            assertThat(sheet.getRow(3).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(3).getCell(1).getCellStyle().getDataFormatString()).isEqualTo("#,##0");
            assertThat(sheet.getRow(4).getCell(1).getNumericCellValue()).isEqualTo(36.7);
            assertThat(sheet.getRow(4).getCell(1).getCellStyle().getDataFormatString()).isEqualTo("0.0");
            assertThat(sheet.getRow(4).getCell(2).getStringCellValue()).isEqualTo("–");
            assertThat(sheet.getRow(5).getCell(1).getStringCellValue()).isEmpty();
            assertThat(sheet.getRow(6).getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(6).getCell(0).getStringCellValue()).isEqualTo("123");
            assertThat(sheet.getRow(7).getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(7).getCell(0).getStringCellValue()).startsWith("=HYPERLINK");
        }
        verify(mapper).insertAccess(anyString(),contains("202602"));
    }
    @Test void serializationFailureNeverInsertsDownloadLog() {
        var service=mock(CommonProfitStructureService.class);var mapper=mock(ProfitStructureMapper.class);var excel=mock(ExcelExportService.class);
        var calendar=mock(BusinessCalendar.class);when(calendar.now()).thenReturn(Instant.parse("2026-02-01T00:00:00Z"));
        var table=new NumericTable(List.of(),List.of());
        var found=result(table);when(service.getCommonProfitStructures(any())).thenReturn(new Response<>(true,Map.of(),found));
        when(excel.write(anyString(),anyList(),anyList(),anyString(),any())).thenThrow(new IllegalStateException("serialization failed"));
        var export=new ProfitStructureExcelService(service,mapper,excel,new CurrentBusinessActor(),calendar);
        assertThatThrownBy(()->export.getCommonProfitStructuresExcel(new Filter(2026,"month","202602"))).isInstanceOf(IllegalStateException.class).hasMessage("serialization failed");
        verifyNoInteractions(mapper);
    }
}
