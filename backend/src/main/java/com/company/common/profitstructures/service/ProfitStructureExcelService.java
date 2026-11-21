package com.company.common.profitstructures.service;

import com.company.common.profitstructures.dto.ProfitStructureContracts.*;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.support.service.ExcelExportService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 시안: 「엑셀 다운로드는 이 표와 같습니다」 — 숫자로 보기 표(줄 = 항목, 열 = 기간)를 그대로 쓴다. */
@Service
public class ProfitStructureExcelService {
    private final CommonProfitStructureService service;
    private final ProfitStructureMapper mapper;
    private final ExcelExportService excel;
    private final CurrentBusinessActor actor;
    private final BusinessCalendar calendar;
    public ProfitStructureExcelService(CommonProfitStructureService service, ProfitStructureMapper mapper,
        ExcelExportService excel, CurrentBusinessActor actor, BusinessCalendar calendar) {
        this.service = service; this.mapper = mapper; this.excel = excel; this.actor = actor; this.calendar = calendar;
    }
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ExcelFile getCommonProfitStructuresExcel(Filter filter) {
        var result = service.getCommonProfitStructures(filter).data();
        var table = result.numericTable();
        List<String> headers = new ArrayList<>();
        headers.add("항목");
        for (var column : table.columns()) headers.add(column.label() + (column.provisional() ? " 잠정" : ""));
        List<List<String>> rows = new ArrayList<>();
        List<Integer> percentRows = new ArrayList<>();
        for (var line : table.rows()) {
            List<String> cells = new ArrayList<>();
            cells.add(line.label());
            for (var value : line.values()) cells.add(line.style().equals("group") ? "" : value == null ? "–" : value.toPlainString());
            if (line.format().equals("percent")) percentRows.add(rows.size());
            rows.add(cells);
        }
        var now = calendar.now();
        var bytes = percentStyle(excel.write("전체 수익 구조", headers, rows, actor.userId(), now), rows, percentRows, headers.size());
        if (mapper.insertAccess(actor.userId(), "전체 수익 구조 " + result.periodStart() + "–" + result.periodEnd()) != 1)
            throw new IllegalStateException("다운로드 접근 기록을 저장하지 못했습니다.");
        return new ExcelFile(bytes, excel.fileName("전체 수익 구조", result.periodStart() + "–" + result.periodEnd(), now), rows.size());
    }
    /** 공통 부품은 소수가 섞인 열을 모두 0.00으로 보이므로, 금액 줄은 #,##0, 비율 줄만 0.0으로 다시 맞춘다. */
    private static byte[] percentStyle(byte[] bytes, List<List<String>> rows, List<Integer> percentRows, int width) {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes)); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.getSheetAt(0);
            var integer = workbook.createCellStyle();
            integer.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            var percent = workbook.createCellStyle();
            percent.setDataFormat(workbook.createDataFormat().getFormat("0.0"));
            for (int r = 0; r < rows.size(); r++) {
                var row = sheet.getRow(r + 3);
                if (row == null) continue;
                for (int c = 1; c < width; c++) {
                    var cell = row.getCell(c);
                    if (cell == null || cell.getCellType() != org.apache.poi.ss.usermodel.CellType.NUMERIC) continue;
                    cell.setCellStyle(percentRows.contains(r) ? percent : integer);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
