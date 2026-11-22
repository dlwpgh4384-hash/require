package com.company.common.netprofitstatements.service;

import com.company.common.netprofitstatements.dto.NetProfitStatementContracts.Filter;
import com.company.common.netprofitstatements.mapper.NetProfitStatementMapper;
import com.company.common.operatingprofitstatements.dto.OperatingProfitStatementContracts.*;
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
public class NetProfitStatementExcelService {
    private final CommonNetProfitStatementService reports;
    private final NetProfitStatementMapper mapper;
    private final ExcelExportService excel;
    private final CurrentBusinessActor actor;
    private final BusinessCalendar calendar;

    public NetProfitStatementExcelService(CommonNetProfitStatementService reports,
        NetProfitStatementMapper mapper, ExcelExportService excel, CurrentBusinessActor actor,
        BusinessCalendar calendar) {
        this.reports = reports;
        this.mapper = mapper;
        this.excel = excel;
        this.actor = actor;
        this.calendar = calendar;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ExcelFile getCommonNetProfitStatementsExcel(Filter filter, String amountUnit) {
        BigDecimal divisor = switch (amountUnit == null ? "" : amountUnit) {
            case "원" -> BigDecimal.ONE;
            case "천 원" -> BigDecimal.valueOf(1000);
            case "백만 원" -> BigDecimal.valueOf(1000000);
            default -> throw CodeFailure.invalid("amountUnit", "금액 단위를 선택해 주세요.");
        };
        var report = reports.getCommonNetProfitStatements(filter).data();
        var table = report.numericTable();
        var headers = new ArrayList<String>();
        headers.add("항목");
        table.columns().forEach(c -> headers.add(c.heading()));
        var rows = table.rows().stream().map(line -> {
            var cells = new ArrayList<String>();
            cells.add(line.label());
            line.values().forEach(cell -> cells.add(cell.value() == null ? "–" : cell.value().toPlainString()));
            return (List<String>) cells;
        }).toList();
        var now = calendar.now();
        var bytes = style(excel.write("순이익표", headers, rows, actor.userId(), now), table, divisor);
        if (mapper.insertAccess(actor.userId(), "순이익표 " + filter.baseMonth() + " " + amountUnit) != 1) {
            throw new IllegalStateException("다운로드 접근 기록을 저장하지 못했습니다.");
        }
        return new ExcelFile(bytes, excel.fileName("순이익표", filter.baseMonth(), now), rows.size());
    }

    private static byte[] style(byte[] bytes, NumericTable table, BigDecimal divisor) {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes));
             var out = new ByteArrayOutputStream()) {
            var styles = new HashMap<String, CellStyle>();
            for (int r = 0; r < table.rows().size(); r++) {
                var line = table.rows().get(r);
                for (int c = 0; c < line.values().size(); c++) {
                    var source = line.values().get(c);
                    var target = book.getSheetAt(0).getRow(r + 3).getCell(c + 1);
                    var value = source.value();
                    if (value != null && "money".equals(source.format())) {
                        value = value.divide(divisor, 0,
                            value.signum() < 0 ? RoundingMode.HALF_DOWN : RoundingMode.HALF_UP);
                    }
                    if (value == null) target.setCellValue("–");
                    else target.setCellValue(value.doubleValue());
                    boolean negative = value != null && value.signum() < 0;
                    String key = source.format() + ":" + line.style() + ":" + negative;
                    var style = styles.computeIfAbsent(key, ignored -> {
                        var created = book.createCellStyle();
                        String format = switch (source.format()) {
                            case "percent" -> "0.0\"%\"";
                            case "percentage-point" -> "0.0\"%p\"";
                            default -> "#,##0;[Red]\"−\"#,##0";
                        };
                        created.setDataFormat(book.createDataFormat().getFormat(format));
                        var font = book.createFont();
                        font.setBold("bold".equals(line.style()) || "group".equals(line.style()));
                        if (negative) font.setColor(IndexedColors.RED.getIndex());
                        created.setFont(font);
                        return created;
                    });
                    target.setCellStyle(style);
                }
                var labelStyle = book.createCellStyle();
                var font = book.createFont();
                font.setBold("bold".equals(line.style()) || "group".equals(line.style()));
                labelStyle.setFont(font);
                if ("indent".equals(line.style())) labelStyle.setIndention((short) 1);
                book.getSheetAt(0).getRow(r + 3).getCell(0).setCellStyle(labelStyle);
            }
            book.write(out);
            return out.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
