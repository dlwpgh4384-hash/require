package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.aianalysis.mapper.AiAnalysisMapper;
import com.company.common.airecommendations.service.AiRecommendationCodes;
import com.company.common.personalcostprofits.dto.PersonalCostProfitContracts;
import com.company.common.personalcostprofits.service.CommonPersonalCostProfitService;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import static com.company.common.aianalysis.service.AiAnalysisInterpreter.has;
import static com.company.common.aianalysis.service.AiAnalysisTables.*;

/** F-04 개인 손익·적자 인력(개인 원가·수익표 계산 그대로), 급여 질문도 여기서 답한다. */
@Component
public class AiAnalysisPersonalFunction implements AiAnalysisFunction {
    private static final Pattern THRESHOLD = Pattern.compile("이익률\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%?\\s*미만");
    private final CommonPersonalCostProfitService personal;
    private final UserPreferencePeriodBasis periods;
    private final AiAnalysisTables tables;
    private final AiRecommendationCodes codes;
    private final AiAnalysisMapper mapper;
    private final AiAnalysisProjection projection;
    public AiAnalysisPersonalFunction(CommonPersonalCostProfitService personal, UserPreferencePeriodBasis periods,
        AiAnalysisTables tables, AiRecommendationCodes codes, AiAnalysisMapper mapper, AiAnalysisProjection projection) {
        this.personal = personal; this.periods = periods; this.tables = tables; this.codes = codes;
        this.mapper = mapper; this.projection = projection;
    }
    public Set<String> functions() { return Set.of("F-04"); }
    public Result read(AiAnalysisInterpreter.Query query) {
        if (!(periods.metadata().get("periodBasis") instanceof UserPreferencePeriodBasis.Basis basis)) throw new NoAnswer(null);
        var end = YearMonth.parse(query.period().endMonth());
        var first = YearMonth.parse(query.period().startMonth());
        if (first.getYear() != end.getYear()) first = YearMonth.of(end.getYear(), 1);
        if (key(end).compareTo(basis.latestYm()) > 0) throw new NoAnswer(AiAnalysisProfitFunction.NOT_YET, null, List.of("다음 분기 대기 인력은?"));
        if (key(end).compareTo(basis.cashBaseYm()) < 0) throw new NoAnswer(null);
        if (key(first).compareTo(basis.cashBaseYm()) < 0) first = month(basis.cashBaseYm());
        int latestYear = Integer.parseInt(basis.latestYm().substring(0, 4));
        String q = query.question();
        var input = new LinkedHashMap<String, Object>();
        input.put("year", end.getYear());
        // 지난 해는 화면처럼 기준월 12월로 부르고 더하는 달만 줄인다(그 해 12월이 아니어도 오류가 나지 않게).
        boolean pastYear = end.getYear() < latestYear;
        input.put("baseMonth", pastYear ? 12 : end.getMonthValue());
        input.put("displayMode", "연누계"); input.put("includeTurnkey", true);
        boolean loss = has(q, "적자", "손해", "마이너스");
        if (loss && !query.salary()) input.put("view", "적자만");
        // 직무 고르기는 인력 함수(F-09·F-10·P-04)와 같다: 이름·앞 낱말(「백엔드」)·코드(「BE」).
        var job = AiAnalysisWorkforceFunctions.code(codes, "JOB", q);
        if (job != null) input.put("jobCode", job.code);
        var identities = mapper.identities();
        var names = (identities == null ? List.<com.company.common.aianalysis.mapper.AiAnalysisRows.Identity>of() : identities).stream()
            .filter(i -> i != null && i.name() != null && !i.name().isBlank() && i.replacement() != null && !i.replacement().isBlank()
                && q.contains(i.name())).map(i -> i.name()).distinct().toList();
        if (names.size() > 1) throw new NoAnswer(null);
        if (names.size() == 1) input.put("name", names.get(0));
        var filter = tables.filter(input, PersonalCostProfitContracts.Filter.class);
        var period = span(first, end);
        String label = first.equals(end) ? end.toString() : period(period);
        boolean provisional = false;
        for (var m = first; !m.isAfter(end); m = m.plusMonths(1)) provisional |= !basis.closedMonths().contains(key(m));
        if (query.salary()) {
            // 급여는 달마다 따로 부른다(연 누계의 급여 합을 「월 급여」라고 보이지 않게).
            var monthly = new LinkedHashMap<YearMonth, List<Map<String, Object>>>();
            for (var m = first; !m.isAfter(end); m = m.plusMonths(1)) {
                var items = personal.calculate(filter, m.getMonthValue(), m.getMonthValue()).result().items();
                if (names.size() == 1) items = items.stream().filter(row -> names.get(0).equals(row.get("developerName"))).toList();
                monthly.put(m, items);
            }
            return salary(monthly, label, period, end, provisional);
        }
        var calculation = personal.calculate(filter, first.getMonthValue(), pastYear ? end.getMonthValue() : null);
        var rows = calculation.result().items();
        if (names.size() == 1) {
            rows = rows.stream().filter(row -> names.get(0).equals(row.get("developerName"))).toList();
            if (rows.isEmpty()) throw new NoAnswer(null);
        }
        // 시안: 매출이 없는 사람(대기 등)은 뺀다.
        if (loss) rows = rows.stream().filter(r -> number(r, "unitPrice").signum() > 0 && number(r, "operatingProfit").signum() < 0).toList();
        var threshold = THRESHOLD.matcher(q);
        BigDecimal limit = null;
        if (threshold.find()) {
            limit = new BigDecimal(threshold.group(1));
            var value = limit;
            rows = rows.stream().filter(r -> number(r, "unitPrice").signum() > 0
                && r.get("profitRate") instanceof BigDecimal rate && rate.compareTo(value) < 0).toList();
        }
        boolean visible = projection.salaryAllowed();
        // 시안 적자 인력 표: 이름(밑에 작은 관리번호)·프로젝트·단가·원가·영업이익, 합계 줄 없음.
        var columns = new ArrayList<Column>(List.of(Column.text("name", "이름"), Column.text("projectName", "프로젝트"),
            Column.money("unitPrice", "단가").hidden(), Column.money("totalCost", "원가").hidden(),
            Column.money("operatingProfit", "영업이익").hidden()));
        if (limit != null || names.size() == 1) columns.add(Column.rate("profitRate", "이익률").hidden());
        var tableRows = new ArrayList<List<Object>>();
        BigDecimal profit = BigDecimal.ZERO;
        for (var row : rows) {
            var line = new ArrayList<Object>(Arrays.asList(AiAnalysisWorkforceFunctions.label((String) row.get("developerName"),
                (String) row.get("managementNumber")), projects(row), row.get("unitPrice"), row.get("totalCost"), row.get("operatingProfit")));
            if (columns.size() > 5) line.add(row.get("profitRate"));
            tableRows.add(line);
            profit = profit.add(number(row, "operatingProfit"));
        }
        var table = AiAnalysisTables.table(null, columns, tableRows, null, null);
        String conclusion;
        if (names.size() == 1) conclusion = label + " " + names.get(0) + (visible ? "의 영업이익은 " + bold(money(profit))
            + (rows.get(0).get("profitRate") instanceof BigDecimal r ? "(이익률 " + rate(r) + ")" : "") + "입니다."
            : "은 개인 원가·수익표에 있습니다. 개인별 원가는 볼 권한이 없어 숫자를 뺐습니다.");
        else if (limit != null) conclusion = label + " 이익률 " + limit.stripTrailingZeros().toPlainString() + "% 미만 인력은 "
            + bold(people(rows.size())) + "입니다.";
        else if (loss) conclusion = label + " 적자 인력은 " + bold(people(rows.size())) + (visible && !rows.isEmpty()
            ? ", 합계 " + bold(money(profit)) : "") + "입니다.";
        else conclusion = label + " 개인 손익 대상은 " + bold(people(rows.size())) + (visible ? ", 영업이익 합계 " + bold(money(profit)) : "") + "입니다.";
        var assumptions = new ArrayList<String>();
        if (loss) assumptions.add("그 달 개인 원가·수익표의 영업이익(단가 − 개인 원가)이 0보다 작은 사람입니다. 매출이 없는 사람(대기 등)은 뺐습니다.");
        else if (limit != null) assumptions.add("개인 원가·수익표의 이익률(영업이익 ÷ 단가)로 골랐습니다. 매출이 없는 사람(대기 등)은 뺐습니다.");
        else assumptions.add("개인 원가·수익표와 같은 계산입니다.");
        assumptions.add("마감 전 달은 잠정 값입니다.");
        // 시안: 「개인 원가·수익표 · 2026-10 (마감)」. 마감 전이면 화면이 「(…는 잠정)」을 붙인다.
        String used = used(tables.screen("UI-03"), period) + (provisional ? "" : " (마감)");
        return new Result(conclusion, List.of(table), null, assumptions, used, end.toString(),
            provisional, false, List.of(tables.open("UI-03", period)));
    }
    /** 시안 급여 답: 그 달 재직 중인 사람의 월 급여 합(외주 제외). 권한 검사는 CommonAiAnalysisService가 먼저 한다. */
    /**
     * 달마다 한 줄: 연월 · 인원 · 월 급여 합계 · 1인 평균(그 달 월 급여 합 ÷ 그 달 인원). 여러 달이어도 칸 이름과 값이 같은 뜻이다.
     * 한 달이면 시안처럼 화면에는 결론만 보이고 표는 엑셀에만 쓴다.
     */
    private Result salary(Map<YearMonth, List<Map<String, Object>>> monthly, String label, Period period, YearMonth end,
        boolean provisional) {
        var rows = new ArrayList<List<Object>>();
        int lastCount = 0;
        BigDecimal lastTotal = BigDecimal.ZERO, lastAverage = BigDecimal.ZERO;
        for (var entry : monthly.entrySet()) {
            var paid = entry.getValue().stream().filter(r -> !"외주".equals(r.get("employeeTypeName"))
                && r.get("monthlySalary") instanceof BigDecimal).toList();
            BigDecimal total = paid.stream().map(r -> number(r, "monthlySalary")).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal average = paid.isEmpty() ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(paid.size()), 0, java.math.RoundingMode.HALF_UP);
            rows.add(Arrays.<Object>asList(entry.getKey().toString(), paid.size(), total, average));
            lastCount = paid.size(); lastTotal = total; lastAverage = average;
        }
        var table = AiAnalysisTables.table(null, List.of(Column.month("month", "연월"), Column.count("people", "인원"),
            Column.money("monthlySalary", "월 급여 합계").hidden(), Column.money("average", "1인 평균").hidden()), rows, null, null);
        boolean single = monthly.size() == 1;
        String conclusion = single
            ? label + " 급여(월 급여 기준, 외주 제외)는 " + bold(people(lastCount)) + ", 합계 " + bold(money(lastTotal))
                + "입니다. 1인 평균 " + money(lastAverage) + "."
            : label + " 급여(월 급여 기준, 외주 제외)는 달마다 표와 같습니다. " + end + "은 " + bold(people(lastCount))
                + ", 합계 " + bold(money(lastTotal)) + ", 1인 평균 " + money(lastAverage) + "입니다.";
        return new Result(conclusion, List.of(single ? table.hide() : table), null,
            List.of("그 달 재직 중인 사람의 월 급여 합입니다(외주 제외, 그 달 전에 퇴사했거나 그 달 뒤에 입사한 사람 제외)."
                + (single ? "" : " 1인 평균은 그 달 합계 ÷ 그 달 인원입니다.")),
            used("급여 업로드 · " + tables.screen("UI-02"), period), end.toString(), provisional, false,
            List.of(tables.open("UI-02", period)));
    }
    private static String projects(Map<String, Object> row) {
        if (!(row.get("allocations") instanceof List<?> list)) return null;
        var names = new LinkedHashSet<String>();
        for (Object item : list) if (item instanceof Map<?, ?> map && map.get("projectName") instanceof String name
            && !"IDLE".equals(map.get("projectId"))) names.add(name);
        return names.isEmpty() ? null : String.join(", ", names);
    }
    private static BigDecimal number(Map<String, Object> row, String key) {
        return row.get(key) instanceof BigDecimal value ? value : BigDecimal.ZERO;
    }
}
