package com.company.common.cashflowstatements.service;

import com.company.common.cashflowstatements.dto.CashFlowStatementContracts.*;
import com.company.common.cashflowstatements.mapper.CashFlowStatementMapper;
import com.company.common.operatingprofitstatements.dto.OperatingProfitStatementContracts.ExcelFile;
import com.company.common.support.service.*;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CashFlowStatementExcelService {
    private final CommonCashFlowStatementService reports;
    private final CashFlowStatementMapper mapper;
    private final ExcelExportService excel;
    private final CurrentBusinessActor actor;
    private final BusinessCalendar calendar;
    public CashFlowStatementExcelService(CommonCashFlowStatementService reports, CashFlowStatementMapper mapper,
        ExcelExportService excel, CurrentBusinessActor actor, BusinessCalendar calendar) {
        this.reports = reports;
        this.mapper = mapper;
        this.excel = excel;
        this.actor = actor;
        this.calendar = calendar;
    }
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ExcelFile getCommonCashFlowStatementsExcel(Filter filter, String amountUnit) {
        BigDecimal divisor = switch (amountUnit == null ? "" : amountUnit) {
            case "원" -> BigDecimal.ONE;
            case "천 원" -> BigDecimal.valueOf(1000);
            case "백만 원" -> BigDecimal.valueOf(1000000);
            default -> throw CodeFailure.invalid("amountUnit", "금액 단위를 선택해 주세요.");
        };
        var report = reports.getCommonCashFlowStatements(filter).data();
        var table = report.numericTable();
        var headers = new ArrayList<String>();
        headers.add("항목");
        table.columns().forEach(c -> headers.add(c.label() + (c.actual() == null ? "" : c.actual() ? " 실적" : " 예정")));
        var rows = table.rows().stream().map(line -> {
            List<String> cells = new ArrayList<>();
            cells.add(line.label());
            // 「수입」·「지출」 묶음 줄은 화면·시안처럼 빈칸(B.4 화면과 같은 내용, 46번 점검)
            boolean group = "group".equals(line.style());
            line.values().forEach(value -> cells.add(group ? "" : value == null ? "–" : value.toPlainString()));
            return cells;
        }).toList();
        var now = calendar.now();
        byte[] bytes = style(excel.write("자금수지표", headers, rows, actor.userId(), now), report, divisor, amountUnit);
        if (mapper.insertAccess(actor.userId(), "자금수지표 " + report.year() + " " + amountUnit) != 1)
            throw new IllegalStateException("다운로드 접근 기록을 저장하지 못했습니다.");
        return new ExcelFile(bytes, excel.fileName("자금수지표", String.valueOf(report.year()), now), rows.size());
    }
    private static byte[] style(byte[] bytes, Result report, BigDecimal divisor, String unit) {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes)); var out = new ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            sheet.createRow(1).createCell(0).setCellValue(report.year() + "년 자금수지 (부가세 포함) · 단위: " + unit);
            var styles = new HashMap<String, CellStyle>();
            for (int r = 0; r < report.numericTable().rows().size(); r++) {
                var line = report.numericTable().rows().get(r);
                sheet.getRow(r + 3).setHeightInPoints(30);
                for (int c = 0; c <= 15; c++) {
                    var cell = sheet.getRow(r + 3).getCell(c);
                    boolean forecast = c > 0 && Boolean.FALSE.equals(report.numericTable().columns().get(c - 1).actual());
                    String key = line.style() + ":" + forecast;
                    var style = styles.computeIfAbsent(key, ignored -> {
                        var created = book.createCellStyle();
                        var font = book.createFont();
                        font.setBold("bold".equals(line.style()) || "group".equals(line.style()));
                        font.setColor((forecast ? IndexedColors.GREY_50_PERCENT : IndexedColors.BLACK).getIndex());
                        created.setFont(font);
                        created.setDataFormat(book.createDataFormat().getFormat("#,##0;[Red]−#,##0"));
                        return created;
                    });
                    cell.setCellStyle(style);
                    if (c == 0) continue;
                    BigDecimal value = line.values().get(c - 1);
                    if (value == null) cell.setCellValue("–");
                    else cell.setCellValue(value.divide(divisor, 0, value.signum() < 0 ? RoundingMode.HALF_DOWN : RoundingMode.HALF_UP).doubleValue());
                }
            }
            sheet.setColumnWidth(0, 32 * 256);
            sheet.createFreezePane(1, 3);
            book.write(out);
            return out.toByteArray();
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
