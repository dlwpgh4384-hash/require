package com.company.common.projectcostanalyses.service;

import com.company.common.projectcostanalyses.dto.ProjectCostAnalysisContracts.*;
import com.company.common.projectcostanalyses.mapper.ProjectCostAnalysisMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.support.service.ExcelExportService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectCostAnalysisExcelService {
    /** 화면 목록 열(시안 19열)과 같은 이름·순서. */
    public static final List<String> HEADINGS = List.of("프로젝트", "유형", "상태", "원 계약금액", "현재 계약금액",
        "계획 MM", "실제 MM", "누적 매출", "누적 원가", "이익", "누적 마진율", "예상 최종·월간 마진율",
        "목표 대비", "예산 소진율", "MM당 단가", "MM당 원가", "MM당 이익", "무상지원 MM·원가", "경보");
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
        String period = filter.year() + "년 " + filter.baseMonth() + "월";
        if (mapper.insertAccess(actor.userId(), "프로젝트 원가 분석 " + period + " " + result.totalCount() + "건") != 1) {
            throw new IllegalStateException("다운로드 접근 기록을 저장하지 못했습니다.");
        }
        return new ExcelFile(bytes, excel.fileName("프로젝트 원가 분석", period, now), result.totalCount());
    }
    /** 화면과 같은 글자: 금액은 원, MM은 소수 둘째 자리, 비율은 소수 한 자리, 빈 값은 「–」. */
    private static List<String> values(CommonProjectCostAnalysisRow r) {
        boolean internal = "IN".equals(r.contractTypeCode()), turnkey = "TK".equals(r.contractTypeCode());
        String margin = turnkey ? pct(r.expectedFinalMarginRate()) + (r.missingMonthlyCost() ? " 단가 없음" : "")
            : r.monthlyMarginRate() == null ? "–" : pct(r.monthlyMarginRate()) + " 월간";
        String free = !internal && r.freeSupportMm() != null && r.freeSupportMm().signum() != 0
            ? mm(r.freeSupportMm()) + " · " + won(r.freeSupportCost()) : "–";
        return Arrays.asList(r.projectName(), r.contractTypeName(), r.projectStatus(),
            internal ? "–" : won(r.originalContractAmount()), internal ? "–" : won(r.contractAmount()),
            internal ? "–" : r.plannedMm() == null ? "계획 없음" : mm(r.plannedMm()), mm(r.actualMm()),
            internal ? "–" : won(r.cumulativeRevenue()), won(r.totalCost()) + (internal ? " 연구개발비" : ""),
            internal ? "–" : won(r.cumulativeProfit()), pct(r.cumulativeMarginRate()), margin,
            r.targetDifference() == null ? "–" : pp(r.targetDifference()), pct(r.budgetUsageRate()),
            internal ? "–" : won(r.unitPricePerMm()), internal ? "–" : won(r.costPerMm()),
            internal ? "–" : won(r.profitPerMm()), free,
            r.alerts().isEmpty() ? "–" : String.join(" · ", r.alerts())
                + (r.primaryCauseName() == null ? "" : " / 주원인: " + r.primaryCauseName() + " (" + won(r.primaryCauseAmount()) + ")"));
    }
    private static String won(BigDecimal value) {
        return value == null ? "–" : value.setScale(0, RoundingMode.DOWN).toPlainString();
    }
    private static String mm(BigDecimal value) {
        return value == null ? "–" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    private static String pct(BigDecimal value) {
        return value == null ? "–" : value.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }
    private static String pp(BigDecimal value) {
        return (value.signum() < 0 ? "" : "+") + value.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%p";
    }
}
