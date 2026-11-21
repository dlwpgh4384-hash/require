package com.company.common.profitstructures;

import com.company.common.profitstructures.dto.ProfitStructureContracts.*;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.profitstructures.service.*;
import com.company.common.support.service.*;
import com.company.common.users.dto.UserContracts.Response;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfitStructureExcelTest {
    @Test void numericLookingIdentifiersAndFormulaLookingNamesRemainTextWhileAmountsAreNumeric() throws Exception {
        var service=mock(CommonProfitStructureService.class);var mapper=mock(ProfitStructureMapper.class);
        var result=mock(Result.class);var row=mock(PeriodRow.class);
        when(result.compositionVisible()).thenReturn(true);when(result.periodStart()).thenReturn("202602");when(result.periodEnd()).thenReturn("202602");
        when(result.numericRows()).thenReturn(List.of(row));when(row.period()).thenReturn("202602");
        when(row.summary()).thenReturn(new Summary(new java.math.BigDecimal("1000"),null,null,null,null,null));
        when(row.composition()).thenReturn(new Composition(List.of(),List.of(
            new CompositionRow("123",new java.math.BigDecimal("600"),new java.math.BigDecimal("60"),null),
            new CompositionRow("=HYPERLINK(\"https://invalid.example\")",new java.math.BigDecimal("400"),new java.math.BigDecimal("40"),null)),List.of(),""));
        when(service.getCommonProfitStructures(any())).thenReturn(new Response<>(true,Map.of(),result));when(mapper.insertAccess(anyString(),anyString())).thenReturn(1);
        var export=new ProfitStructureExcelService(service,mapper,new ExcelExportService(),new CurrentBusinessActor(),new BusinessCalendar());
        var file=export.getCommonProfitStructuresExcel(new Filter(2026,"month","202602"),List.of("period","revenue","topClients"));
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(file.bytes()))) {
            var sheet=book.getSheetAt(0);
            assertThat(sheet.getRow(3).getCell(0).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(sheet.getRow(3).getCell(1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.NUMERIC);
            assertThat(sheet.getRow(3).getCell(2).getStringCellValue()).isEqualTo("123");
            assertThat(sheet.getRow(4).getCell(2).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(sheet.getRow(4).getCell(2).getStringCellValue()).startsWith("=HYPERLINK");
        }
    }
    @Test void serializationFailureNeverInsertsDownloadLog() {
        var service=mock(CommonProfitStructureService.class);var mapper=mock(ProfitStructureMapper.class);var excel=mock(ExcelExportService.class);
        var calendar=mock(BusinessCalendar.class);when(calendar.now()).thenReturn(Instant.parse("2026-02-01T00:00:00Z"));
        var result=mock(Result.class);when(result.numericRows()).thenReturn(List.of());
        when(service.getCommonProfitStructures(any())).thenReturn(new Response<>(true,Map.of(),result));
        when(excel.write(anyString(),anyList(),anyList(),anyString(),any())).thenThrow(new IllegalStateException("serialization failed"));
        var export=new ProfitStructureExcelService(service,mapper,excel,new CurrentBusinessActor(),calendar);
        assertThatThrownBy(()->export.getCommonProfitStructuresExcel(new Filter(2026,"month","202602"),List.of("period"))).isInstanceOf(IllegalStateException.class).hasMessage("serialization failed");
        verifyNoInteractions(mapper);
    }
    @Test void invalidColumnsNeverReadSourcesOrCreateWorkbook() {
        var service=mock(CommonProfitStructureService.class);var mapper=mock(ProfitStructureMapper.class);var excel=mock(ExcelExportService.class);
        var export=new ProfitStructureExcelService(service,mapper,excel,new CurrentBusinessActor(),new BusinessCalendar());
        for(var columns:List.of(List.of("=HYPERLINK(1)"),List.of("revenue","revenue"),List.<String>of()))
            assertThatThrownBy(()->export.getCommonProfitStructuresExcel(new Filter(2026,"month","202602"),columns)).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(service,mapper,excel);
    }
}
