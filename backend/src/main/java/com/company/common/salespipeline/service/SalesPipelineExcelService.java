package com.company.common.salespipeline.service;

import com.company.common.salespipeline.dto.SalesPipelineContracts.Workbook;
import com.company.common.salespipeline.mapper.SalesPipelineMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.support.service.ExcelExportService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesPipelineExcelService {
    private final SalesPipelineForecastService forecast;
    private final SalesPipelineMapper mapper;
    private final ExcelExportService excel;
    private final CurrentBusinessActor actor;
    private final BusinessCalendar calendar;

    public SalesPipelineExcelService(SalesPipelineForecastService forecast, SalesPipelineMapper mapper,
        ExcelExportService excel, CurrentBusinessActor actor, BusinessCalendar calendar) {
        this.forecast = forecast;
        this.mapper = mapper;
        this.excel = excel;
        this.actor = actor;
        this.calendar = calendar;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Workbook getCommonSalesPipelineForecastExcel() {
        var result = forecast.getCommonSalesPipelineForecast();
        var rows = result.months().stream().map(m -> List.of(m.month(), amount(m.projectRevenue()),
            amount(m.weightedOpportunityRevenue()), amount(m.allWonRevenue()), mm(m.demand()), mm(m.supply()))).toList();
        var now = calendar.now();
        String title = "매출·인력 예측";
        byte[] bytes = excel.write(title,
            List.of("년월", "진행 프로젝트 매출", "확률 반영 기회 매출", "모두 따면 매출", "수요 MM", "공급 MM"),
            rows, actor.userId(), now);
        mapper.insertAccess(actor.userId(), rows.size());
        String period = result.months().get(0).month() + "~" + result.months().get(result.months().size() - 1).month();
        // 공통 0장: 파일 이름은 「화면이름_기간_다운로드일시.xlsx」(화면 이름 영업 파이프라인). 시트 제목은 탭 이름 그대로.
        return new Workbook(bytes, excel.fileName("영업 파이프라인", period, now), rows.size());
    }

    /** 금액은 원 단위(식-00), MM은 소수 둘째 자리(식-58) 반올림해 숫자 칸으로 쓴다. */
    private static String amount(BigDecimal value) { return SalesPipelineForecastService.won(value).toPlainString(); }
    private static String mm(BigDecimal value) {
        return SalesPipelineForecastService.mm(value).stripTrailingZeros().toPlainString();
    }
}
