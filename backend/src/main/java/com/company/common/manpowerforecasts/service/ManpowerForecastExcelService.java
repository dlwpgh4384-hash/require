package com.company.common.manpowerforecasts.service;

import com.company.common.manpowerforecasts.dto.ManpowerForecastContracts.*;
import com.company.common.manpowerforecasts.mapper.ManpowerForecastMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.support.service.ExcelExportService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManpowerForecastExcelService {
    private final CommonManpowerForecastService forecast;
    private final ManpowerForecastMapper mapper;
    private final ExcelExportService excel;
    private final CurrentBusinessActor actor;
    private final BusinessCalendar calendar;

    public ManpowerForecastExcelService(CommonManpowerForecastService forecast, ManpowerForecastMapper mapper,
        ExcelExportService excel, CurrentBusinessActor actor, BusinessCalendar calendar) {
        this.forecast = forecast;
        this.mapper = mapper;
        this.excel = excel;
        this.actor = actor;
        this.calendar = calendar;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Workbook getCommonManpowerForecastsExcel(Integer year) {
        var result = forecast.getCommonManpowerForecasts(year);
        var headers = new ArrayList<String>();
        headers.add("구분");
        result.months().forEach(m -> headers.add(m.month()));
        var rows = List.of(row("필요 인력", result, Month::demand),
            row("가용 인력", result, Month::supply), row("차이", result, Month::difference));
        var now = calendar.now();
        var bytes = oneDecimal(excel.write("인력 수요 예측", headers, rows, actor.userId(), now));
        String period = result.months().isEmpty() ? year.toString()
            : result.months().get(0).month() + "~" + result.months().get(result.months().size() - 1).month();
        String name = excel.fileName("인력 수요 예측", period, now);
        mapper.insertAccess(actor.userId(), rows.size());
        return new Workbook(bytes, name, rows.size());
    }

    private static List<String> row(String label, Result result, Function<Month, BigDecimal> value) {
        var cells = new ArrayList<String>();
        cells.add(label);
        result.months().forEach(m -> cells.add(value.apply(m).setScale(1, RoundingMode.HALF_UP).toPlainString()));
        return cells;
    }

    // 공통 엑셀의 전역 두 자리 형식은 보존하고 이 파일의 숫자 칸만 화면과 같은 한 자리로 보인다.
    private static byte[] oneDecimal(byte[] bytes) {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes)); var out = new ByteArrayOutputStream()) {
            var style = book.createCellStyle();
            style.setDataFormat(book.createDataFormat().getFormat("#,##0.0"));
            var sheet = book.getSheetAt(0);
            for (int r = 3; r <= 5; r++) {
                var row = sheet.getRow(r);
                for (int c = 1; c < row.getLastCellNum(); c++) row.getCell(c).setCellStyle(style);
            }
            book.write(out);
            return out.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
