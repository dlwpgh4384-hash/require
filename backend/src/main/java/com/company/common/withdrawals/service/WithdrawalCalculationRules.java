package com.company.common.withdrawals.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** DB 정의의 지원 문법만 해석한다. 정의·원천 자료를 저장하거나 배치를 실행하지 않는다. */
@Component
public class WithdrawalCalculationRules {
    private static final String NUMBER = "([0-9]+(?:\\.[0-9]+)?)";
    private final CalculationRuleService definitions;

    public WithdrawalCalculationRules(CalculationRuleService definitions) {
        this.definitions = definitions;
    }

    public record ScoreInput(boolean sameJob, boolean relatedJob, Integer gradeDistance,
        int gapMonths, boolean sameCustomer, BigDecimal winningProbability) {}
    public record Extension(BigDecimal monthlyMm, String extensionPossibilityCode) {}
    /** 조회 계층에서 직접·휴직·퇴사월을 판정한 사람별 확정 월 MM과 철수 건. */
    public record SupplyPerson(BigDecimal confirmedMm, List<Extension> extensions) {}
    public record OpportunityDemand(BigDecimal requiredCount, BigDecimal monthlyMm,
        BigDecimal winningProbability) {}
    public record ProjectDemand(BigDecimal plannedMm, BigDecimal confirmedMm, int remainingMonths) {}

    private String formula(String id) {
        definitions.definition("식-00");
        String value = definitions.definition(id).formula();
        if (value == null) throw unsupported(id);
        return value.replaceAll("\\s+", "");
    }

    private Matcher match(String id, String expression) {
        Matcher matcher = Pattern.compile(expression).matcher(formula(id));
        if (!matcher.matches()) throw unsupported(id);
        return matcher;
    }

    public int nearTermMonthOffset() {
        Matcher terms = match("식-77",
            "남은일수\\(D-n\\)=철수일[−-]오늘\\.철수예정=0≤D-n이고철수일이속한달≤이번달\\+([0-9]+)개월");
        return integer(terms.group(1), "식-77");
    }

    public int remainingDays(LocalDate endDate, LocalDate today) {
        nearTermMonthOffset();
        return Math.toIntExact(ChronoUnit.DAYS.between(today, endDate));
    }

    public boolean nearTerm(LocalDate endDate, LocalDate today) {
        int months = nearTermMonthOffset();
        return !endDate.isBefore(today)
            && !YearMonth.from(endDate).isAfter(YearMonth.from(today).plusMonths(months));
    }

    public int candidateWindowMonths() {
        Matcher terms = match("식-79", "조건1:철수다음달부터([0-9]+)개월안에시작\\(이미시작했으면끝나기전\\)\\."
            + "조건2:같은직무\\(또는관련직무\\)의가배정인원<필요인원\\.공백개월=자리시작월[−-]철수다음달,0~([0-9]+)");
        int months = integer(terms.group(1), "식-79");
        if (months != integer(terms.group(2), "식-79")) throw unsupported("식-79");
        return months;
    }

    public boolean candidatePeriod(LocalDate endDate, YearMonth needStart, int durationMonths) {
        int window = candidateWindowMonths();
        YearMonth available = YearMonth.from(endDate).plusMonths(1);
        return durationMonths > 0 && !needStart.isAfter(available.plusMonths(window))
            && !needStart.plusMonths(durationMonths - 1L).isBefore(available);
    }

    public YearMonth tentativeStartMonth(LocalDate endDate, YearMonth needStart) {
        candidateWindowMonths();
        YearMonth available = YearMonth.from(endDate).plusMonths(1);
        return available.isAfter(needStart) ? available : needStart;
    }

    public int waitingDays(LocalDate endDate, YearMonth startMonth) {
        Matcher terms = match("식-81", "대기예상일수=자리시작일[−-]철수일[−-]([0-9]+)");
        long days = ChronoUnit.DAYS.between(endDate, startMonth.atDay(1))
            - integer(terms.group(1), "식-81");
        return Math.toIntExact(Math.max(0, days));
    }

    public BigDecimal aiRecommendScore(ScoreInput input) {
        Matcher terms = match("식-80", "점수=직무\\(일치" + NUMBER + ",관련직무" + NUMBER
            + "\\)\\+등급\\(일치" + NUMBER + ",한단계차이" + NUMBER
            + "\\)\\+공백\\(없음" + NUMBER + ",1개월" + NUMBER + ",2개월" + NUMBER
            + ",3개월" + NUMBER + "\\)\\+같은고객사" + NUMBER
            + "\\+수주확률" + NUMBER + "%이상" + NUMBER);
        if (input.gapMonths() < 0 || input.gapMonths() > 3
                || input.winningProbability() == null) throw unsupported("식-80");
        BigDecimal score = BigDecimal.ZERO;
        if (input.sameJob()) score = score.add(decimal(terms, 1));
        else if (input.relatedJob()) score = score.add(decimal(terms, 2));
        if (Integer.valueOf(0).equals(input.gradeDistance())) score = score.add(decimal(terms, 3));
        else if (Integer.valueOf(1).equals(input.gradeDistance())) score = score.add(decimal(terms, 4));
        score = score.add(decimal(terms, 5 + input.gapMonths()));
        if (input.sameCustomer()) score = score.add(decimal(terms, 9));
        if (input.winningProbability().compareTo(decimal(terms, 10)) >= 0)
            score = score.add(decimal(terms, 11));
        if (score.compareTo(BigDecimal.valueOf(100)) > 0) throw unsupported("식-80");
        return score;
    }

    public boolean bannerCandidate(LocalDate endDate, LocalDate today,
        String extensionPossibilityCode, boolean hasOutstandingTentative) {
        Matcher terms = match("식-82", "A=([0-9]+)개월안철수예정자수\\.B=A중가배정이없고연장가능성이[“\"]높음[”\"]이아닌사람수\\."
            + "C=B중추천자리가([0-9]+)곳이상있는사람수");
        int months = integer(terms.group(1), "식-82");
        if (months != nearTermMonthOffset() + 1 || integer(terms.group(2), "식-82") != 1)
            throw unsupported("식-82");
        return !hasOutstandingTentative && !"H".equals(extensionPossibilityCode)
            && nearTerm(endDate, today);
    }

    public BigDecimal supply(List<SupplyPerson> people, YearMonth month) {
        Matcher terms = match("식-60", "사람별공급=max\\(0," + NUMBER
            + "[−-]확정투입MM[−-]철수한건의MM×연장확률\\)\\.그달투입이하나도없는사람은"
            + NUMBER + "\\.직무공급=사람별합,소수(첫째|둘째|셋째)자리반올림");
        BigDecimal capacity = decimal(terms, 1);
        BigDecimal idle = decimal(terms, 2);
        int scale = switch (terms.group(3)) { case "첫째" -> 1; case "둘째" -> 2; default -> 3; };
        BigDecimal total = BigDecimal.ZERO;
        for (SupplyPerson person : people) {
            BigDecimal extended = BigDecimal.ZERO;
            for (Extension extension : person.extensions()) {
                String code = extension.extensionPossibilityCode();
                String standard = code == null || code.isEmpty() ? "EXT_PRBL_NULL" : switch (code) {
                    case "H" -> "EXT_PRBL_H";
                    case "M" -> "EXT_PRBL_M";
                    case "L" -> "EXT_PRBL_L";
                    default -> throw CodeFailure.invalid("extensionPossibilityCode", "등록된 값을 골라 주세요.");
                };
                BigDecimal probability = definitions.effectiveStandard(standard, month).value();
                if (probability.signum() < 0 || probability.compareTo(BigDecimal.valueOf(100)) > 0)
                    throw unsupported("식-60");
                extended = extended.add(extension.monthlyMm().multiply(probability).movePointLeft(2));
            }
            BigDecimal available = person.confirmedMm().signum() == 0 && person.extensions().isEmpty()
                ? idle : capacity.subtract(person.confirmedMm()).subtract(extended).max(BigDecimal.ZERO);
            total = total.add(available);
        }
        return total.setScale(scale, RoundingMode.HALF_UP);
    }

    /** 호출자는 대상 월에 유효한 진행 기회와 계획을 가진 진행 프로젝트만 전달한다. */
    public BigDecimal demand(List<OpportunityDemand> opportunities, List<ProjectDemand> projects) {
        match("식-61", "수요=Σ\\(필요인원×월MM×수주확률\\)\\+Σ진행프로젝트미충원MM");
        String details = definitions.definition("식-61").details();
        if (details == null || !Pattern.compile("미충원\\(프로젝트·직무·달\\)=max\\(0,계획MM[−-]확정배정MM\\)÷남은개월")
                .matcher(details.replaceAll("\\s+", "")).find()) throw unsupported("식-61");
        BigDecimal total = BigDecimal.ZERO;
        for (OpportunityDemand opportunity : opportunities)
            total = total.add(opportunity.requiredCount().multiply(opportunity.monthlyMm())
                .multiply(opportunity.winningProbability()).movePointLeft(2));
        for (ProjectDemand project : projects) {
            if (project.remainingMonths() > 0)
                total = total.add(project.plannedMm().subtract(project.confirmedMm()).max(BigDecimal.ZERO)
                    .divide(BigDecimal.valueOf(project.remainingMonths()), MathContext.DECIMAL128));
        }
        return total;
    }

    private static int integer(String value, String id) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException failure) { throw unsupported(id); }
    }

    private static BigDecimal decimal(Matcher matcher, int group) {
        return new BigDecimal(matcher.group(group));
    }

    private static CodeFailure unsupported(String id) {
        return CodeFailure.invalid("calculation", "지원하지 않는 " + id + " 정의입니다. DB 계산식의 지원 연산을 확인해 주세요.");
    }
}
