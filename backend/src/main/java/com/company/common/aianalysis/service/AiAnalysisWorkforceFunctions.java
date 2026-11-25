package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.salespipeline.service.SalesPipelineForecastService;
import com.company.common.salespipeline.mapper.SalesPipelineRows.MonthWindow;
import com.company.common.manpowerboard.service.CommonManpowerBoardService;
import com.company.common.manpowerboard.dto.ManpowerBoardContracts;
import com.company.common.dashboard.service.DashboardUtilizationService;
import com.company.common.withdrawals.service.CommonWithdrawalService;
import com.company.common.withdrawals.dto.WithdrawalContracts;
import com.company.common.airecommendations.service.AiRecommendationCodes;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisWorkforceFunctions implements AiAnalysisFunction {
    private final SalesPipelineForecastService forecast;
    private final CommonManpowerBoardService board;
    private final DashboardUtilizationService utilization;
    private final CommonWithdrawalService withdrawals;
    private final AiRecommendationCodes codes;
    private final AiAnalysisTables tables;
    public AiAnalysisWorkforceFunctions(SalesPipelineForecastService forecast, CommonManpowerBoardService board,
        DashboardUtilizationService utilization, CommonWithdrawalService withdrawals,
        AiRecommendationCodes codes, AiAnalysisTables tables) {
        this.forecast = forecast; this.board = board; this.utilization = utilization;
        this.withdrawals = withdrawals; this.codes = codes; this.tables = tables;
    }
    public Set<String> functions() { return Set.of("F-09", "F-10", "P-04"); }
    public Result read(AiAnalysisInterpreter.Query query) {
        var first = YearMonth.parse(query.period().startMonth());
        var end = YearMonth.parse(query.period().endMonth());
        var jobs = codes.selectable("JOB").stream().filter(c -> query.question().contains(c.codeName)).toList();
        var grades = codes.selectable("GRADE").stream().filter(c -> query.question().contains(c.codeName)).toList();
        if (jobs.size() > 1 || grades.size() > 1) return null;
        var filter = new LinkedHashMap<String, Object>();
        if (jobs.size() == 1) filter.put("jobCode", jobs.get(0).code);
        if (grades.size() == 1) filter.put("gradeCode", grades.get(0).code);
        var rows = new ArrayList<Object>();
        if ("P-04".equals(query.function())) {
            var windows = new ArrayList<MonthWindow>();
            for (var month = first; !month.isAfter(end); month = month.plusMonths(1))
                windows.add(new MonthWindow(month.toString().replace("-", ""), month.atDay(1), month.atEndOfMonth()));
            var basic = forecast.getWorkforceForecast(windows);
            var high = forecast.getWorkforceForecast(windows, BigDecimal.valueOf(100));
            for (int i = 0; i < basic.size(); i++) {
                var value = basic.get(i);
                var highValue = high.get(i);
                for (var job : value.jobs()) {
                    if (jobs.size() == 1 && !jobs.get(0).code.equals(job.jobCode())) continue;
                    var matching = highValue.jobs().stream().filter(j -> j.jobCode().equals(job.jobCode())).findFirst().orElseThrow();
                    rows.add(Map.of("month", value.month(), "job", job.jobName(), "low", job.unfilledDemand(),
                        "basic", job.demand(), "high", matching.demand(), "supply", job.supply()));
                }
            }
            return new Result(tables.table(rows, "month", "연월", "job", "직무", "low", "낮음 MM", "basic", "기본 MM",
                "high", "높음 MM", "supply", "공급 MM"), query.period(), true, "UI-17",
                List.of("낮음은 미충원만, 기본은 원천 수주 확률, 높음은 동일 계산의 수주 확률 100%입니다."));
        }
        for (int year = first.getYear(); year <= end.getYear(); year++) {
            filter.put("year", year);
            if ("F-10".equals(query.function())) {
                var result = withdrawals.getCommonWithdrawals(tables.filter(filter, WithdrawalContracts.Filter.class));
                rows.addAll(result.items().stream().filter(r -> !r.endDate().isBefore(first.atDay(1))
                    && !r.endDate().isAfter(end.atEndOfMonth())).filter(r -> !query.question().contains("다음 배정 없음")
                    || r.nextAssignment() != null && !r.nextAssignment().hasTentativeAssignment()
                        && !r.nextAssignment().assignmentCandidate() && r.nextAssignment().nextProjectName() == null).toList());
            } else {
                var result = board.getCommonManpowerBoard(tables.filter(filter, ManpowerBoardContracts.Filter.class));
                for (var month : result.monthTotals()) {
                    if (month.month().compareTo(first.toString()) < 0 || month.month().compareTo(end.toString()) > 0) continue;
                    var rate = utilization.read(YearMonth.parse(month.month()));
                    var row = new LinkedHashMap<String, Object>();
                    row.put("month", month.month()); row.put("idleCount", month.idleCount());
                    row.put("availableMm", month.availableMm()); row.put("rate", rate.rate()); rows.add(row);
                }
            }
        }
        return new Result("F-10".equals(query.function())
            ? tables.table(rows, "name", "이름", "projectName", "프로젝트", "endDate", "철수일", "remainingDays", "남은 일수")
            : tables.table(rows, "month", "연월", "idleCount", "대기 인원", "availableMm", "남는 MM", "rate", "회사 가동률"),
            query.period(), true, "F-10".equals(query.function()) ? "UI-05" : "UI-28", List.of());
    }
}
