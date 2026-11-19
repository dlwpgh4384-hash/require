package com.company.common.personalcostprofits.service;

import com.company.common.personalcostprofits.dto.PersonalCostProfitContracts.*;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.support.service.ExcelExportService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalCostProfitExcelService {
    private final CommonPersonalCostProfitService service;
    private final PersonalCostProfitMapper mapper;
    private final ExcelExportService excel;
    private final CurrentBusinessActor actor;
    private final BusinessCalendar calendar;

    public PersonalCostProfitExcelService(CommonPersonalCostProfitService service, PersonalCostProfitMapper mapper,
        ExcelExportService excel, CurrentBusinessActor actor, BusinessCalendar calendar) {
        this.service = service;
        this.mapper = mapper;
        this.excel = excel;
        this.actor = actor;
        this.calendar = calendar;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ExcelFile getCommonPersonalCostProfitsExcel(Filter filter) {
        var calculation = service.calculate(filter);
        var columns = calculation.columns();
        List<List<String>> rows = new ArrayList<>();
        for (var item : calculation.result().items()) rows.add(values(item, columns));
        if (CommonPersonalCostProfitService.number(calculation.leaveTotals(), "totalCost").signum() != 0) {
            var leave = new java.util.LinkedHashMap<>(calculation.leaveTotals());
            leave.put("developerName", "대기 원가(휴직 비용)");
            rows.add(values(leave, columns));
        }
        var totals = new java.util.LinkedHashMap<>(calculation.result().totals());
        totals.put("developerName", "합계");
        rows.add(values(totals, columns));
        String title = Boolean.FALSE.equals(filter.includeTurnkey()) ? "턴키단가미포함" : "개인 원가·수익표";
        var now = calendar.now();
        byte[] bytes = excel.write(title, columns.stream().map(key -> PersonalCostProfitColumnPolicy.LABELS
            .get(PersonalCostProfitColumnPolicy.KEYS.indexOf(key))).toList(), rows, actor.userId(), now);
        if (mapper.insertAccess(actor.userId(), "개인 원가·수익표 " + filter.year() + "년 "
            + calculation.result().items().size() + "건") != 1) {
            throw new IllegalStateException("다운로드 접근 기록을 저장하지 못했습니다.");
        }
        return new ExcelFile(bytes, excel.fileName("개인 원가·수익표", filter.year() + "년", now),
            calculation.result().items().size());
    }

    private static List<String> values(Map<String, Object> row, List<String> columns) {
        return columns.stream().map(key -> {
            Object value = row.get(key);
            if (value == null) return "profitRate".equals(key) ? "–" : "";
            if (value instanceof java.math.BigDecimal amount) return amount.toPlainString();
            return value.toString();
        }).toList();
    }
}
