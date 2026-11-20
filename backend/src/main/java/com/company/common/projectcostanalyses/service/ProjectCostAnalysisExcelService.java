package com.company.common.projectcostanalyses.service;

import com.company.common.projectcostanalyses.dto.ProjectCostAnalysisContracts.*;
import com.company.common.projectcostanalyses.mapper.ProjectCostAnalysisMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.support.service.ExcelExportService;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectCostAnalysisExcelService {
    public static final List<String> HEADINGS = List.of("프로젝트", "유형", "상태", "처음 계약금액", "현재 계약금액",
        "계획 MM", "실제 MM", "누적 매출", "누적 원가", "누적 이익", "누적 마진율", "예상 최종 마진율",
        "월간 마진율", "목표 대비", "예산 소진율", "MM당 단가", "MM당 원가", "MM당 이익", "무상지원 MM",
        "무상지원 원가", "연구개발비 누계", "경보");
    private final CommonProjectCostAnalysisService service;
    private final ProjectCostAnalysisMapper mapper;
    private final ExcelExportService excel;
    private final CurrentBusinessActor actor;
    private final BusinessCalendar calendar;
    public ProjectCostAnalysisExcelService(CommonProjectCostAnalysisService service, ProjectCostAnalysisMapper mapper,
        ExcelExportService excel, CurrentBusinessActor actor, BusinessCalendar calendar) {
        this.service = service;
        this.mapper = mapper;
        this.excel = excel;
        this.actor = actor;
        this.calendar = calendar;
    }
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ExcelFile getCommonProjectCostAnalysesExcel(Filter filter) {
        var result = service.getCommonProjectCostAnalyses(filter);
        List<List<String>> rows = result.items().stream().map(ProjectCostAnalysisExcelService::values).toList();
        var now = calendar.now();
        byte[] bytes = excel.write("프로젝트 원가 분석", HEADINGS, rows, actor.userId(), now);
        if (mapper.insertAccess(actor.userId(), "프로젝트 원가 분석 " + filter.year() + "년 "
            + filter.baseMonth() + "월 " + result.totalCount() + "건") != 1) {
            throw new IllegalStateException("다운로드 접근 기록을 저장하지 못했습니다.");
        }
        return new ExcelFile(bytes, excel.fileName("프로젝트 원가 분석", filter.year() + "년", now), result.totalCount());
    }
    private static List<String> values(CommonProjectCostAnalysisRow r) {
        return Arrays.asList(r.projectName(), r.contractTypeName(), r.projectStatus(), r.originalContractAmount(),
            r.contractAmount(), r.plannedMm() == null && !"IN".equals(r.contractTypeCode()) ? "계획 없음" : r.plannedMm(),
            r.actualMm(), r.cumulativeRevenue(), r.totalCost(), r.cumulativeProfit(), r.cumulativeMarginRate(),
            r.missingMonthlyCost() ? "단가 없음" : r.expectedFinalMarginRate(), r.monthlyMarginRate(), r.targetDifference(),
            r.budgetUsageRate(), r.unitPricePerMm(), r.costPerMm(), r.profitPerMm(), r.freeSupportMm(),
            r.freeSupportCost(), r.researchCost(), String.join(" · ", r.alerts())).stream()
            .map(value -> value == null ? "" : value instanceof java.math.BigDecimal number ? number.toPlainString() : value.toString()).toList();
    }
}
