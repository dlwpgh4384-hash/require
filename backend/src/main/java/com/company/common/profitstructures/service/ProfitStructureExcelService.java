package com.company.common.profitstructures.service;

import com.company.common.profitstructures.dto.ProfitStructureContracts.*;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.support.service.ExcelExportService;
import java.util.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.company.common.profitstructures.service.ProfitStructureColumnPolicy.*;

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
    public ExcelFile getCommonProfitStructuresExcel(Filter filter, List<String> requested) {
        var selected = validate(requested);
        var result = service.getCommonProfitStructures(filter).data();
        var columns = selected.stream().filter(key -> (result.compositionVisible() || !COMPOSITION.contains(key))
            && (result.referenceRevenueVisible() || !key.equals("referenceRevenue"))).toList();
        List<String> headers = new ArrayList<>();
        List<Integer> textColumns = new ArrayList<>();
        for (var key : columns) {
            if (key.equals("period") || COMPOSITION.contains(key)) textColumns.add(headers.size());
            if (COMPOSITION.contains(key)) for (var suffix : List.of("이름", "매출", "비중", "이익률")) headers.add(LABELS.get(key) + " " + suffix);
            else headers.add(LABELS.get(key));
        }
        List<List<String>> rows = new ArrayList<>();
        for (var row : result.numericRows()) {
            int count = columns.stream().filter(COMPOSITION::contains).mapToInt(key -> composition(row, key).size()).max().orElse(1);
            for (int n = 0; n < Math.max(1, count); n++) {
                List<String> cells = new ArrayList<>();
                for (var key : columns) {
                    if (!COMPOSITION.contains(key)) cells.add(n == 0 ? decimal(value(row, key)) : "");
                    else {
                        var group = composition(row, key);
                        if (n < group.size()) {
                            var item = group.get(n);
                            cells.add(item.name()); cells.add(decimal(item.revenue())); cells.add(decimal(item.share())); cells.add(decimal(item.profitRate()));
                        } else cells.addAll(List.of("", "", "", ""));
                    }
                }
                rows.add(cells);
            }
        }
        var now = calendar.now();
        var bytes = preserveText(excel.write("전체 수익 구조", headers, rows, actor.userId(), now), rows, textColumns);
        if (mapper.insertAccess(actor.userId(), "전체 수익 구조 " + result.periodStart() + "–" + result.periodEnd()) != 1)
            throw new IllegalStateException("다운로드 접근 기록을 저장하지 못했습니다.");
        return new ExcelFile(bytes, excel.fileName("전체 수익 구조", result.periodStart() + "–" + result.periodEnd(), now), result.numericRows().size());
    }
    private static byte[] preserveText(byte[] bytes, List<List<String>> rows, List<Integer> columns) {
        if (columns.isEmpty()) return bytes;
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes)); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.getSheetAt(0);
            for (int r = 0; r < rows.size(); r++) for (int c : columns)
                sheet.getRow(r + 3).getCell(c).setCellValue(rows.get(r).get(c));
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
