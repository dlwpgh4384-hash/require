package com.company.common.partsettlements.service;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.partsettlements.dto.PartSettlementContracts.*;
import com.company.common.partsettlements.mapper.PartSettlementRows.*;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import static java.math.BigDecimal.ZERO;

public final class PartSettlementRules {
    public static final String MISSING_SALARY = "⚠ 월 급여 없음(시작 달 급여 0)";
    public static final String UPLOAD_PENDING = "⚠ 급여 업로드 전";
    public static final String TURNKEY_COST_PENDING = "턴키 배분 원가 없음(마감 전)";
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DB = DateTimeFormatter.ofPattern("uuuuMM");
    public PartSettlementRules(List<Definition> definitions) {
        Map<String, Definition> rules = definitions.stream().collect(Collectors.toMap(Definition::calcId, x -> x));
        for (String id : List.of("식-00","식-01","식-35","식-36","식-37","식-39","식-40","식-41","식-42","식-43","식-44","식-45","식-57","식-58")) {
            Definition d = rules.get(id);
            if (d == null || d.formula() == null || d.formula().isBlank() || d.details() == null || d.details().isBlank())
                throw CodeFailure.invalid("source", "파트 정산 계산 정의를 확인해 주세요.");
        }
        for (var entry : Map.of("식-41","월 단가","식-42","퇴직금","식-43","이익금","식-44","법인카드","식-57","버림").entrySet())
            if (!rules.get(entry.getKey()).formula().contains(entry.getValue()))
                throw CodeFailure.invalid("source", "파트 정산 계산 정의와 계산 규칙을 확인해 주세요.");
    }
    public static YearMonth month(String value) {
        if (value == null || !value.matches("[0-9]{4}-(0[1-9]|1[0-2])"))
            throw CodeFailure.invalid("yearMonth", "정산 년월을 확인해 주세요.");
        return YearMonth.parse(value);
    }
    public static String db(YearMonth month) { return month.format(DB); }
    public static BigDecimal money(BigDecimal value) { return value.setScale(0, RoundingMode.DOWN); }
    public static BigDecimal nz(BigDecimal value) { return value == null ? ZERO : value; }
    public static boolean confirmed(Header h) { return h != null && Set.of("CNFM","AUTO").contains(h.state); }
    public static Instant cutoff(LocalDate day) { return day.plusDays(1).atStartOfDay(KOREA).toInstant(); }
    private static Comparator<History> order() { return Comparator.comparing((History h) -> h.at).thenComparing(h -> h.id); }
    public static History next(List<History> history, String table, String target, String item, Instant cutoff) {
        return history.stream().filter(h -> table.equals(h.tableName) && target.equals(h.target)
            && item.equals(h.item) && !h.at.isBefore(cutoff)).min(order()).orElse(null);
    }
    public static boolean closeTarget(Part part, YearMonth month, List<History> history) {
        if ("Y".equals(part.hqYn) || part.distributionRate == null) return false;
        var later = next(history,"TB_BS_PART",part.partId,"사용 여부",cutoff(month.atEndOfMonth()));
        return "Y".equals(later == null ? part.useYn : later.before);
    }
    public record Membership(String partId, String warning) {}
    public static Membership membership(Input input, YearMonth month, List<Part> parts, List<History> history) {
        LocalDate day = input.retireDate != null && input.retireDate.isBefore(month.atEndOfMonth())
            ? input.retireDate : month.atEndOfMonth();
        History change = next(history,"TB_HR_DEV",input.developerId,"영업 파트",cutoff(day));
        if (change == null) return new Membership(input.partId,null);
        if (parts.stream().anyMatch(p -> p.partId.equals(change.before))) return new Membership(change.before,null);
        List<Part> matches = parts.stream().filter(p -> {
            History renamed = history.stream().filter(h -> "TB_BS_PART".equals(h.tableName)
                && p.partId.equals(h.target) && "파트명".equals(h.item)
                && order().compare(h,change) > 0).min(order()).orElse(null);
            return Objects.equals(change.before,renamed == null ? p.partName : renamed.before);
        }).toList();
        return matches.size() == 1 ? new Membership(matches.get(0).partId,null)
            : new Membership(input.partId,"소속 복원 불명확: 현재 소속 사용");
    }
    public List<Workforce> calculate(Part part, YearMonth month, List<Input> inputs, List<String> warnings) {
        var people = inputs.stream().filter(i -> !"IN".equals(i.contractType) && nz(i.mm).signum() > 0)
            .collect(Collectors.groupingBy(i -> i.developerId, LinkedHashMap::new, Collectors.toList()));
        List<Workforce> result = new ArrayList<>();
        for (var assignments : people.values()) {
            Input person = assignments.get(0);
            boolean uploaded = Boolean.TRUE.equals(person.uploaded);
            String type = uploaded ? person.payrollType : person.employeeTypeCode;
            boolean outsourced = "OS".equals(type);
            boolean startMonth = !outsourced && person.firstStart != null && YearMonth.from(person.firstStart).equals(month);
            List<String> notes = new ArrayList<>();
            if (!outsourced) notes.add("FT".equals(type) ? "공통관리비+퇴직금" : "공통관리비");
            BigDecimal salary;
            boolean missing = false;
            if (outsourced) salary = uploaded ? nz(person.vendorSupply) : nz(person.monthlySalary);
            else if (startMonth) {
                // 식-42: 시작 달은 월 급여(개발자 풀) × 일수 비례. 업로드 값은 개발자 풀 월 급여가 없을 때만 쓴다(2026-11-21 사용자 결정).
                BigDecimal base = person.monthlySalary != null ? person.monthlySalary : uploaded ? person.salary : null;
                missing = base == null;
                salary = nz(base);
                int days = month.lengthOfMonth(), from = person.firstStart.getDayOfMonth();
                if (from > 1) {
                    salary = salary.multiply(BigDecimal.valueOf(days - from + 1)).divide(BigDecimal.valueOf(days), 16, RoundingMode.DOWN);
                    notes.add("시작 달 " + (days - from + 1) + "/" + days + "일");
                }
            } else salary = uploaded ? nz(person.salary) : nz(person.monthlySalary);
            salary = money(salary);
            if (missing) notes.add(MISSING_SALARY);
            if (!uploaded && !startMonth) notes.add(UPLOAD_PENDING);
            BigDecimal expense = outsourced ? ZERO : part.salesExpense;
            BigDecimal retirement = "FT".equals(type) ? salary.divide(BigDecimal.valueOf(12),0,RoundingMode.DOWN) : ZERO;
            BigDecimal cost = salary.add(expense).add(retirement);
            BigDecimal mm = assignments.stream().map(i -> i.mm).reduce(ZERO,BigDecimal::add).setScale(2,RoundingMode.HALF_UP);
            BigDecimal revenue = ZERO;
            Map<String,List<Input>> projects = assignments.stream().collect(Collectors.groupingBy(i -> i.projectId));
            for (List<Input> project : projects.values()) {
                Input first = project.get(0);
                List<Input> paid = project.stream().filter(i -> !"FREE".equals(i.inputType)).toList();
                if (paid.size() != project.size()) notes.add("무상지원");
                if (paid.isEmpty()) continue;
                if ("TK".equals(first.contractType)) {
                    if (first.distribution != null) revenue = revenue.add(money(first.distribution));
                    else if (first.budget == null || first.budget.signum() <= 0) notes.add("턴키 예산 없음: 단가 0");
                    else if (first.allocatedCost == null) notes.add(TURNKEY_COST_PENDING);
                    else revenue = revenue.add(money(first.allocatedCost.multiply(nz(first.contractAmount))
                        .divide(first.budget,16,RoundingMode.DOWN)));
                } else for (Input assignment : paid) revenue = revenue.add(money(nz(assignment.price).multiply(assignment.mm)));
            }
            var names = projects.values().stream().sorted(Comparator
                .<List<Input>,BigDecimal>comparing(rows -> rows.stream().map(i -> i.mm).reduce(ZERO,BigDecimal::add)).reversed()
                .thenComparing(rows -> rows.get(0).projectName))
                .map(rows -> rows.get(0).projectName).distinct().toList();
            BigDecimal profit = revenue.subtract(cost);
            BigDecimal share = money(profit.multiply(part.distributionRate).movePointLeft(2));
            notes.addAll(warnings);
            result.add(new Workforce(person.developerId,person.developerName,type,names,mm,revenue,salary,
                expense,retirement,cost,profit,share,notes.stream().distinct().toList(),missing));
        }
        return result.stream().sorted(Comparator.comparing(Workforce::developerName).thenComparing(Workforce::developerId)).toList();
    }
    public static void snapshotCapacity(List<Workforce> rows) {
        for (var row : rows) {
            if (row.projectNames().stream().anyMatch(n -> n.contains(", ")) || String.join(", ",row.projectNames()).codePointCount(0,String.join(", ",row.projectNames()).length()) > 1000)
                throw CodeFailure.invalid("projectNames", "프로젝트 표시목록은 구분자(쉼표와 공백) 없이 1000자 이내여야 합니다.");
            String notes = String.join(" · ",row.notes());
            if (notes.codePointCount(0,notes.length()) > 100) throw CodeFailure.invalid("notes", "정산 비고는 100자 이내여야 합니다.");
            for (BigDecimal amount : List.of(row.unitPrice(),row.salary(),row.salesExpense(),row.retirementAmount(),row.totalCost(),row.profit(),row.profitShare()))
                if (amount.abs().compareTo(new BigDecimal("999999999999999")) > 0)
                    throw CodeFailure.invalid("amount", "정산 금액이 저장 가능한 범위를 넘었습니다.");
            if (row.monthlyMm().abs().compareTo(new BigDecimal("9999.99")) > 0)
                throw CodeFailure.invalid("monthlyMm", "정산 MM이 저장 가능한 범위를 넘었습니다.");
        }
    }
}
