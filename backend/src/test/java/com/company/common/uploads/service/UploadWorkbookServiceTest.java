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
    // 00-1 코드 리뷰(2026-11-23): 날짜 서식 칸은 화면 서식(10/1/26)이 아니라 실제 날짜로, 연월 서식은 YYYY-MM으로, 큰 수는 지수 모양 없이 읽는다.
    @Test void dateFormattedCellsAreReadAsIsoDatesAndLargeNumbersStayPlain() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("데이터");
            var head = sheet.createRow(0);
            List.of("이름", "입사일", "연월", "금액").forEach(name -> head.createCell(head.getPhysicalNumberOfCells()).setCellValue(name));
            var dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat((short) 14);
            var monthStyle = workbook.createCellStyle();
            monthStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm"));
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("홍길동");
            var date = row.createCell(1);
            date.setCellValue(java.time.LocalDate.of(2026, 10, 1));
            date.setCellStyle(dateStyle);
            var month = row.createCell(2);
            month.setCellValue(java.time.LocalDate.of(2026, 1, 1));
            month.setCellStyle(monthStyle);
            row.createCell(3).setCellValue(150_000_000_000d);
            var parsed = workbooks.readTabular("날짜.xlsx", bytes(workbook), List.of("이름", "입사일", "연월", "금액"));
            assertThat(parsed).hasSize(1);
            assertThat(parsed.get(0).values()).containsExactly("홍길동", "2026-10-01", "2026-01", "150000000000");
        }
    }
    @Test void uploadDatesNeedFourDigitYears() {
        assertThat(com.company.common.developers.service.DeveloperUploadForm.parseDate("26-10-01")).isNull();
        assertThat(com.company.common.developers.service.DeveloperUploadForm.parseDate("2026.10.01"))
            .isEqualTo(java.time.LocalDate.of(2026, 10, 1));
    }
    // 00-1 코드 리뷰(2026-11-23): 값이 있는 줄이 너무 많으면 통째로 열기 전에 거절하고, 서식만 칠한 빈 줄은 세지 않는다.
    @Test void rowGuardRejectsTooManyValueRowsButIgnoresStyledEmptyRows() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("데이터");
            sheet.createRow(0).createCell(0).setCellValue("이름");
            for (int i = 1; i <= UploadWorkbookService.ROW_GUARD + 1; i++) sheet.createRow(i).createCell(0).setCellValue("사람" + i);
            byte[] many = bytes(workbook);
            assertThatThrownBy(() -> workbooks.readTabular("많음.xlsx", many, List.of("이름")))
                .isInstanceOf(CodeFailure.class).hasMessageContaining("5,000행");
        }
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("데이터");
            sheet.createRow(0).createCell(0).setCellValue("이름");
            for (int i = 1; i <= 3; i++) sheet.createRow(i).createCell(0).setCellValue("사람" + i);
            var style = workbook.createCellStyle();
            style.setDataFormat((short) 3);
            for (int i = 4; i < 30_000; i++) sheet.createRow(i).createCell(0).setCellStyle(style);
            assertThat(workbooks.readTabular("서식.xlsx", bytes(workbook), List.of("이름"))).hasSize(3);
        }
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
