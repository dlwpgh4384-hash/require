package com.company.common.uploads.service;

import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.ExcelExportService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UploadTemplateTest {
    final UploadWorkbookService workbooks = new UploadWorkbookService(new ExcelExportService());
    byte[] template() { return workbooks.tabularFile(ClientUploadRules.HEADERS, Collections.emptyList(), "별도 설명"); }
    byte[] bytes(XSSFWorkbook workbook) throws Exception {
        var output = new ByteArrayOutputStream(); workbook.write(output); return output.toByteArray();
    }
    @Test void firstSheetOnlyAndBlankFormattedRowsDoNotCountAndPhysicalRowsSurvive() throws Exception {
        try(var workbook = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            var sheet=workbook.getSheetAt(0); sheet.createRow(6001).createCell(0).setCellValue(" ");
            sheet.createRow(5999).createCell(1).setCellValue("실제 입력");
            workbook.getSheetAt(1).createRow(1).createCell(0).setCellFormula("1+1");
            var rows=workbooks.readTabular("clients.xlsx",bytes(workbook),ClientUploadRules.HEADERS);
            assertThat(rows).hasSize(1); assertThat(rows.get(0).rowNumber()).isEqualTo(6000);
            assertThat(rows.get(0).values().get(1)).isEqualTo("실제 입력");
        }
    }
    @Test void headerNamesDetermineColumnsNotTheirPosition() throws Exception {
        var headers=new ArrayList<>(ClientUploadRules.HEADERS); Collections.reverse(headers);
        var row=new ArrayList<>(Collections.nCopies(11,"")); row.set(headers.indexOf("상호"),"반대 순서");
        var rows=workbooks.readTabular("clients.xlsx",workbooks.tabularFile(headers,List.of(row),null),ClientUploadRules.HEADERS);
        assertThat(rows.get(0).values().get(1)).isEqualTo("반대 순서");
    }
    @Test void legacyExportMetadataIsNotInterpretedAsData() throws Exception {
        var values=new ArrayList<>(Collections.nCopies(11,"")); values.set(1,"거래처");
        byte[] bytes=new ExcelExportService().write("내려받기",ClientUploadRules.HEADERS,List.of(values),"admin",Instant.EPOCH);
        var rows=workbooks.readTabular("clients.xlsx",bytes,ClientUploadRules.HEADERS);
        assertThat(rows).hasSize(1); assertThat(rows.get(0).rowNumber()).isEqualTo(4);
    }
    @Test void fiveThousandNonemptyRowsAcceptedButNextRowRejected() throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            var sheet=workbook.getSheetAt(0);
            for(int i=1;i<=5000;i++) sheet.createRow(i).createCell(1).setCellValue("행");
            assertThat(workbooks.readTabular("clients.xlsx",bytes(workbook),ClientUploadRules.HEADERS)).hasSize(5000);
            sheet.createRow(5001).createCell(1).setCellValue("추가");
            assertThatThrownBy(() -> workbooks.readTabular("clients.xlsx",bytes(workbook),ClientUploadRules.HEADERS))
                .isInstanceOf(CodeFailure.class).hasMessageContaining("5,000");
        }
    }
    @Test void formulaCellIsReadAsItsSavedResult() throws Exception {
        // SFR-012-03: 수식 칸은 결과값으로 읽는다(수식을 실행하지 않고 엑셀이 저장해 둔 값).
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            var cell=workbook.getSheetAt(0).createRow(1).createCell(1);
            cell.setCellFormula("1+1");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(cell);
            var rows=workbooks.readTabular("clients.xlsx",bytes(workbook),ClientUploadRules.HEADERS);
            assertThat(rows.get(0).formulaColumns()).isEmpty();
            assertThat(rows.get(0).values().get(1)).isEqualTo("2");
        }
    }
    @Test void malformedAndRenamedTypesAndPathNamesRejected() {
        byte[] text="not an xlsx".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> workbooks.readTabular("renamed.xlsx",text,ClientUploadRules.HEADERS)).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> workbooks.readTabular("../clients.xlsx",template(),ClientUploadRules.HEADERS)).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> workbooks.readTabular("clients.csv",template(),ClientUploadRules.HEADERS)).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> workbooks.readTabular("broken.xlsx",new byte[]{'P','K',3,4},ClientUploadRules.HEADERS)).isInstanceOf(CodeFailure.class);
    }
    @Test void decimalTenMillionByteLimitIsNotTenMebibytes() {
        byte[] bytes=new byte[10_000_001]; bytes[0]='P'; bytes[1]='K';
        assertThatThrownBy(() -> workbooks.readTabular("large.xlsx",bytes,ClientUploadRules.HEADERS)).isInstanceOf(CodeFailure.class);
    }
    @Test void missingAndDuplicateHeadersRejected() throws Exception {
        var wrong=new ArrayList<>(ClientUploadRules.HEADERS); wrong.set(1,"잘못된 열");
        assertThatThrownBy(() -> workbooks.readTabular("clients.xlsx",workbooks.tabularFile(wrong,List.of(),null),ClientUploadRules.HEADERS))
            .isInstanceOf(CodeFailure.class);
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            workbook.getSheetAt(0).getRow(0).createCell(11).setCellValue("상호");
            assertThatThrownBy(() -> workbooks.readTabular("clients.xlsx",bytes(workbook),ClientUploadRules.HEADERS)).isInstanceOf(CodeFailure.class);
        }
    }
    @Test void externalRelationshipIsRejected() throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            workbook.getPackagePart().addExternalRelationship("https://example.invalid/data.xlsx",
                "http://schemas.openxmlformats.org/officeDocument/2006/relationships/externalLink");
            assertThatThrownBy(() -> workbooks.readTabular("clients.xlsx",bytes(workbook),ClientUploadRules.HEADERS)).isInstanceOf(CodeFailure.class);
        }
    }
    @Test void externalEntitiesAreRejectedBeforeParsing() throws Exception {
        var output=new ByteArrayOutputStream();
        try(var input=new ZipInputStream(new ByteArrayInputStream(template()));var zip=new ZipOutputStream(output)) {
            for(var entry=input.getNextEntry();entry!=null;entry=input.getNextEntry()) {
                zip.putNextEntry(new ZipEntry(entry.getName())); byte[] data=input.readAllBytes();
                if(entry.getName().equals("xl/workbook.xml")) data=("<?xml version=\"1.0\"?><!DOCTYPE workbook [<!ENTITY x SYSTEM \"file:///forbidden\">]><workbook>&x;</workbook>").getBytes(StandardCharsets.UTF_8);
                zip.write(data); zip.closeEntry();
            }
        }
        assertThatThrownBy(() -> workbooks.readTabular("clients.xlsx",output.toByteArray(),ClientUploadRules.HEADERS)).isInstanceOf(CodeFailure.class);
    }
    @Test void generatedErrorValuesAreStringsNeverFormulas() throws Exception {
        byte[] bytes=workbooks.tabularFile(List.of("행","열","입력값","사유"),List.of(List.of("2","상호","=HYPERLINK(\"x\")","수식 금지")),null);
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(2).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
        }
    }
}
