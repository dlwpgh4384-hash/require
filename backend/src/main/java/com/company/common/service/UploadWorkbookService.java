package com.company.common.service;

import com.company.common.dto.UploadWorkbookContracts.InputRow;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/** 형식별 코드·필수값 규칙은 호출자가 주입하며, 원본 문자열·엑셀 행번호를 보존한다. */
@Service
public class UploadWorkbookService {
    private final ExcelExportService exports;

    public UploadWorkbookService(ExcelExportService exports) { this.exports = exports; }

    public byte[] template(String title, List<String> headers, String downloadedBy, Instant at) {
        return exports.write(title, headers, java.util.Collections.emptyList(), downloadedBy, at);
    }

    public List<InputRow> read(String fileName, byte[] bytes, List<String> headers) {
        if (fileName == null || fileName.length() > 200 || fileName.contains("/") || fileName.contains("\\")
            || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx") || bytes == null || bytes.length < 4
            || bytes.length > 10 * 1024 * 1024 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw invalid();
        }
        inspectXml(bytes);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (workbook.isMacroEnabled() || workbook.getNumberOfSheets() == 0) throw invalid();
            for (var relationship : workbook.getPackage().getRelationships()) {
                if (relationship.getTargetMode() == TargetMode.EXTERNAL) throw invalid();
            }
            for (var part : workbook.getPackage().getParts()) {
                if (!part.isRelationshipPart()) {
                    for (var relationship : part.getRelationships()) {
                        if (relationship.getTargetMode() == TargetMode.EXTERNAL) throw invalid();
                    }
                }
            }
            for (var sheet : workbook) {
                for (var row : sheet) {
                    for (var cell : row) if (cell.getCellType() == CellType.FORMULA) throw invalid();
                }
            }
            var sheet = workbook.getSheetAt(0);
            var formatter = new DataFormatter(Locale.KOREA);
            var header = sheet.getRow(2);
            if (header == null || header.getLastCellNum() != headers.size()) throw invalid();
            for (int column = 0; column < headers.size(); column++) {
                if (!headers.get(column).equals(formatter.formatCellValue(header.getCell(column)))) throw invalid();
            }
            var result = new ArrayList<InputRow>();
            if (sheet.getLastRowNum() > 5002) throw CodeFailure.invalid("file", "5,000행 이하의 파일을 선택해 주세요.");
            for (int index = 3; index <= sheet.getLastRowNum(); index++) {
                var row = sheet.getRow(index);
                if (row == null) continue;
                if (row.getLastCellNum() > headers.size()) throw invalid();
                var values = new ArrayList<String>();
                for (int column = 0; column < headers.size(); column++) {
                    values.add(formatter.formatCellValue(row.getCell(column)));
                }
                if (values.stream().anyMatch(value -> !value.isBlank())) result.add(new InputRow(index + 1, values));
            }
            return List.copyOf(result);
        } catch (CodeFailure failure) {
            throw failure;
        } catch (Exception failure) {
            throw invalid();
        }
    }

    private void inspectXml(byte[] bytes) {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            int entries = 0;
            long expanded = 0;
            byte[] buffer = new byte[8192];
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > 1000) throw invalid();
                boolean xml = entry.getName().endsWith(".xml") || entry.getName().endsWith(".rels");
                String overlap = "";
                for (int size; (size = zip.read(buffer)) != -1;) {
                    expanded += size;
                    if (expanded > 64L * 1024 * 1024) throw invalid();
                    if (xml) {
                        String fragment = overlap + new String(buffer, 0, size, java.nio.charset.StandardCharsets.ISO_8859_1);
                        String upper = fragment.toUpperCase(Locale.ROOT);
                        if (upper.contains("<!DOCTYPE") || upper.contains("<!ENTITY") || fragment.indexOf('\0') >= 0) throw invalid();
                        overlap = fragment.substring(Math.max(0, fragment.length() - 16));
                    }
                }
            }
        } catch (CodeFailure failure) {
            throw failure;
        } catch (Exception failure) {
            throw invalid();
        }
    }

    public byte[] tabularFile(List<String> headers, List<List<String>> rows, String guidance) {
        try (var workbook = new XSSFWorkbook(); var output = new java.io.ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("데이터");
            var header = sheet.createRow(0);
            for (int column = 0; column < headers.size(); column++) {
                header.createCell(column).setCellValue(headers.get(column));
                sheet.setColumnWidth(column, 22 * 256);
            }
            for (int index = 0; index < rows.size(); index++) {
                var row = sheet.createRow(index + 1);
                for (int column = 0; column < rows.get(index).size(); column++)
                    row.createCell(column).setCellValue(rows.get(index).get(column));
            }
            sheet.createFreezePane(0, 1);
            if (guidance != null) workbook.createSheet("설명").createRow(0).createCell(0).setCellValue(guidance);
            workbook.write(output);
            return output.toByteArray();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("엑셀 파일을 만들지 못했습니다.");
        }
    }

    /** 이름으로 헤더를 찾는다. 수식은 실행하지 않고 호출자의 행 오류로 전달한다. */
    public List<com.company.common.dto.UploadWorkbookContracts.TabularRow> readTabular(
        String fileName, byte[] bytes, List<String> headers) {
        if (fileName == null || fileName.length() > 200 || fileName.contains("/") || fileName.contains("\\")
            || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx") || bytes == null || bytes.length < 4
            || bytes.length > 10_000_000 || bytes[0] != 'P' || bytes[1] != 'K') throw invalid();
        inspectXml(bytes);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (workbook.isMacroEnabled() || workbook.getNumberOfSheets() == 0) throw invalid();
            for (var part : workbook.getPackage().getParts()) {
                if (part.isRelationshipPart()) continue;
                for (var relation : part.getRelationships())
                    if (relation.getTargetMode() == TargetMode.EXTERNAL) throw invalid();
            }
            for (var relation : workbook.getPackage().getRelationships())
                if (relation.getTargetMode() == TargetMode.EXTERNAL) throw invalid();
            var sheet = workbook.getSheetAt(0);
            var formatter = new DataFormatter(Locale.KOREA);
            int headerIndex = -1;
            var columns = new java.util.LinkedHashMap<String, Integer>();
            for (var row : sheet) {
                columns.clear();
                for (var cell : row) {
                    if (cell.getCellType() == CellType.FORMULA) continue;
                    String name = formatter.formatCellValue(cell).trim();
                    if (headers.contains(name) && columns.put(name, cell.getColumnIndex()) != null) throw invalid();
                }
                if (columns.keySet().containsAll(headers)) { headerIndex = row.getRowNum(); break; }
                if (row.getRowNum() >= 20) break;
            }
            if (headerIndex < 0) throw invalid();
            var result = new ArrayList<com.company.common.dto.UploadWorkbookContracts.TabularRow>();
            for (var row : sheet) {
                if (row.getRowNum() <= headerIndex) continue;
                var values = new ArrayList<String>();
                var formulas = new ArrayList<Integer>();
                for (int index = 0; index < headers.size(); index++) {
                    var cell = row.getCell(columns.get(headers.get(index)));
                    boolean formula = cell != null && cell.getCellType() == CellType.FORMULA;
                    if (formula) formulas.add(index);
                    values.add(formula ? "=" + cell.getCellFormula() : formatter.formatCellValue(cell));
                }
                if (values.stream().allMatch(String::isBlank)) continue;
                result.add(new com.company.common.dto.UploadWorkbookContracts.TabularRow(
                    row.getRowNum() + 1, values, formulas));
                if (result.size() > 5000) throw CodeFailure.invalid("file", "5,000행 이하의 파일을 선택해 주세요.");
            }
            return List.copyOf(result);
        } catch (CodeFailure failure) {
            throw failure;
        } catch (Exception failure) {
            throw invalid();
        }
    }

    private static CodeFailure invalid() {
        return CodeFailure.invalid("file", "올바른 xlsx 양식을 선택해 주세요. 수식·외부 연결은 사용할 수 없습니다.");
    }
}
