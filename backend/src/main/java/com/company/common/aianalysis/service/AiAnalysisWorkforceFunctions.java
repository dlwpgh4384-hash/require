package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.airecommendations.service.AiRecommendationCodes;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.codegroups.dto.CodeContracts;
import com.company.common.dashboard.service.DashboardUtilizationService;
import com.company.common.manpowerboard.dto.ManpowerBoardContracts;
import com.company.common.manpowerboard.service.CommonManpowerBoardService;
import com.company.common.manpowerforecasts.service.CommonManpowerForecastService;
import com.company.common.salespipeline.dto.SalesPipelineContracts.*;
import com.company.common.salespipeline.mapper.SalesPipelineRows.MonthWindow;
import com.company.common.salespipeline.service.SalesPipelineForecastService;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.withdrawals.dto.WithdrawalContracts;
import com.company.common.withdrawals.service.CommonWithdrawalService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;
import static com.company.common.aianalysis.service.AiAnalysisInterpreter.has;
import static com.company.common.aianalysis.service.AiAnalysisTables.*;

/** F-09 인력 현황, F-10 철수 예정, P-04 인력 과부족(인력 수요 예측과 같은 계산·반올림). */
@Component
public class AiAnalysisWorkforceFunctions implements AiAnalysisFunction {
    private static final BigDecimal NEAR_ZERO = new BigDecimal("0.05");
    private final SalesPipelineForecastService forecast;
    private final CommonManpowerBoardService board;
    private final DashboardUtilizationService utilization;
    private final CommonWithdrawalService withdrawals;
    private final AiRecommendationCodes codes;
    private final CalculationRuleService standards;
    private final BusinessCalendar calendar;
    private final AiAnalysisTables tables;
    public AiAnalysisWorkforceFunctions(SalesPipelineForecastService forecast, CommonManpowerBoardService board,
        DashboardUtilizationService utilization, CommonWithdrawalService withdrawals, AiRecommendationCodes codes,
        CalculationRuleService standards, BusinessCalendar calendar, AiAnalysisTables tables) {
        this.forecast = forecast; this.board = board; this.utilization = utilization; this.withdrawals = withdrawals;
        this.codes = codes; this.standards = standards; this.calendar = calendar; this.tables = tables;
    }
    public Set<String> functions() { return Set.of("F-09", "F-10", "P-04"); }
    public Result read(AiAnalysisInterpreter.Query query) {
        var job = code("JOB", query.question());
        return switch (query.function()) {
            case "P-04" -> shortage(query, job);
            case "F-10" -> withdrawal(query, job);
            default -> status(query, job, code("GRADE", query.question()));
        };
    }
    /** 직무·등급 이름(「백엔드」「BE」「백엔드 개발」). 둘 이상이면 답할 수 없음. */
    private CodeContracts.Item code(String group, String question) { return code(codes, group, question); }
    /** F-04 개인 손익도 같은 직무 고르기를 쓴다(「백엔드 적자 인력」 → BE). */
    static CodeContracts.Item code(AiRecommendationCodes codes, String group, String question) {
        var found = codes.selectable(group).stream().filter(c -> question.contains(c.codeName)
            || alias(c.codeName) != null && question.contains(alias(c.codeName))
            || !"AI".equals(c.code) && question.matches(".*(?<![A-Za-z])" + java.util.regex.Pattern.quote(c.code) + "(?![A-Za-z]).*")).toList();
        if (found.size() > 1) throw new NoAnswer(null);
        return found.isEmpty() ? null : found.get(0);
    }
    private static String alias(String name) {
        if (name == null) return null;
        String head = name.split("[ ·(/]")[0];
        return head.length() >= 3 && !head.equals(name) && head.matches("[가-힣]+") ? head : null;
    }

    // ---- P-04 ----
    private Result shortage(AiAnalysisInterpreter.Query query, CodeContracts.Item job) {
        var now = calendar.currentMonth();
        var first = YearMonth.parse(query.period().startMonth());
        var end = YearMonth.parse(query.period().endMonth());
        if (!query.periodGiven()) { first = now; end = now.plusMonths(2); }
        if (first.isBefore(now)) first = now;
        if (end.isBefore(first)) throw new NoAnswer("지난 달의 인력 과부족은 예측하지 않습니다. 인력 운영 보드에서 확인해 주세요.");
        var windows = new ArrayList<MonthWindow>();
        var months = new ArrayList<YearMonth>();
        for (var month = first; !month.isAfter(end); month = month.plusMonths(1)) {
            windows.add(new MonthWindow(calendar.toDatabaseMonth(month), month.atDay(1), month.atEndOfMonth()));
            months.add(month);
        }
        var basic = forecast.getWorkforceForecast(windows);
        var high = forecast.getWorkforceForecast(windows, BigDecimal.valueOf(100));
        var shortLimit = standards.effectiveStandard("SHORT_PERSON", now).value();
        var surplusLimit = standards.effectiveStandard("SURPLUS_PERSON", now).value();
        // 낮음 = 영업 기회 0%(가용 − 미충원), 기본 = 수주 확률, 높음 = 같은 계산에 확률 100%.
        var low = new ArrayList<BigDecimal>(); var base = new ArrayList<BigDecimal>(); var top = new ArrayList<BigDecimal>();
        for (int i = 0; i < basic.size(); i++) {
            var m = basic.get(i); var h = high.get(i);
            if (job == null) {
                low.add(CommonManpowerForecastService.monthDifference(m.supply(), m.unfilledDemand()));
                base.add(CommonManpowerForecastService.monthDifference(m.supply(), m.demand()));
                top.add(CommonManpowerForecastService.monthDifference(h.supply(), h.demand()));
            } else {
                var b = jobOf(m, job.code); var t = jobOf(h, job.code);
                low.add(CommonManpowerForecastService.jobDifference(b.supply(), b.unfilledDemand()));
                base.add(CommonManpowerForecastService.jobDifference(b.supply(), b.demand()));
                top.add(CommonManpowerForecastService.jobDifference(t.supply(), t.demand()));
            }
        }
        var columns = new ArrayList<Column>(List.of(Column.text("range", "범위")));
        months.forEach(m -> columns.add(Column.right(m.toString(), m.toString())));
        BiFunction<String, List<BigDecimal>, List<Object>> line = (name, values) -> {
            var row = new ArrayList<Object>(List.of(name));
            values.forEach(v -> row.add(person(v)));
            return row;
        };
        var rangeTones = new ArrayList<List<String>>();
        for (var values : List.of(low, base, top)) {
            var tone = new ArrayList<String>(); tone.add(null);
            values.forEach(v -> tone.add(v.compareTo(shortLimit.negate()) < 0 ? "bad" : null));
            rangeTones.add(tone);
        }
        // 시안 대기 인력 답: 범위 표·직무 표 모두 제목 없음.
        var range = AiAnalysisTables.table(null, columns, List.of(line.apply("낮음 · 영업 기회를 하나도 못 따면", low),
            line.apply("기본 · 수주 확률만큼", base), line.apply("높음 · 영업 기회를 모두 따면", top)), rangeTones, null);
        var jobColumns = new ArrayList<Column>(List.of(Column.text("job", "직무")));
        months.forEach(m -> jobColumns.add(Column.right(m.toString(), m.toString())));
        var jobRows = new ArrayList<List<Object>>();
        var jobTones = new ArrayList<List<String>>();
        var jobNames = basic.isEmpty() ? List.<WorkforceJob>of() : basic.get(0).jobs();
        for (var j : jobNames) {
            if (job != null && !job.code.equals(j.jobCode())) continue;
            var values = basic.stream().map(m -> {
                var v = jobOf(m, j.jobCode());
                return CommonManpowerForecastService.jobDifference(v.supply(), v.demand());
            }).toList();
            // 시안: 모든 달이 0.05 미만인 직무 줄은 뺀다.
            if (values.stream().allMatch(v -> v.abs().compareTo(NEAR_ZERO) < 0)) continue;
            var row = new ArrayList<Object>(List.of(j.jobName()));
            var tone = new ArrayList<String>(); tone.add(null);
            for (var v : values) {
                row.add(person(v));
                tone.add(v.compareTo(shortLimit.negate()) < 0 ? "bad" : v.compareTo(surplusLimit) > 0 ? "warn" : null);
            }
            jobRows.add(row); jobTones.add(tone);
        }
        var total = new ArrayList<Object>(List.of(job == null ? "전체" : job.codeName));
        base.forEach(v -> total.add(person(v)));
        var jobs = AiAnalysisTables.table(null, jobColumns, jobRows, jobTones, total);
        String scope = has(query.question(), "다음 분기", "다음분기") ? "다음 분기(" + first + "~" + end + ")" : first + "~" + end;
        String conclusion = scope + " " + (job == null ? "" : job.codeName + " ") + "남는 인원(가용 − 필요)은 "
            + String.join(" · ", java.util.stream.IntStream.range(0, months.size())
                .mapToObj(i -> months.get(i).getMonthValue() + "월 " + bold(person(base.get(i)))).toList())
            + "으로 예상됩니다(기본).";
        var period = span(first, end);
        return new Result(conclusion, List.of(range, jobs), null, List.of(assumption(now)),
            used(USED_SHORTAGE, period), now.toString(), false, true,
            List.of(tables.open("UI-17", period), tables.open("UI-28", period)));
    }
    /** 시안 대기 인력 답의 「사용한 데이터」 글. */
    static final String USED_SHORTAGE = "투입 이력, 근무 상태, 영업 파이프라인, 인원 계획";
    private String assumption(YearMonth month) {
        String extension = "연장 가능성 높음 " + percent("EXT_PRBL_H", month) + "·보통 " + percent("EXT_PRBL_M", month)
            + "·낮음 " + percent("EXT_PRBL_L", month);
        // 영업 파이프라인 예측은 기회마다 넣은 수주 확률(WIN_PRBL_RT)을 쓴다(단계 기본 확률이 아님).
        return extension + ", 영업 기회는 기회별 수주 확률만큼 사람이 필요하다고 봄(범위의 낮음은 0%, 높음은 100%), "
            + "진행 프로젝트 미충원 포함. 인력 수요 예측 화면의 「차이」와 같은 숫자입니다.";
    }
    private String percent(String code, YearMonth month) { return number(code, month) + "%"; }
    private String number(String code, YearMonth month) {
        var value = standards.effectiveStandard(code, month).value();
        return value == null ? "–" : value.stripTrailingZeros().toPlainString();
    }
    private static WorkforceJob jobOf(WorkforceMonth month, String code) {
        return month.jobs().stream().filter(j -> j.jobCode().equals(code)).findFirst()
            .orElse(new WorkforceJob(code, code, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }
    /** 시안 f(): 0.05 미만이면 「맞음」, 아니면 「n.n명 부족/남음」. */
    static String person(BigDecimal value) {
        if (value.abs().compareTo(NEAR_ZERO) < 0) return "맞음";
        return value.abs().setScale(1, RoundingMode.HALF_UP).toPlainString() + "명 " + (value.signum() < 0 ? "부족" : "남음");
    }

    // ---- F-10 ----
    private Result withdrawal(AiAnalysisInterpreter.Query query, CodeContracts.Item job) {
        var first = YearMonth.parse(query.period().startMonth());
        var end = YearMonth.parse(query.period().endMonth());
        var rows = new ArrayList<WithdrawalContracts.Row>();
        for (int year = first.getYear(); year <= end.getYear(); year++) {
            var filter = new LinkedHashMap<String, Object>();
            filter.put("year", year);
            if (job != null) filter.put("jobCode", job.code);
            rows.addAll(withdrawals.getCommonWithdrawals(tables.filter(filter, WithdrawalContracts.Filter.class)).items().stream()
                .filter(r -> r.endDate() != null && !r.endDate().isBefore(first.atDay(1)) && !r.endDate().isAfter(end.atEndOfMonth())).toList());
        }
        var none = withdrawals.withoutNextAssignment(rows);
        boolean onlyNone = has(query.question(), "다음 배정 없", "배정 없는", "갈 곳 없", "갈 데 없");
        var shown = onlyNone ? none : rows;
        var table = AiAnalysisTables.table(null, List.of(Column.text("name", "이름"), Column.text("projectName", "프로젝트"),
            Column.text("jobName", "직무"), Column.date("endDate", "철수일"), Column.count("remainingDays", "남은 일수"),
            Column.text("extension", "연장 가능성"), Column.text("next", "다음 배정")),
            shown.stream().map(r -> Arrays.<Object>asList(label(r.name(), r.managementNumber()), r.projectName(), r.jobName(), r.endDate(),
                r.remainingDays(), r.extensionPossibilityName(), next(r, none))).toList(), null, null);
        var period = span(first, end);
        String who = (job == null ? "" : job.codeName + " ") + (onlyNone ? "다음 배정 없는 철수 예정자" : "철수 예정자");
        String conclusion = period(period) + " " + who + "는 " + bold(people(shown.size())) + "입니다."
            + (!onlyNone && !rows.isEmpty() ? " 그중 다음 배정 없음은 " + bold(people(none.size())) + "입니다." : "");
        return new Result(conclusion, List.of(table), null, List.of("철수 리스트와 같은 목록입니다.",
            "다음 배정 없음은 철수 리스트 안내 띠와 같이 3개월 안 철수 예정, 연장 가능성 높음이 아니고 가배정·배정 후보·확정된 다음 투입이 없는 사람입니다."),
            used(tables.screen("UI-05"), period), end.toString(), false, false, List.of(tables.open("UI-05", period)));
    }
    private static String next(WithdrawalContracts.Row row, List<WithdrawalContracts.Row> none) {
        var next = row.nextAssignment();
        if (next != null && next.nextProjectName() != null) return next.nextProjectName();
        if (next != null && next.opportunityName() != null) return next.opportunityName() + (next.assignmentCandidate() ? "(배정 후보)" : "(가배정)");
        return none.contains(row) ? "다음 배정 없음" : "–";
    }
    static String label(String name, String number) {
        if (name == null) return number;
        return number == null || number.isBlank() ? name : name + "(" + number + ")";
    }

    // ---- F-09 ----
    private Result status(AiAnalysisInterpreter.Query query, CodeContracts.Item job, CodeContracts.Item grade) {
        var first = YearMonth.parse(query.period().startMonth());
        var end = YearMonth.parse(query.period().endMonth());
        var rows = new ArrayList<List<Object>>();
        Integer lastIdle = null; BigDecimal lastRate = null, lastTarget = null, lastMm = null;
        for (int year = first.getYear(); year <= end.getYear(); year++) {
            var filter = new LinkedHashMap<String, Object>();
            filter.put("year", year);
            if (job != null) filter.put("jobCode", job.code);
            if (grade != null) filter.put("gradeCode", grade.code);
            var result = board.getCommonManpowerBoard(tables.filter(filter, ManpowerBoardContracts.Filter.class));
            for (var month : result.monthTotals()) {
                if (month.month().compareTo(first.toString()) < 0 || month.month().compareTo(end.toString()) > 0) continue;
                var rate = utilization.read(YearMonth.parse(month.month()));
                rows.add(Arrays.<Object>asList(month.month(), month.idleCount(), month.availableMm(), rate.rate(), rate.target()));
                lastIdle = month.idleCount(); lastMm = month.availableMm(); lastRate = rate.rate(); lastTarget = rate.target();
            }
        }
        if (rows.isEmpty()) throw new NoAnswer(null);
        var table = AiAnalysisTables.table(null, List.of(Column.month("month", "연월"), Column.count("idleCount", "대기 인원"),
            Column.mm("availableMm", "남는 MM"), Column.rate("rate", "회사 가동률"), Column.rate("target", "목표 가동률")), rows, null, null);
        boolean idle = has(query.question(), "대기", "비어", "남는");
        boolean rateAsked = has(query.question(), "가동률", "가득률");
        String target = (job == null ? "" : job.codeName + " ") + (grade == null ? "" : grade.codeName + " ");
        var parts = new ArrayList<String>();
        if (idle || !rateAsked) parts.add(target + "대기 인원은 " + (lastIdle == null ? "–" : bold(people(lastIdle)))
            + (lastMm == null ? "" : "(남는 MM " + mm(lastMm) + ")"));
        if (rateAsked || !idle) parts.add("회사 가동률은 " + bold(rate(lastRate)) + (lastTarget == null ? "" : "(목표 " + rate(lastTarget) + ")"));
        var period = span(first, end);
        return new Result(end + " " + String.join(", ", parts) + "입니다.", List.of(table), null,
            List.of("대기 인원은 인력 운영 보드의 비어 있는 인원(휴직·퇴사·입사 전 제외), 가동률은 대시보드 가동률과 같습니다.",
                "지난 달의 대기 인원은 인력 운영 보드처럼 보이지 않습니다."),
            used(tables.screen("UI-28"), period), end.toString(), false, false, List.of(tables.open("UI-28", period)));
    }
}
