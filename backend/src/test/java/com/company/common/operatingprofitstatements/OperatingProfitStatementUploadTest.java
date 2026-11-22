package com.company.common.operatingprofitstatements;

import com.company.common.operatingprofitstatements.mapper.OperatingProfitStatementMapper;
import com.company.common.operatingprofitstatements.mapper.OperatingProfitStatementMapper.ImportedMonth;
import com.company.common.operatingprofitstatements.service.OperatingProfitStatementUploadForm;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.service.*;
import com.company.common.uploads.dto.UploadContracts.Inspection;
import com.company.common.uploads.service.UploadWorkbookService;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperatingProfitStatementUploadTest {
    OperatingProfitStatementMapper mapper;
    OperatingProfitStatementUploadForm form;
    final UploadWorkbookService workbook=new UploadWorkbookService(new ExcelExportService());
    @BeforeEach void setup() {
        mapper=mock(OperatingProfitStatementMapper.class);
        var periods=mock(UserPreferencePeriodSourceMapper.class);
        when(periods.selectCashBaseYm()).thenReturn(new BigDecimal("202603"));
        form=new OperatingProfitStatementUploadForm(mapper,periods,new CurrentBusinessActor());
        when(mapper.upsertImported(any())).thenReturn(1);
    }
    List<String> row() { return new ArrayList<>(List.of("202501","1,000,000","600000","100000","300000","","0","","","","","","-100000","","","","")); }
    List<String> row(int column,String value) { var row=row(); row.set(column,value); return row; }
    byte[] bytes(List<List<String>> rows) { return workbook.tabularFile(form.headers(),rows,null); }
    Inspection inspect(byte[] bytes) { return form.inspect(workbook.readTabular("profit.xlsx",bytes,form.headers()),null); }
    @Test void validSingleMonthPreservesSignedRawTotalsNullAndZero() {
        var result=inspect(bytes(List.of(row())));
        assertThat(result.errorCount()).isZero(); assertThat(form.monthly()).isFalse();
        var value=(ImportedMonth)result.records().get(0);
        assertThat(value.month).isEqualTo("202501"); assertThat(value.revenue).isEqualByComparingTo("1000000");
        assertThat(value.grossProfit).isNull(); assertThat(value.allocatedCost).isNull(); assertThat(value.idleCost).isZero();
        assertThat(value.previousAdjustment).isEqualByComparingTo("-100000");
        assertThat(value.operatingProfit).isEqualByComparingTo("300000"); verifyNoInteractions(mapper);
    }
    @Test void duplicateMonthsMarkEveryConflictingRowAndPreventAllApply() {
        var result=inspect(bytes(List.of(row(),row(),row(0,"202502"))));
        assertThat(result.errorCount()).isEqualTo(2);
        assertThat(result.issues()).extracting(i->i.column()).containsExactly("연월","연월");
        assertThat(result.issues()).extracting(i->i.row()).containsExactly(2,3);
        assertThatThrownBy(()->form.apply(result,null,1L)).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"202412","202603","202613"})
    void outOfConfiguredWindowIsMonthErrorWithoutWrites(String month) {
        var result=inspect(bytes(List.of(row(0,month))));
        assertThat(result.errorCount()).isEqualTo(1); assertThat(result.issues().get(0).column()).isEqualTo("연월");
        verifyNoInteractions(mapper);
    }
    @Test void errorReasonsFollowMockupWording() {
        var noRevenue=row(1,""); var noSga=row(3,""); noSga.set(0,"202502");
        var result=inspect(bytes(List.of(row(0,"202603"),row(0,"2025.13"),noRevenue,noSga)));
        assertThat(result.issues()).extracting(i->i.reason()).containsExactly(
            "2025-01 ~ 2026-02(시작한 달 전) 달만 올릴 수 있습니다.",
            "연월 형식이 맞지 않습니다. YYYY-MM으로 적어 주세요.",
            "매출을 입력해 주세요. (필수: 연월·매출·매출원가·판관비 합계·영업이익)",
            "판관비 합계를 입력해 주세요. (필수: 연월·매출·매출원가·판관비 합계·영업이익)");
        assertThat(result.issues().get(2).inputValue()).isEqualTo("(빈칸)");
    }
    @Test void currentYearPreStartMonthIsAllowedWithoutClosingRestrictions() {
        assertThat(inspect(bytes(List.of(row(0,"202602")))).errorCount()).isZero();
    }
    @Test void missingOperatingProfitReportsItsExactColumn() {
        var result=inspect(bytes(List.of(row(4,""))));
        assertThat(result.issues().get(0).column()).isEqualTo("영업이익");
        assertThatThrownBy(()->form.apply(result,null,1L)).isInstanceOf(CodeFailure.class); verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"1.5","1000000000000000","=1+1","NaN"})
    void invalidAmountCannotBeSilentlyRounded(String amount) {
        assertThat(inspect(bytes(List.of(row(1,amount)))).issues().get(0).column()).isEqualTo("매출");
    }
    @Test void suppliedOptionalValuesAndContradictoryTotalsAreNotDerived() {
        var values=row(); for(int c=5;c<17;c++) values.set(c,String.valueOf(-c));
        var result=inspect(bytes(List.of(values))); form.apply(result,null,8L);
        verify(mapper).upsertImported(argThat(r -> r.grossProfit.intValueExact()==-8 && r.operatingProfit.intValueExact()==300000
            && r.sellingAdministrativeInput.intValueExact()==-9 && r.indirectLaborCost.intValueExact()==-10 && r.internalResearchCost.intValueExact()==-11));
    }
    @Test void applyUpsertsOnlySubmittedNaturalMonthsAndUsesExistingActor() {
        form.lock(); form.apply(inspect(bytes(List.of(row(),row(0,"202502")))),null,8L);
        verify(mapper).lockSources();
        verify(mapper).upsertImported(argThat(r->r.month.equals("202501") && r.userId.equals("admin")));
        verify(mapper).upsertImported(argThat(r->r.month.equals("202502")));
        verifyNoMoreInteractions(mapper);
    }
    @Test void malformedAndRenamedFilesAreRejectedBeforeFormWrites() {
        assertThatThrownBy(()->inspect(new byte[]{'P','K',3,4})).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(()->inspect("text".getBytes(java.nio.charset.StandardCharsets.UTF_8))).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper);
    }
    @Test void optionalValuesStillRequireAllSeventeenHeaders() {
        var wrong=workbook.tabularFile(form.headers().subList(0,16),List.of(row().subList(0,16)),null);
        assertThatThrownBy(()->inspect(wrong)).isInstanceOf(CodeFailure.class);
    }
    @Test void formulaUsesStoredCacheEvenWhenFormulaWouldNowDiffer() throws Exception {
        byte[] bytes;
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes(List.of(row()))));var out=new ByteArrayOutputStream()) {
            var sheet=book.getSheetAt(0); sheet.getRow(1).getCell(2).setCellValue(7);
            var cell=sheet.getRow(1).getCell(1); cell.setCellFormula("C2*2");
            book.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(cell);
            sheet.getRow(1).getCell(2).setCellValue(99); book.write(out); bytes=out.toByteArray();
        }
        var result=inspect(bytes); assertThat(result.errorCount()).isZero();
        assertThat(((ImportedMonth)result.records().get(0)).revenue).isEqualByComparingTo("14");
    }
    @Test void externalLinksAreRejected() throws Exception {
        byte[] bytes;
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes(List.of(row()))));var out=new ByteArrayOutputStream()) {
            var link=book.getCreationHelper().createHyperlink(org.apache.poi.common.usermodel.HyperlinkType.URL);
            link.setAddress("https://example.invalid/"); book.getSheetAt(0).getRow(1).getCell(1).setHyperlink(link);
            book.write(out); bytes=out.toByteArray();
        }
        assertThatThrownBy(()->inspect(bytes)).isInstanceOf(CodeFailure.class);
    }
}
