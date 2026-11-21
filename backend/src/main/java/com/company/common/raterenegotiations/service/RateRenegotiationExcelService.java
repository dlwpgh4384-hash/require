package com.company.common.raterenegotiations.service;

import com.company.common.raterenegotiations.dto.RateRenegotiationContracts.*;
import com.company.common.raterenegotiations.mapper.RateRenegotiationMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.support.service.ExcelExportService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RateRenegotiationExcelService {
    private final CommonRateRenegotiationService service;
    private final RateRenegotiationMapper mapper;
    private final ExcelExportService excel;
    private final CurrentBusinessActor actor;
    private final BusinessCalendar calendar;

    public RateRenegotiationExcelService(CommonRateRenegotiationService service, RateRenegotiationMapper mapper,
        ExcelExportService excel, CurrentBusinessActor actor, BusinessCalendar calendar) {
        this.service = service;
        this.mapper = mapper;
        this.excel = excel;
        this.actor = actor;
        this.calendar = calendar;
    }
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ExcelFile getCommonRateRenegotiationsExcel(Filter filter, List<String> requestedColumns) {
        var columns = RateRenegotiationColumnPolicy.validate(requestedColumns);
        var result = service.getCommonRateRenegotiations(filter);
        var rows = result.items().stream().map(row -> columns.stream()
            .map(key -> RateRenegotiationColumnPolicy.value(row, key)).toList()).toList();
        var now = calendar.now();
        var bytes = excel.write("단가 재협상 대상", columns.stream().map(key -> RateRenegotiationColumnPolicy.label(key,
            result.appliedIncreaseRate(), result.summary().targetProfitRate())).toList(),
            rows, actor.userId(), now);
        if (mapper.insertAccess(actor.userId(), "단가 재협상 대상 " + result.baseYearMonth() + " " + result.items().size() + "건") != 1) {
            throw new IllegalStateException("다운로드 접근 기록을 저장하지 못했습니다.");
        }
        return new ExcelFile(bytes, excel.fileName("단가 재협상 대상", result.baseYearMonth(), now), result.items().size());
    }
}
