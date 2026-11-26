package com.company.common.support.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** 엑셀 숫자 열은 숫자 칸으로 써서 엑셀에서 바로 더할 수 있다(2026-11-21 사용자 결정). */
class ExcelExportNumericCellTest {
    @Test
    void numberColumnsBecomeNumericCellsAndIdsPhonesStayText() throws Exception {
        var bytes = new ExcelExportService().write("시험",
            List.of("이름", "관리번호", "전화", "MM", "금액", "비고"),
            List.of(List.of("가", "1001", "01012345678", "0.52", "-1,234,567", "–"),
                List.of("나", "1002", "0212345678", "1.00", "7000000", "메모"),
                List.of("합계", "", "", "1.52", "5765433", "")),
            "admin", Instant.parse("2026-11-20T15:00:00Z"));
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = book.getSheetAt(0);
            var format = new DataFormatter();
            var first = sheet.getRow(3);
            assertThat(first.getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(first.getCell(2).getCellType()).isEqualTo(CellType.STRING);
            assertThat(first.getCell(3).getNumericCellValue()).isEqualTo(0.52d);
            assertThat(format.formatCellValue(sheet.getRow(4).getCell(3))).isEqualTo("1.00");
            assertThat(first.getCell(4).getNumericCellValue()).isEqualTo(-1234567d);
            assertThat(format.formatCellValue(sheet.getRow(4).getCell(4))).isEqualTo("7,000,000");
            assertThat(first.getCell(5).getStringCellValue()).isEqualTo("–");
            assertThat(sheet.getRow(5).getCell(4).getNumericCellValue()).isEqualTo(5765433d);
        }
    }
    @Test
    void wordsWithoutDigitsDoNotTurnAPriceColumnIntoText() throws Exception {
        var bytes = new ExcelExportService().write("시험", List.of("이름", "월 단가"),
            List.of(List.of("가", "5000000"), List.of("나", "배분표"), List.of("다", "무상지원")),
            "admin", Instant.parse("2026-11-20T15:00:00Z"));
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(3).getCell(1).getNumericCellValue()).isEqualTo(5000000d);
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo("배분표");
            assertThat(sheet.getRow(5).getCell(1).getStringCellValue()).isEqualTo("무상지원");
        }
    }
    @Test
    void manySheetsGetUniqueNamesWithinThirtyOneCharactersEvenAfterSuffixing() throws Exception {
        String longName = "가".repeat(40);
        var sheet = new ExcelExportService.SheetData(longName, "제목", List.of("이름"), List.of(List.of("가")));
        var bytes = new ExcelExportService().write(List.of(sheet, sheet, sheet,
            new ExcelExportService.SheetData("a/b:c", "제목", List.of("이름"), List.of()),
            new ExcelExportService.SheetData("A B C", "제목", List.of("이름"), List.of())),
            "admin", Instant.parse("2026-11-20T15:00:00Z"));
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(book.getNumberOfSheets()).isEqualTo(5);
            var names = new java.util.HashSet<String>();
            for (int i = 0; i < 5; i++) {
                String name = book.getSheetName(i);
                assertThat(name.length()).isLessThanOrEqualTo(31);
                assertThat(names.add(name.toLowerCase(java.util.Locale.ROOT))).as(name).isTrue();
            }
            assertThat(book.getSheetName(0)).isEqualTo("가".repeat(31));
            assertThat(book.getSheetName(1)).isEqualTo("가".repeat(29) + " 2");
            assertThat(book.getSheetName(2)).isEqualTo("가".repeat(29) + " 3");
            assertThat(book.getSheetName(3)).isEqualTo("a b c");
            assertThat(book.getSheetName(4)).isEqualTo("A B C 2");
        }
    }
    @Test
    void noSheetsStillWritesOneReadableSheetWithItsHeaderLine() throws Exception {
        var bytes = new ExcelExportService().write(List.of(), "admin", Instant.parse("2026-11-20T15:00:00Z"));
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(book.getNumberOfSheets()).isEqualTo(1);
            assertThat(book.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).startsWith("자료 없음 · 다운로드: admin");
        }
    }
    @Test
    void singleSheetExportKeepsItsTitleAsTheSheetName() throws Exception {
        var bytes = new ExcelExportService().write("철수 리스트", List.of("이름"), List.of(List.of("가")),
            "admin", Instant.parse("2026-11-20T15:00:00Z"));
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(book.getNumberOfSheets()).isEqualTo(1);
            assertThat(book.getSheetName(0)).isEqualTo("철수 리스트");
        }
    }
}
