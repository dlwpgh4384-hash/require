package com.company.common.support.service;

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

    /** 시트 하나: name은 시트 이름(엑셀 규칙에 맞게 고쳐 씀), title은 1행 제목. */
    public record SheetData(String name, String title, List<String> headers, List<List<String>> rows) {}

    public byte[] write(String title, List<String> headers, List<List<String>> rows, String downloadedBy,
        Instant atUtc) {
        return write(List.of(new SheetData(title, title, headers, rows)), downloadedBy, atUtc);
    }

    /** 표가 여러 개인 답(예: AI 분석 도우미)은 표마다 시트 하나. 각 시트 모양은 한 시트 파일과 같다. */
    public byte[] write(List<SheetData> sheets, String downloadedBy, Instant atUtc) {
        try (var workbook = new SXSSFWorkbook(200); var out = new ByteArrayOutputStream()) {
            // ZIP64가 필요 없는 파일은 JDK ZipInputStream으로도 읽을 수 있게 쓴다.
            workbook.setZip64Mode(Zip64Mode.AsNeeded);
            var names = new java.util.HashSet<String>();
            // 표가 하나도 없으면 깨진 파일 대신 제목 줄만 있는 빈 시트 하나를 쓴다.
            var list = sheets == null || sheets.isEmpty()
                ? List.of(new SheetData(EMPTY_SHEET, EMPTY_SHEET, List.of(), List.of())) : sheets;
            for (var data : list) {
            String title = data.title() == null ? "" : data.title();
            List<String> headers = data.headers() == null ? List.of() : data.headers();
            List<List<String>> rows = data.rows() == null ? List.of() : data.rows();
            Sheet sheet = workbook.createSheet(sheetName(data.name(), names));
            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle head = workbook.createCellStyle();
            head.setFont(bold);
            sheet.createRow(0).createCell(0).setCellValue(
                title + " · 다운로드: " + downloadedBy + " · " + korea(atUtc).format(STAMP));
            boolean[] numeric = numericColumns(headers, rows);
            // 소수가 하나라도 있는 열(MM 등)은 열 전체를 소수 둘째 자리로 보인다(3.00).
            boolean[] decimal = new boolean[headers.size()];
            for (List<String> values : rows) for (int c = 0; c < Math.min(values.size(), decimal.length); c++)
                if (numeric[c] && values.get(c) != null && values.get(c).contains(".")) decimal[c] = true;
            CellStyle integerStyle = workbook.createCellStyle();
            integerStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            CellStyle decimalStyle = workbook.createCellStyle();
            decimalStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            Row header = sheet.createRow(2);
            for (int i = 0; i < headers.size(); i++) {
                var cell = header.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(head);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 3);
                List<String> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    String value = values.get(c) == null ? "" : values.get(c);
                    var cell = row.createCell(c);
                    if (c < numeric.length && numeric[c] && NUMBER.matcher(value).matches()) {
                        var number = new java.math.BigDecimal(value.replace(",", ""));
                        cell.setCellValue(number.doubleValue());
                        cell.setCellStyle(decimal[c] ? decimalStyle : integerStyle);
                    } else cell.setCellValue(value);
                }
            }
            for (int i = 0; i < headers.size(); i++) sheet.setColumnWidth(i, 18 * 256);
            }
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static final String EMPTY_SHEET = "자료 없음";
    /**
     * 엑셀 시트 이름 규칙: 금지 글자(\ / ? * [ ] :)는 빈칸, 31자 이하, 같은 파일 안에서 대소문자 없이 겹치지 않게.
     * 겹치면 「 2」「 3」…을 붙이되 붙인 뒤에도 31자를 넘지 않게 앞부분을 줄인다.
     */
    static String sheetName(String requested, java.util.Set<String> used) {
        String name = requested == null ? "" : requested.replaceAll("[\\\\/?*\\[\\]:]", " ").strip();
        // 작은따옴표는 시트 이름 앞뒤에 올 수 없다.
        name = name.replaceAll("^'+|'+$", "").strip();
        if (name.isEmpty()) name = "Sheet";
        if (name.length() > 31) name = name.substring(0, 31);
        String unique = name;
        for (int n = 2; !used.add(unique.toLowerCase(java.util.Locale.ROOT)); n++) {
            String suffix = " " + n;
            unique = name.substring(0, Math.min(name.length(), 31 - suffix.length())).stripTrailing() + suffix;
        }
        return unique;
    }

    /** 숫자 모양(1234, -1,234, 0.52). 앞자리 0(전화·계좌 등)은 글자로 둔다. */
    private static final java.util.regex.Pattern NUMBER =
        java.util.regex.Pattern.compile("-?(0|[1-9][0-9]{0,2}(,[0-9]{3})+|[1-9][0-9]*)(\\.[0-9]+)?");
    /** 이름이 번호·코드·연도인 열은 숫자처럼 보여도 글자로 둔다(예: 사업자번호, 관리번호, 2026). */
    private static final java.util.regex.Pattern TEXT_HEADER =
        java.util.regex.Pattern.compile(".*(번호|코드|ID|아이디|연도|년도|년월|우편).*");

    /**
     * 엑셀에서 바로 더할 수 있게 숫자 열은 숫자 칸으로 쓴다(2026-11-21 사용자 결정, 전 화면 공통).
     * 빈칸·「–」를 뺀 모든 값이 숫자 모양인 열만 숫자 열로 본다. 합계 줄 등 글자 값이 섞인 칸은 글자로 둔다.
     */
    static boolean[] numericColumns(List<String> headers, List<List<String>> rows) {
        boolean[] numeric = new boolean[headers.size()];
        for (int c = 0; c < headers.size(); c++) {
            if (headers.get(c) != null && TEXT_HEADER.matcher(headers.get(c)).matches()) continue;
            boolean any = false, all = true;
            for (List<String> row : rows) {
                if (c >= row.size()) continue;
                String value = row.get(c);
                if (value == null || value.isBlank() || value.equals("–") || value.equals("-")) continue;
                // 숫자 열 속 「배분표」·「무상지원」처럼 숫자가 하나도 없는 글은 빈칸처럼 본다(나머지 금액은 숫자 칸 그대로, 2026-11-24).
                if (value.chars().noneMatch(Character::isDigit)) continue;
                if (NUMBER.matcher(value).matches()) any = true; else if (!isLabelRow(row)) { all = false; break; }
            }
            numeric[c] = any && all;
        }
        return numeric;
    }

    /** 「합계」 같은 맨 아래 줄은 글자가 섞여도 열 판단에서 뺀다(첫 칸이 글자이고 숫자 모양 칸이 아닌 값). */
    private static boolean isLabelRow(List<String> row) {
        return !row.isEmpty() && row.get(0) != null && (row.get(0).startsWith("합계") || row.get(0).startsWith("법인카드")
            || row.get(0).startsWith("파트 이익금") || row.get(0).startsWith("월별"));
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
