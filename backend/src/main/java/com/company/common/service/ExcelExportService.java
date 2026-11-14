package com.company.common.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * SFR-011 엑셀 다운로드 공통: 조회 결과 전체를 보이는 열 그대로 .xlsx로 만든다.
 * 1행에 다운로드한 사람·시각을 적고(SFR-011-03), 3행부터 머리글·자료를 쓴다.
 */
@Service
public class ExcelExportService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FILE = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    public byte[] write(String title, List<String> headers, List<List<String>> rows, String downloadedBy,
        Instant atUtc) {
        try (var workbook = new SXSSFWorkbook(200); var out = new ByteArrayOutputStream()) {
            // ZIP64가 필요 없는 파일은 JDK ZipInputStream으로도 읽을 수 있게 쓴다.
            workbook.setZip64Mode(Zip64Mode.AsNeeded);
            Sheet sheet = workbook.createSheet(title);
            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle head = workbook.createCellStyle();
            head.setFont(bold);
            sheet.createRow(0).createCell(0).setCellValue(
                title + " · 다운로드: " + downloadedBy + " · " + korea(atUtc).format(STAMP));
            Row header = sheet.createRow(2);
            for (int i = 0; i < headers.size(); i++) {
                var cell = header.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(head);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 3);
                List<String> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) row.createCell(c).setCellValue(values.get(c) == null ? "" : values.get(c));
            }
            for (int i = 0; i < headers.size(); i++) sheet.setColumnWidth(i, 18 * 256);
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** 파일 이름: 「화면이름_다운로드일시.xlsx」(기간이 있는 화면은 화면이름_기간_다운로드일시). */
    public String fileName(String screenName, String period, Instant atUtc) {
        String stamp = korea(atUtc).format(FILE);
        return screenName + (period == null || period.isBlank() ? "" : "_" + period) + "_" + stamp + ".xlsx";
    }

    /** 화면에 보이는 시각(한국 시간) 「yyyy-MM-dd HH:mm」. */
    public static String display(Instant utc) {
        return utc == null ? "–" : korea(utc).format(STAMP);
    }

    private static java.time.ZonedDateTime korea(Instant utc) {
        return utc.atZone(KOREA);
    }
}
