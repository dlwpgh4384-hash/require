package com.company.common.withdrawals.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 식 내용은 DB(TB_SY_CALC_RULE)에서 CALC_ID로 읽어 숫자(기간·가중치·자리수)를 꺼내 쓴다.
 * 식 글자 전체가 같은지는 비교하지 않는다. 숫자를 못 꺼내면 명세 숫자로 계산하고 로그만 남긴다(화면을 막지 않음).
 * 정의·원천 자료를 저장하거나 배치를 실행하지 않는다.
 */
@Component
public class WithdrawalCalculationRules {
    private static final Logger log = LoggerFactory.getLogger(WithdrawalCalculationRules.class);
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
    /** 식-80 가중치. gap은 공백 0·1·2·3개월 점수. */
    record Weights(BigDecimal sameJob, BigDecimal relatedJob, BigDecimal sameGrade, BigDecimal oneGrade,
        BigDecimal[] gap, BigDecimal sameCustomer, BigDecimal probabilityFrom, BigDecimal probability) {}
    record SupplyTerms(BigDecimal capacity, BigDecimal idle, int scale) {}

    /** 한 요청 동안 쓰는 식 묶음. 식마다 DB를 한 번만 읽는다(목록 줄마다 다시 읽지 않음). */
    public Settings settings() {
        return new Settings();
    }

    public final class Settings {
        private boolean principles;
        private final Map<String, String> texts = new HashMap<>();
        private Integer nearTermOffset;
        private Integer window;
        private Integer waitingOffset;
        private Integer minimumSeats;
        private Weights weights;
        private SupplyTerms supplyTerms;
        private final Map<String, BigDecimal> probabilities = new HashMap<>();
        private boolean demandRead;

        private Settings() {}

        private String text(String id) {
            if (!principles) {
                definitions.definition("식-00");
                principles = true;
            }
            return texts.computeIfAbsent(id, key -> {
                String value = definitions.definition(key).formula();
                return value == null ? "" : value.replaceAll("\\s+", "");
            });
        }

        public int nearTermMonthOffset() {
            if (nearTermOffset == null) nearTermOffset = whole(text("식-77"), "이번달\\+([0-9]+)개월", "식-77", 2);
            return nearTermOffset;
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

        /** 식-79 조건 1의 개월 수(철수 다음 달부터 n개월 안에 시작). */
        public int candidateWindowMonths() {
            if (window == null) window = whole(text("식-79"), "([0-9]+)개월안에시작", "식-79", 3);
            return window;
        }

        /** 식-79 조건 1: 철수 다음 달부터 n개월 안에 시작하거나, 이미 시작했으면 끝나기 전. */
        public boolean candidatePeriod(LocalDate endDate, YearMonth needStart, int durationMonths) {
            int months = candidateWindowMonths();
            YearMonth available = YearMonth.from(endDate).plusMonths(1);
            return durationMonths > 0 && !needStart.isAfter(available.plusMonths(months))
                && !needStart.plusMonths(durationMonths - 1L).isBefore(available);
        }

        /** 가배정 시작월 = max(철수 다음 달, 자리 시작월). */
        public YearMonth tentativeStartMonth(LocalDate endDate, YearMonth needStart) {
            YearMonth available = YearMonth.from(endDate).plusMonths(1);
            return available.isAfter(needStart) ? available : needStart;
        }

        public int waitingDays(LocalDate endDate, YearMonth startMonth) {
            if (waitingOffset == null) waitingOffset = whole(text("식-81"), "철수일[−-]([0-9]+)", "식-81", 1);
            long days = ChronoUnit.DAYS.between(endDate, startMonth.atDay(1)) - waitingOffset;
            return Math.toIntExact(Math.max(0, days));
        }

        private Weights weights() {
            if (weights == null) {
                String f = text("식-80");
                weights = new Weights(
                    decimal(f, "직무\\(일치" + NUMBER, 1, "식-80", "40"),
                    decimal(f, "관련직무" + NUMBER, 1, "식-80", "20"),
                    decimal(f, "등급\\(일치" + NUMBER, 1, "식-80", "20"),
                    decimal(f, "한단계차이" + NUMBER, 1, "식-80", "10"),
                    new BigDecimal[] {decimal(f, "없음" + NUMBER, 1, "식-80", "25"),
                        decimal(f, "1개월" + NUMBER, 1, "식-80", "15"),
                        decimal(f, "2개월" + NUMBER, 1, "식-80", "8"),
                        decimal(f, "3개월" + NUMBER, 1, "식-80", "3")},
                    decimal(f, "같은고객사" + NUMBER, 1, "식-80", "10"),
                    decimal(f, "수주확률" + NUMBER + "%이상" + NUMBER, 1, "식-80", "60"),
                    decimal(f, "수주확률" + NUMBER + "%이상" + NUMBER, 2, "식-80", "5"));
            }
            return weights;
        }

        /** 수주 확률 가점 대상인지(식-80 「수주 확률 n% 이상」). */
        public boolean probabilityBonus(BigDecimal winningProbability) {
            return winningProbability != null && winningProbability.compareTo(weights().probabilityFrom()) >= 0;
        }

        public BigDecimal aiRecommendScore(ScoreInput input) {
            Weights w = weights();
            BigDecimal score = BigDecimal.ZERO;
            if (input.sameJob()) score = score.add(w.sameJob());
            else if (input.relatedJob()) score = score.add(w.relatedJob());
            if (Integer.valueOf(0).equals(input.gradeDistance())) score = score.add(w.sameGrade());
            else if (Integer.valueOf(1).equals(input.gradeDistance())) score = score.add(w.oneGrade());
            if (input.gapMonths() >= 0 && input.gapMonths() < w.gap().length) score = score.add(w.gap()[input.gapMonths()]);
            if (input.sameCustomer()) score = score.add(w.sameCustomer());
            if (probabilityBonus(input.winningProbability())) score = score.add(w.probability());
            if (score.compareTo(BigDecimal.valueOf(100)) > 0) {
                log.warn("식-80 점수가 100을 넘어 100으로 둡니다: {}", score);
                score = BigDecimal.valueOf(100);
            }
            return score;
        }

        /** 식-82 C의 「추천 자리가 n곳 이상」. */
        public int minimumSeats() {
            if (minimumSeats == null) minimumSeats = whole(text("식-82"), "([0-9]+)곳이상", "식-82", 1);
            return minimumSeats;
        }

        /** 식-82 B: 3개월 안 철수 예정이고 가배정이 없고 연장 가능성이 「높음」이 아님. */
        public boolean bannerCandidate(LocalDate endDate, LocalDate today,
            String extensionPossibilityCode, boolean hasOutstandingTentative) {
            minimumSeats();
            return !hasOutstandingTentative && !"H".equals(extensionPossibilityCode)
                && nearTerm(endDate, today);
        }

        private SupplyTerms supplyTerms() {
            if (supplyTerms == null) {
                String f = text("식-60");
                Matcher digits = Pattern.compile("소수(첫째|둘째|셋째)자리").matcher(f);
                int scale = 1;
                if (digits.find()) scale = switch (digits.group(1)) { case "첫째" -> 1; case "둘째" -> 2; default -> 3; };
                else log.warn("식-60 정의에서 반올림 자리를 읽지 못해 명세 값(소수 첫째 자리)을 씁니다.");
                supplyTerms = new SupplyTerms(decimal(f, "max\\(0," + NUMBER, 1, "식-60", "1"),
                    decimal(f, "투입이하나도없는사람은" + NUMBER, 1, "식-60", "1"), scale);
            }
            return supplyTerms;
        }

        public BigDecimal supply(List<SupplyPerson> people, YearMonth month) {
            SupplyTerms terms = supplyTerms();
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
                    // 같은 묶음(Settings) 안에서는 기준값을 코드·달마다 한 번만 읽는다(철수 줄마다 다시 읽지 않음).
                    BigDecimal probability = probabilities.computeIfAbsent(standard + "|" + month,
                        key -> definitions.effectiveStandard(standard, month).value());
                    if (probability.signum() < 0 || probability.compareTo(BigDecimal.valueOf(100)) > 0)
                        throw CodeFailure.invalid("calculation", "운영 기준값 " + standard + "을(를) 확인해 주세요.");
                    extended = extended.add(extension.monthlyMm().multiply(probability).movePointLeft(2));
                }
                BigDecimal available = person.confirmedMm().signum() == 0 && person.extensions().isEmpty()
                    ? terms.idle() : terms.capacity().subtract(person.confirmedMm()).subtract(extended).max(BigDecimal.ZERO);
                total = total.add(available);
            }
            return total.setScale(terms.scale(), RoundingMode.HALF_UP);
        }

        /** 호출자는 대상 월에 유효한 진행 기회와 계획을 가진 진행 프로젝트만 전달한다. */
        public BigDecimal demand(List<OpportunityDemand> opportunities, List<ProjectDemand> projects) {
            if (!demandRead) {
                text("식-61");
                demandRead = true;
            }
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
    }

    // 한 번만 쓰는 곳(시험·단건 호출)은 아래로 부른다. 목록처럼 여러 번 쓰는 곳은 settings() 하나를 돌려쓴다.
    public int nearTermMonthOffset() { return settings().nearTermMonthOffset(); }
    public int remainingDays(LocalDate endDate, LocalDate today) { return settings().remainingDays(endDate, today); }
    public boolean nearTerm(LocalDate endDate, LocalDate today) { return settings().nearTerm(endDate, today); }
    public int candidateWindowMonths() { return settings().candidateWindowMonths(); }
    public boolean candidatePeriod(LocalDate endDate, YearMonth needStart, int durationMonths) {
        return settings().candidatePeriod(endDate, needStart, durationMonths);
    }
    public YearMonth tentativeStartMonth(LocalDate endDate, YearMonth needStart) {
        return settings().tentativeStartMonth(endDate, needStart);
    }
    public int waitingDays(LocalDate endDate, YearMonth startMonth) { return settings().waitingDays(endDate, startMonth); }
    public BigDecimal aiRecommendScore(ScoreInput input) { return settings().aiRecommendScore(input); }
    public boolean bannerCandidate(LocalDate endDate, LocalDate today,
        String extensionPossibilityCode, boolean hasOutstandingTentative) {
        return settings().bannerCandidate(endDate, today, extensionPossibilityCode, hasOutstandingTentative);
    }
    public BigDecimal supply(List<SupplyPerson> people, YearMonth month) { return settings().supply(people, month); }
    public BigDecimal demand(List<OpportunityDemand> opportunities, List<ProjectDemand> projects) {
        return settings().demand(opportunities, projects);
    }

    private static int whole(String text, String regex, String id, int fallback) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        if (matcher.find()) {
            try { return Integer.parseInt(matcher.group(1)); }
            catch (NumberFormatException ignored) { /* 아래에서 명세 값을 쓴다 */ }
        }
        log.warn("{} 정의에서 값을 읽지 못해 명세 값 {}을(를) 씁니다.", id, fallback);
        return fallback;
    }

    private static BigDecimal decimal(String text, String regex, int group, String id, String fallback) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        if (matcher.find()) return new BigDecimal(matcher.group(group));
        log.warn("{} 정의에서 값을 읽지 못해 명세 값 {}을(를) 씁니다.", id, fallback);
        return new BigDecimal(fallback);
    }
}
