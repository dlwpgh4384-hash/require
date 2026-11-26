package com.company.common.personalcostprofits.service;

import com.company.common.common.PersonalCostProfitDefinitions;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.personalcostprofits.dto.PersonalCostProfitContracts.*;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitMapper;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.company.common.personalcostprofits.service.PersonalCostProfitRules.*;
import static java.math.BigDecimal.ZERO;

@Service
public class CommonPersonalCostProfitService {
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("uuuuMM");
    /** 소속 조건 「자사」 값. 매퍼에서 BLNG_CLNT_ID IS NULL로 바꾼다. */
    public static final String SELF_AFFILIATION = "__self";
    private final PersonalCostProfitMapper mapper;
    private final PersonalCostProfitDefinitions definitions;
    private final CalculationRuleService calculation;
    private final UserPreferencePeriodBasis periods;
    private final CurrentBusinessActor actor;

    public CommonPersonalCostProfitService(PersonalCostProfitMapper mapper,
        PersonalCostProfitDefinitions definitions, CalculationRuleService calculation,
        UserPreferencePeriodBasis periods, CurrentBusinessActor actor) {
        this.mapper = mapper;
        this.definitions = definitions;
        this.calculation = calculation;
        this.periods = periods;
        this.actor = actor;
    }

    public record Calculation(Result result, List<String> columns, Map<String, Object> leaveTotals) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result getCommonPersonalCostProfits(Filter filter) { return calculate(filter).result(); }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Calculation calculate(Filter filter) {
        return calculate(filter, 1);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Calculation calculate(Filter filter, int firstMonth) {
        return calculate(filter, firstMonth, null);
    }

    /**
     * lastMonth: 기준월 검사는 화면과 같이 하고(지난 해는 12월), 더하는 달만 firstMonth~lastMonth로 줄인다.
     * AI 분석 도우미가 지난 해 한 달을 물을 때 쓴다. null이면 기준월까지.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Calculation calculate(Filter filter, int firstMonth, Integer lastMonth) {
        validateShape(filter);
        int last = lastMonth == null ? filter.baseMonth() : lastMonth;
        if (last < 1 || last > filter.baseMonth() || firstMonth < 1 || firstMonth > last)
            throw CodeFailure.invalid("firstMonth", "조회 시작월을 확인해 주세요.");
        var meta = periods.metadata();
        if (!(meta.get("periodBasis") instanceof UserPreferencePeriodBasis.Basis basis)) {
            throw CodeFailure.invalid("year", "기간 기준을 확인해 주세요.");
        }
        int firstYear = Integer.parseInt(basis.cashBaseYm().substring(0, 4));
        int latestYear = Integer.parseInt(basis.latestYm().substring(0, 4));
        if (filter.year() < firstYear || filter.year() > latestYear) {
            throw CodeFailure.invalid("year", "지원하는 연도를 선택해 주세요.");
        }
        if ((filter.year() < latestYear && filter.baseMonth() != 12)
            || (filter.year() == latestYear && filter.baseMonth() > Integer.parseInt(basis.latestYm().substring(4)))) {
            throw CodeFailure.invalid("baseMonth", "선택 연도의 기준월을 확인해 주세요.");
        }
        var codes = mapper.selectCodes();
        validateCodes(filter, codes);
        // All semantic evaluators below are keyed by the authoritative DB definition inventory.
        var loaded = definitions.definitions();
        if (loaded == null || loaded.isEmpty()) throw CodeFailure.invalid("source", "계산 정의를 확인해 주세요.");
        List<String> columns = PersonalCostProfitColumnPolicy.columns(filter.columns(), mapper.selectActorRoles(actor.userId()));
        var people = mapper.selectPeople(filter);
        var pay = mapper.selectPayroll(filter);
        var closedCosts = mapper.selectCosts(filter);
        var assignments = mapper.selectAssignments(filter);
        var leaves = mapper.selectLeaves(filter);
        // 사람·달마다 목록 전체를 다시 훑지 않도록 한 번 묶어 둔다(인원이 많아도 느려지지 않게, 2026-11-24 사용자 결정).
        var payBy = new HashMap<String, Pay>();
        for (var p : pay) payBy.putIfAbsent(p.developerId() + ":" + p.yearMonth(), p);
        var inputBy = new HashMap<String, List<Assignment>>();
        for (var a : assignments) inputBy.computeIfAbsent(a.developerId() + ":" + a.yearMonth(), key -> new ArrayList<>()).add(a);
        var closedBy = new HashMap<String, List<Cost>>();
        for (var c : closedCosts) closedBy.computeIfAbsent(c.developerId() + ":" + c.yearMonth(), key -> new ArrayList<>()).add(c);
        var leavesBy = new HashMap<String, List<Leave>>();
        for (var l : leaves) leavesBy.computeIfAbsent(l.developerId(), key -> new ArrayList<>()).add(l);
        var rates = new HashMap<String, Rates>();
        List<Aggregate> visible = new ArrayList<>();
        Map<String, Object> leaveTotals = numeric();
        int totalPeople = 0, leavePeople = 0;
        BigDecimal employedCount = ZERO, operatingCount = ZERO, earningCount = ZERO;
        for (Person person : people) {
            Aggregate aggregate = new Aggregate(person);
            var personalLeaves = leavesBy.getOrDefault(person.developerId(), List.of());
            for (int m = firstMonth; m <= last; m++) {
                YearMonth month = YearMonth.of(filter.year(), m);
                if (!employed(person, month)) continue;
                String ym = month.format(YM);

                boolean closed = ym.compareTo(basis.cashBaseYm()) < 0 || basis.closedMonths().contains(ym);
                Pay payroll = payBy.get(person.developerId() + ":" + ym);
                String type = payroll == null ? person.employeeTypeCode() : payroll.employeeTypeCode();
                var input = inputBy.getOrDefault(person.developerId() + ":" + ym, List.of());
                boolean leave = leaveAt(personalLeaves, month.atEndOfMonth());
                Rates rate = closed ? new Rates(ZERO, ZERO, ZERO, ZERO) : rates.computeIfAbsent(ym,
                    key -> new Rates(rate(codes, "ALLOW_TEAM"), rate(codes, "ALLOW_GROUP"),
                        rate(codes, "SEV_RATE"), calculation.effectiveStandard("WTHLD_RT", month).value()));
                List<Cost> costs = closed ? closedBy.getOrDefault(person.developerId() + ":" + ym, List.of())
                    : live(person, payroll, input, ym, rate, leave);
                Map<String, BigDecimal> payrollValues = payroll(person, closed && costs.isEmpty() ? null : payroll, rate, closed);
                BigDecimal monthlyMm = costs.stream().filter(c -> !"IDLE".equals(c.projectId()))
                    .map(Cost::mm).reduce(ZERO, BigDecimal::add);
                boolean active = monthlyMm.signum() > 0;
                boolean earning = costs.stream().anyMatch(c -> Set.of("MO", "DP").contains(
                    c.contractTypeCode() == null ? "" : c.contractTypeCode()) && c.revenue().signum() > 0);
                aggregate.employed++;
                if (active && !leave) aggregate.operating++;
                if (earning && !leave) aggregate.earning++;
                if ("DRCT".equals(person.directClassCode())) {
                    aggregate.directMonths++;
                    if (active && !leave) aggregate.directOperating++;
                    if (earning && !leave) aggregate.directEarning++;
                }
                if (monthlyMm.compareTo(BigDecimal.ONE) > 0) aggregate.over = true;
                if (costs.stream().filter(c -> !"IDLE".equals(c.projectId()) && c.mm().signum() > 0).count() >= 2) {
                    aggregate.concurrent = true;
                }
                boolean external = "FL".equals(type) || "OS".equals(type);
                if (!leave && (!external || active)) aggregate.worked++;
                if (leave) {
                    // Leave payroll belongs to an anonymous IDLE group, never another named developer.
                    BigDecimal labor = closed ? costs.stream().map(Cost::labor).reduce(ZERO, BigDecimal::add)
                        : payrollValues.get("personalCost");
                    if (payroll != null || !costs.isEmpty()) {
                        var anon = numeric();
                        payrollValues.forEach(anon::put);
                        anon.put("personalCost", labor);
                        BigDecimal team = costs.stream().map(Cost::team).reduce(ZERO, BigDecimal::add);
                        BigDecimal expenses = costs.stream().map(Cost::expense).reduce(ZERO, BigDecimal::add);
                        anon.put("teamExpenses", team);
                        anon.put("projectExpenses", expenses);
                        anon.put("totalCost", labor.add(team).add(expenses));
                        anon.put("operatingProfit", labor.add(team).add(expenses).negate());
                        aggregate.leaveAmounts.add(anon);
                    }
                    continue;
                }
                payrollValues.forEach((key, value) -> add(aggregate.values, key, value));
                for (Cost cost : costs) {
                    boolean turnkey = "TK".equals(cost.contractTypeCode());
                    if (!"IDLE".equals(cost.projectId())) {
                        aggregate.hasProject = true;
                        if (!turnkey) aggregate.hasNonTurnkey = true;
                    }
                    BigDecimal revenue = Boolean.FALSE.equals(filter.includeTurnkey()) && turnkey ? ZERO : cost.revenue();
                    BigDecimal total = cost.labor().add(cost.team()).add(cost.expense());
                    add(aggregate.values, "mm", cost.mm());
                    add(aggregate.values, "unitPrice", revenue);
                    add(aggregate.values, "teamExpenses", cost.team());
                    add(aggregate.values, "projectExpenses", cost.expense());
                    add(aggregate.values, "totalCost", total);
                    add(aggregate.values, "operatingProfit", revenue.subtract(total));
                    aggregate.labor = aggregate.labor.add(cost.labor());
                    // 펼친 줄은 투입ID마다 한 줄: 조회 기간(1월~기준월)의 달별 값을 더한다(같은 프로젝트 유상·무상은 2줄)
                    Map<String, Object> allocation = aggregate.allocations.computeIfAbsent(cost.assignmentId(), key -> {
                        Map<String, Object> created = new LinkedHashMap<>();
                        created.put("projectId", cost.projectId());
                        created.put("assignmentId", cost.assignmentId());
                        created.put("projectName", cost.projectName());
                        created.put("jobName", cost.jobName());
                        created.put("contractTypeCode", cost.contractTypeCode());
                        created.put("free", "FREE".equals(cost.inputTypeCode()));
                        created.put("mm", ZERO);
                        created.put("unitPrice", ZERO);
                        created.put("allocatedCost", ZERO);
                        created.put("operatingProfit", ZERO);
                        return created;
                    });
                    add(allocation, "mm", cost.mm());
                    add(allocation, "unitPrice", revenue);
                    add(allocation, "allocatedCost", total);
                    add(allocation, "operatingProfit", revenue.subtract(total));
                }
            }
            aggregate.values.put("personalCost", aggregate.labor);
            if ("월평균".equals(filter.displayMode())) {
                aggregate.values.replaceAll((key, value) -> value instanceof BigDecimal number
                    ? divide(number, BigDecimal.valueOf(aggregate.worked), "mm".equals(key) ? 2 : 0) : value);
                // 펼친 줄도 사람 줄과 같이 일한 달 수로 나눈다
                for (var allocation : aggregate.allocations.values()) {
                    allocation.replaceAll((key, value) -> value instanceof BigDecimal number
                        ? divide(number, BigDecimal.valueOf(aggregate.worked), "mm".equals(key) ? 2 : 0) : value);
                }
                for (var anonymous : aggregate.leaveAmounts) {
                    anonymous.replaceAll((key, value) -> value instanceof BigDecimal number
                        ? divide(number, BigDecimal.valueOf(aggregate.leaveAmounts.size()), 0) : value);
                }
            }
            boolean endLeave = leaveAt(personalLeaves, YearMonth.of(filter.year(), last).atEndOfMonth());
            BigDecimal profit = number(aggregate.values, "operatingProfit");
            // 「적자만」은 표에 보이는 그 사람 영업이익으로 거른다. 휴직 비용은 대기 원가로 따로 보므로 더하지 않는다
            // (명세 SFR-103-07·시안, 요약 「적자 인원」과 같은 기준, 2026-11-24 사용자 결정).
            if ("적자만".equals(filter.view()) && profit.signum() >= 0) continue;
            if ("동시 투입·1.0 초과만".equals(filter.view()) && !aggregate.over && !aggregate.concurrent) continue;
            totalPeople++;
            employedCount = employedCount.add(BigDecimal.valueOf(aggregate.directMonths));
            operatingCount = operatingCount.add(BigDecimal.valueOf(aggregate.directOperating));
            earningCount = earningCount.add(BigDecimal.valueOf(aggregate.directEarning));
            for (var anonymous : aggregate.leaveAmounts) sum(leaveTotals, anonymous);
            if (endLeave) {
                sum(leaveTotals, aggregate.values);
                leavePeople++;
            } else visible.add(aggregate);
        }
        // 시안: 영업 파트 이름 → 이름(가나다) 순. 파트가 없으면 맨 뒤
        Collator korean = Collator.getInstance(Locale.KOREAN);
        visible.sort(Comparator.comparing((Aggregate a) -> a.person.salesPartName(), Comparator.nullsLast(korean))
            .thenComparing(a -> a.person.developerName(), Comparator.nullsLast(korean))
            .thenComparing(a -> a.person.developerId()));
        var totals = numeric();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Aggregate a : visible) {
            sum(totals, a.values);
            var row = new LinkedHashMap<>(a.values);
            row.put("developerId", a.person.developerId());
            row.put("developerName", a.person.developerName());
            row.put("managementNumber", a.person.managementNumber());
            row.put("affiliationName", a.person.affiliationName());
            row.put("positionName", a.person.positionName());
            row.put("employeeTypeName", a.person.employeeTypeName());
            row.put("joinDate", a.person.joinDate());
            row.put("operatingRate", percent(BigDecimal.valueOf(a.operating), BigDecimal.valueOf(a.employed)));
            // 식-96 칸 도움말 「투입된 달 ÷ 재직한 달」(시안 wr: "9달 ÷ 10달")
            row.put("operatingMonths", a.operating);
            row.put("employedMonths", a.employed);
            row.put("profitRate", number(row, "unitPrice").signum() == 0 ? null
                : percent(number(row, "operatingProfit"), number(row, "unitPrice")));
            row.put("mmOverOne", a.over);
            row.put("allocations", allocations(a));
            items.add(PersonalCostProfitColumnPolicy.project(row, columns));
        }
        var summary = summary(visible, totals, totalPeople, leavePeople,
            new BigDecimal[]{employedCount, operatingCount, earningCount}, filter);
        // 총 인원(휴직 포함·월평균 가동/가득 인원)과 가동률·가득률은 조건과 상관없이 회사 전체(식-37·38 「그 달 재직 직접 인력」,
        // SFR-103-04 「요약 칸 회사 전체 가득률」, 시안, 2026-11-24 사용자 결정). 조건이 있으면 조건 없이 한 번 더 계산해 가져온다.
        if (narrowed(filter)) {
            var company = calculate(new Filter(filter.year(), filter.baseMonth(), filter.displayMode(), null, null, null, null,
                null, null, filter.includeTurnkey(), filter.columns()), firstMonth, lastMonth).result().summary();
            for (String key : List.of("totalPeople", "leavePeople", "averageOperatingPeople", "averageEarningPeople",
                "averageDirectPeople", "operatingRate", "earningRate")) summary.put(key, company.get(key));
        }
        sum(totals, leaveTotals);
        return new Calculation(new Result(items, summary, PersonalCostProfitColumnPolicy.project(totals, columns),
            contributions(visible, codes)), columns, PersonalCostProfitColumnPolicy.project(leaveTotals, columns));
    }

    private static final class Aggregate {
        final Person person;
        final Map<String, Object> values = numeric();
        final Map<String, Map<String, Object>> allocations = new LinkedHashMap<>();
        final List<Map<String, Object>> leaveAmounts = new ArrayList<>();
        BigDecimal labor = ZERO;
        int employed, worked, operating, earning, directMonths, directOperating, directEarning;
        boolean over, concurrent, hasProject, hasNonTurnkey;
        Aggregate(Person person) { this.person = person; }
    }
    static Map<String, Object> numeric() {
        var result = new LinkedHashMap<String, Object>();
        for (String key : PersonalCostProfitColumnPolicy.KEYS.subList(6, 22)) {
            if (!"profitRate".equals(key)) result.put(key, ZERO);
        }
        return result;
    }
    /** 투입ID별 펼친 줄. 대기(IDLE) 몫은 맨 아래 한 줄. MM은 소수 둘째 자리. */
    private static List<Map<String, Object>> allocations(Aggregate a) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> idle = null;
        for (var entry : a.allocations.entrySet()) {
            var row = new LinkedHashMap<>(entry.getValue());
            row.put("mm", number(row, "mm").setScale(2, RoundingMode.HALF_UP));
            if ("IDLE".equals(entry.getKey())) idle = row;
            else result.add(row);
        }
        if (idle != null) result.add(idle);
        return result;
    }
    static BigDecimal number(Map<String, Object> row, String key) { return (BigDecimal) row.getOrDefault(key, ZERO); }
    static void add(Map<String, Object> row, String key, BigDecimal value) { row.put(key, number(row, key).add(value)); }
    static void sum(Map<String, Object> target, Map<String, Object> source) {
        source.forEach((key, value) -> { if (value instanceof BigDecimal amount) add(target, key, amount); });
    }
    private static BigDecimal rate(List<Code> codes, String code) {
        return codes.stream().filter(c -> "ALWNC".equals(c.groupCode()) && code.equals(c.code()) && c.value() != null)
            .map(Code::value).findFirst().orElseThrow(() -> CodeFailure.invalid("source", "수당 기준값을 확인해 주세요."));
    }
    private static void validateShape(Filter f) {
        if (f.year() == null) throw CodeFailure.invalid("year", "연도를 입력해 주세요.");
        if (f.baseMonth() == null || f.baseMonth() < 1 || f.baseMonth() > 12) {
            throw CodeFailure.invalid("baseMonth", "1월부터 12월까지 선택해 주세요.");
        }
        if (f.displayMode() == null || !Set.of("연누계", "월평균").contains(f.displayMode())) {
            throw CodeFailure.invalid("displayMode", "연 누계 또는 월 평균을 선택해 주세요.");
        }
        if (f.view() != null && !Set.of("전체", "적자만", "동시 투입·1.0 초과만").contains(f.view())) {
            throw CodeFailure.invalid("view", "등록된 보기를 선택해 주세요.");
        }
        // TB_HR_DEV.DEV_NM VARCHAR(50)
        if (f.name() != null && f.name().length() > 50) throw CodeFailure.invalid("name", "이름은 50자 이내로 입력해 주세요.");
        if (f.columns() != null && f.columns().stream().anyMatch(c -> !PersonalCostProfitColumnPolicy.KEYS.contains(c))) {
            throw CodeFailure.invalid("columns", "등록된 열을 선택해 주세요.");
        }
    }
    private static void validateCodes(Filter f, List<Code> codes) {
        var requested = new LinkedHashMap<String, String>();
        requested.put("employeeTypeCode", f.employeeTypeCode()); requested.put("jobCode", f.jobCode());
        requested.put("affiliationId", f.affiliationId()); requested.put("salesPartId", f.salesPartId());
        requested.forEach((field, value) -> {
            if (value == null || value.isEmpty()) return;
            // 소속 「자사」는 거래처 코드가 아니라 소속 없음(SELF_AFFILIATION)이다
            if ("affiliationId".equals(field) && SELF_AFFILIATION.equals(value)) return;
            String group = "employeeTypeCode".equals(field) ? "EMP_TYPE" : "jobCode".equals(field) ? "JOB" : field;
            if (codes.stream().noneMatch(c -> group.equals(c.groupCode()) && value.equals(c.code()))) {
                throw CodeFailure.invalid(field, "사용 가능한 값을 선택해 주세요.");
            }
        });
    }
    private static boolean narrowed(Filter f) {
        return java.util.stream.Stream.of(f.name(), f.employeeTypeCode(), f.affiliationId(), f.salesPartId(), f.jobCode())
            .anyMatch(v -> v != null && !v.isBlank()) || (f.view() != null && !f.view().isBlank() && !"전체".equals(f.view()));
    }
    private static Map<String, Object> summary(List<Aggregate> people, Map<String, Object> totals,
        int totalPeople, int leavePeople, BigDecimal[] headcounts, Filter filter) {
        var summary = new LinkedHashMap<String, Object>();
        BigDecimal employed = divide(headcounts[0], BigDecimal.valueOf(filter.baseMonth()), 1);
        BigDecimal operating = divide(headcounts[1], BigDecimal.valueOf(filter.baseMonth()), 1);
        BigDecimal earning = divide(headcounts[2], BigDecimal.valueOf(filter.baseMonth()), 1);
        summary.put("totalPeople", totalPeople);
        summary.put("averageOperatingPeople", operating);
        summary.put("averageEarningPeople", earning);
        long pricedPeople = people.stream()
            .filter(a -> !Boolean.FALSE.equals(filter.includeTurnkey()) || !a.hasProject || a.hasNonTurnkey).count();
        summary.put("averageUnitPrice", divide(number(totals, "unitPrice"), BigDecimal.valueOf(pricedPeople), 0));
        // 요약 칸 보조 문구(시안): 휴직 N명 포함 · 단가 칸이 있는 N명 · 월평균 재직 직접 인력 N.N명
        summary.put("leavePeople", leavePeople);
        summary.put("unitPricePeople", pricedPeople);
        summary.put("averageDirectPeople", employed);
        summary.put("averageCost", divide(number(totals, "totalCost"), BigDecimal.valueOf(people.size()), 0));
        summary.put("averageOperatingProfit", divide(number(totals, "operatingProfit"), BigDecimal.valueOf(people.size()), 0));
        summary.put("lossPeople", people.stream().filter(a -> number(a.values, "operatingProfit").signum() < 0).count());
        summary.put("operatingRate", percent(operating, employed));
        summary.put("earningRate", percent(earning, employed));
        return summary;
    }
    private static List<Map<String, Object>> contributions(List<Aggregate> people, List<Code> codes) {
        // 직무 줄은 공통코드 JOB 정렬 순서(시안 JOBS 순서), 같은 직무 안은 GRADE 정렬 순서. 코드에 없으면 뒤로
        Comparator<String> byJob = order(codes, "JOB");
        Comparator<String> byGrade = order(codes, "GRADE");
        Map<String, List<Aggregate>> grouped = new LinkedHashMap<>();
        people.stream().filter(a -> a.person.gradeName() != null
                && Set.of("초급", "중급", "고급", "특급").contains(a.person.gradeName()))
            .sorted(Comparator.comparing((Aggregate a) -> a.person.jobCode(), byJob)
                .thenComparing(a -> a.person.gradeCode(), byGrade))
            .forEach(a -> grouped.computeIfAbsent(a.person.jobCode() + ":" + a.person.gradeCode(),
                key -> new ArrayList<>()).add(a));
        return grouped.values().stream().map(group -> {
            var p = group.get(0).person;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("jobCode", p.jobCode()); row.put("jobName", p.jobName());
            row.put("gradeCode", p.gradeCode()); row.put("gradeName", p.gradeName());
            row.put("people", group.size());
            row.put("contributionPerPerson", divide(group.stream().map(a -> number(a.values, "operatingProfit"))
                .reduce(ZERO, BigDecimal::add), BigDecimal.valueOf(group.size()), 0));
            return row;
        }).toList();
    }
    /** selectCodes가 SORT_ORD 순서로 주는 그 그룹 코드의 순서. 없는 코드는 맨 뒤(코드 글자 순). */
    private static Comparator<String> order(List<Code> codes, String group) {
        List<String> ordered = codes.stream().filter(c -> group.equals(c.groupCode())).map(Code::code).toList();
        return Comparator.<String>comparingInt(code -> {
            int index = ordered.indexOf(code);
            return index < 0 ? Integer.MAX_VALUE : index;
        }).thenComparing(Comparator.nullsLast(Comparator.<String>naturalOrder()));
    }
}
