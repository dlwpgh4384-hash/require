package com.company.common.uploads.service;

import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.ExcelExportService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UploadWorkbookServiceTest {
    private final UploadWorkbookService workbooks = new UploadWorkbookService(new ExcelExportService());
    private final List<String> headers = List.of("상호", "사업자번호");

    @Test void templateAndReaderAgreeOnThirdRowHeaderAndFirstDataRow() throws Exception {
        byte[] template = workbooks.template("거래처", headers, "admin", Instant.parse("2026-12-31T15:00:00Z"));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("다운로드: admin", "2027-01-01 00:00");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("상호");
            assertThat(workbooks.read("거래처.xlsx", template, headers)).isEmpty();
            var row = sheet.createRow(3);
            row.createCell(0).setCellValue("입력 그대로");
            row.createCell(1).setCellValue("123-45-67890");
            var parsed = workbooks.read("거래처.xlsx", bytes(workbook), headers);
            assertThat(parsed).hasSize(1);
            assertThat(parsed.get(0).rowNumber()).isEqualTo(4);
            assertThat(parsed.get(0).values()).containsExactly("입력 그대로", "123-45-67890");
        }
    }
    @Test void duplicateRowsAreRetainedForResourceAdapterValidationNotSilentlyDropped() throws Exception {
        byte[] workbook = new ExcelExportService().write("거래처", headers,
            List.of(List.of("중복", "123-45-67890"), List.of("중복", "123-45-67890")), "admin", Instant.EPOCH);
        var parsed = workbooks.read("거래처.xlsx", workbook, headers);
        assertThat(parsed).hasSize(2);
        assertThat(parsed).extracting(row -> row.rowNumber()).containsExactly(4, 5);
    }
    @Test void renamedNonWorkbookCannotBeParsedAsXlsx() {
        assertThatThrownBy(() -> workbooks.read("renamed.xlsx", "not an Excel file".getBytes(StandardCharsets.UTF_8), headers))
            .isInstanceOf(CodeFailure.class);
    }
    @Test void malformedZipIsRejected() {
        assertThatThrownBy(() -> workbooks.read("broken.xlsx", new byte[]{'P', 'K', 3, 4}, headers))
            .isInstanceOf(CodeFailure.class);
    }
    @Test void formulaCellsAreReadAsTheirSavedResult() throws Exception {
        // SFR-012-03: 수식 칸은 실행하지 않고 엑셀이 저장해 둔 결과값으로 읽는다.
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            var row = workbook.getSheetAt(0).createRow(3);
            var cell = row.createCell(0);
            cell.setCellFormula("\"가\"&\"온\"");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(cell);
            row.createCell(1).setCellValue("123-45-67890");
            var parsed = workbooks.read("formula.xlsx", bytes(workbook), headers);
            assertThat(parsed).hasSize(1);
            assertThat(parsed.get(0).values()).containsExactly("가온", "123-45-67890");
        }
    }
    @Test void externalRelationshipsAreRejected() throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            workbook.getPackagePart().addExternalRelationship("https://example.invalid/workbook.xlsx", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/externalLink");
            assertThatThrownBy(() -> workbooks.read("external.xlsx", bytes(workbook), headers)).isInstanceOf(CodeFailure.class);
        }
    }
    @Test void externalEntitiesAreRejectedBeforeXmlParsing() throws Exception {
        byte[] hostile = replaceWorkbookXml(template());
        assertThatThrownBy(() -> workbooks.read("entity.xlsx", hostile, headers)).isInstanceOf(CodeFailure.class);
    }
    @Test void wrongHeaderIsRejectedRatherThanUsingFirstRowAsData() throws Exception {
        assertThatThrownBy(() -> workbooks.read("wrong.xlsx", template(), List.of("다른 열")))
            .isInstanceOf(CodeFailure.class);
    }
    @Test void filenameCannotSupplyDirectoryTraversal() throws Exception {
        assertThatThrownBy(() -> workbooks.read("../clients.xlsx", template(), headers)).isInstanceOf(CodeFailure.class);
    }
    private byte[] template() {
        return workbooks.template("거래처", headers, "admin", Instant.EPOCH);
    }
    private byte[] bytes(XSSFWorkbook workbook) throws Exception {
        try (var output = new ByteArrayOutputStream()) { workbook.write(output); return output.toByteArray(); }
    }
    private byte[] replaceWorkbookXml(byte[] original) throws Exception {
        try (var input = new ZipInputStream(new ByteArrayInputStream(original));
             var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output)) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                zip.putNextEntry(new ZipEntry(entry.getName()));
                byte[] content = input.readAllBytes();
                if (entry.getName().equals("xl/workbook.xml")) {
                    content = "<?xml version=\"1.0\"?><!DOCTYPE workbook [<!ENTITY xxe SYSTEM \"file:///forbidden\">]><workbook>&xxe;</workbook>"
                        .getBytes(StandardCharsets.UTF_8);
                }
                zip.write(content);
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }
}
