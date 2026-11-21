package com.company.common.operatingprofitstatements.service;

import com.company.common.operatingprofitstatements.dto.OperatingProfitStatementContracts.*;
import com.company.common.operatingprofitstatements.mapper.OperatingProfitStatementMapper;
import com.company.common.support.service.*;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatingProfitStatementExcelService {
    private final CommonOperatingProfitStatementService reports;
    private final OperatingProfitStatementMapper mapper;
    private final ExcelExportService excel;
    private final CurrentBusinessActor actor;
    private final BusinessCalendar calendar;
    public OperatingProfitStatementExcelService(CommonOperatingProfitStatementService reports,
        OperatingProfitStatementMapper mapper, ExcelExportService excel, CurrentBusinessActor actor,
        BusinessCalendar calendar) {
        this.reports = reports; this.mapper = mapper; this.excel = excel; this.actor = actor; this.calendar = calendar;
    }
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ExcelFile getCommonOperatingProfitStatementsExcel(Filter filter, String amountUnit) {
        BigDecimal divisor = switch (amountUnit == null ? "" : amountUnit) {
            case "원" -> BigDecimal.ONE;
            case "천 원" -> BigDecimal.valueOf(1000);
            case "백만 원" -> BigDecimal.valueOf(1000000);
            default -> throw CodeFailure.invalid("amountUnit", "금액 단위를 선택해 주세요.");
        };
        var report = reports.getCommonOperatingProfitStatements(filter).data();
        var table = report.numericTable();
        var headers = new ArrayList<String>();
        headers.add("항목");
        table.columns().forEach(c -> headers.add(c.heading()));
        var rows = new ArrayList<List<String>>();
        for (var line : table.rows()) {
            var values = new ArrayList<String>();
            values.add(line.label());
            for (var cell : line.values()) values.add(cell.value() == null ? "–" :
                (cell.format().equals("money") ? cell.value().divide(divisor, 0,
                    cell.value().signum() < 0 ? RoundingMode.HALF_DOWN : RoundingMode.HALF_UP) : cell.value()).toPlainString());
            rows.add(values);
        }
        var now = calendar.now();
        byte[] bytes = style(excel.write("영업이익표", headers, rows, actor.userId(), now), table);
        if (mapper.insertAccess(actor.userId(), "영업이익표 " + filter.baseMonth() + " " + amountUnit) != 1)
            throw new IllegalStateException("다운로드 접근 기록을 저장하지 못했습니다.");
        return new ExcelFile(bytes, excel.fileName("영업이익표", filter.baseMonth(), now), rows.size());
    }
    private static byte[] style(byte[] bytes, NumericTable table) {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes)); var out = new ByteArrayOutputStream()) {
            var money = book.createCellStyle();
            money.setDataFormat(book.createDataFormat().getFormat("#,##0"));
            var percent = book.createCellStyle();
            percent.setDataFormat(book.createDataFormat().getFormat("0.0\"%\""));
            var point = book.createCellStyle();
            point.setDataFormat(book.createDataFormat().getFormat("0.0\"%p\""));
            for (int r = 0; r < table.rows().size(); r++) {
                var line = table.rows().get(r);
                for (int c = 0; c < line.values().size(); c++) {
                    var cell = book.getSheetAt(0).getRow(r + 3).getCell(c + 1);
                    cell.setCellStyle(switch (line.values().get(c).format()) {
                        case "percent" -> percent;
                        case "percentage-point" -> point;
                        default -> money;
                    });
                }
            }
            book.write(out);
            return out.toByteArray();
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
