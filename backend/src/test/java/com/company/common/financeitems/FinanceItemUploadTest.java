package com.company.common.financeitems;

import com.company.common.financeitems.dto.FinanceItemContracts.*;
import com.company.common.financeitems.mapper.FinanceItemMapper;
import com.company.common.financeitems.service.*;
import com.company.common.support.service.*;
import com.company.common.uploads.dto.UploadContracts.Inspection;
import com.company.common.uploads.service.UploadWorkbookService;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinanceItemUploadTest {
    FinanceItemMapper mapper;
    CommonFinanceItemService months;
    FinanceItemUploadForm form;
    final UploadWorkbookService workbook=new UploadWorkbookService(new ExcelExportService());
    final YearMonth month=YearMonth.of(2026,2);
    @BeforeEach void setup() {
        mapper=mock(FinanceItemMapper.class); months=mock(CommonFinanceItemService.class);
        form=new FinanceItemUploadForm(mapper,months,new CurrentBusinessActor());
        when(mapper.code("FIN_CLS","판관비")).thenReturn("SGA");
        when(mapper.code("SGA_ITEM","임대료")).thenReturn("RENT");
        when(mapper.nextItem(anyString())).thenReturn(3L);
        when(mapper.insertItem(any())).thenReturn(1);
    }
    List<String> row() { return List.of("판관비","임대료","사무실","100","","2026-02-28","Y",""); }
    List<String> row(int column,String value) { var result=new ArrayList<>(row()); result.set(column,value); return result; }
    byte[] bytes(List<List<String>> rows) { return workbook.tabularFile(form.headers(),rows,null); }
    Inspection inspect(byte[] bytes) { return form.inspect(workbook.readTabular("finance.xlsx",bytes,form.headers()),month); }
    @Test void originalEightHeadersAndBlankVatIsZeroWithoutWrites() {
        assertThat(form.headers()).containsExactly("구분","판관비 항목","세부 항목","금액","부가세","입출금일","매달 반복","비고");
        var report=inspect(bytes(List.of(row()))); assertThat(report.errorCount()).isZero(); assertThat(((Item)report.records().get(0)).vatAmount()).isZero(); verify(mapper,never()).insertItem(any());
    }
    @Test void missingAmountHasOriginalRowAndAmountColumn() {
        var report=inspect(bytes(List.of(row(3,"")))); assertThat(report.errorCount()).isEqualTo(1);
        assertThat(report.issues()).singleElement().satisfies(issue->{ assertThat(issue.row()).isEqualTo(2); assertThat(issue.column()).isEqualTo("금액"); });
    }
    @Test void missingNameIsSeparateColumnError() { assertThat(inspect(bytes(List.of(row(2,"")))).issues().get(0).column()).isEqualTo("세부 항목"); }
    @Test void explicitZeroIsAccepted() { assertThat(((Item)inspect(bytes(List.of(row(3,"0")))).records().get(0)).amount()).isZero(); }
    @Test void sameClassificationAndNameInOneFileIsAcceptedLikeDirectInput() { assertThat(inspect(bytes(List.of(row(),row()))).errorCount()).isZero(); }
    @Test void cashDateAcceptsSharedUploadDateForms() {
        for (String text : List.of("2026-02-28","2026.02.28","2026/2/28","2026. 2. 28.")) assertThat(((Item)inspect(bytes(List.of(row(5,text)))).records().get(0)).cashDate()).isEqualTo(LocalDate.of(2026,2,28));
        assertThat(inspect(bytes(List.of(row(5,"28/02/2026")))).issues().get(0).reason()).isEqualTo("날짜는 2024-03-02처럼 적어 주세요.");
    }
    @Test void requiredNameMessageNamesTheColumn() { assertThat(inspect(bytes(List.of(row(2,"")))).issues().get(0).reason()).isEqualTo("세부 항목을 입력해 주세요."); }
    @Test void unknownClassificationIsRejected() { assertThat(inspect(bytes(List.of(row(0,"알수없음")))).issues().get(0).column()).isEqualTo("구분"); }
    @Test void malformedFileCannotReachApply() { assertThatThrownBy(()->inspect(new byte[]{'P','K',3,4})).isInstanceOf(CodeFailure.class); verify(mapper,never()).deleteUploaded(anyString()); }
    @Test void renamedTextCannotReachApply() { assertThatThrownBy(()->inspect("text".getBytes(java.nio.charset.StandardCharsets.UTF_8))).isInstanceOf(CodeFailure.class); }
    @Test void formulaCellIsReadAsItsSavedResult() throws Exception {
        // 공통 규칙: 수식 칸은 엑셀이 저장해 둔 결과값으로 읽는다(개발자 풀·투입 배정 업로드와 같음).
        byte[] bytes;
        try (var book=new XSSFWorkbook(new ByteArrayInputStream(bytes(List.of(row())))); var out=new ByteArrayOutputStream()) {
            var cell=book.getSheetAt(0).getRow(1).getCell(3); cell.setCellFormula("60+40");
            book.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(cell); book.write(out); bytes=out.toByteArray();
        }
        var report=inspect(bytes);
        assertThat(report.errorCount()).isZero();
        assertThat(((Item)report.records().get(0)).amount()).isEqualByComparingTo("100");
    }
    @Test void externalRelationshipIsRejected() throws Exception {
        byte[] bytes;
        try (var book=new XSSFWorkbook(new ByteArrayInputStream(bytes(List.of(row())))); var out=new ByteArrayOutputStream()) {
            var link=book.getCreationHelper().createHyperlink(org.apache.poi.common.usermodel.HyperlinkType.URL); link.setAddress("https://example.invalid/"); book.getSheetAt(0).getRow(1).getCell(2).setHyperlink(link); book.write(out); bytes=out.toByteArray();
        }
        assertThatThrownBy(()->inspect(bytes)).isInstanceOf(CodeFailure.class);
    }
    @Test void applyReplacesOnlyUploadedRowsAndSetsNewSource() {
        form.apply(inspect(bytes(List.of(row()))),month,17L); verify(mapper).deleteUploaded("202602");
        verify(mapper).insertItem(argThat(w->w.month().equals("202602") && w.row().uploadId().equals("17") && w.row().itemId().equals("3")));
        verify(months).marker("202602"); verify(months).reallocate(2026); verify(mapper,never()).deleteLoan(any()); verify(mapper,never()).updateCard(any());
    }
    @Test void closedMonthCannotDeleteUploadedRows() {
        when(mapper.isClosed("202602")).thenReturn(true);
        assertThatThrownBy(()->form.apply(inspect(bytes(List.of(row()))),month,17L)).isInstanceOf(CodeFailure.class); verify(mapper,never()).deleteUploaded(anyString());
    }
}
