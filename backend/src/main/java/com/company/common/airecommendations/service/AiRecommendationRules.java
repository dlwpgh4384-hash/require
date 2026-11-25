package com.company.common.airecommendations.service;

import com.company.common.airecommendations.dto.AiRecommendationContracts.ScoreItem;
import com.company.common.airecommendations.mapper.AiRecommendationRows.*;
import com.company.common.codegroups.dto.CodeContracts.Item;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Pattern;

public final class AiRecommendationRules {
    private AiRecommendationRules() {}
    public static final List<String> DIMENSIONS = List.of("job", "skill", "customerIndustry", "grade",
        "performance", "cost", "availability", "workplace");
    private static final List<Integer> BASE_MAXIMA = List.of(25, 20, 15, 10, 5, 5, 10, 10);
    private static final List<String> NAMES = List.of("직무", "스킬", "고객사·업종", "등급", "과거 성과", "비용 효과", "투입 가능일", "근무지");
    public record Context(Target target, String jobCode, String gradeCode, YearMonth month,
        List<Item> jobs, List<Item> grades, List<Item> skills, Map<String, String> parents,
        Map<String, BigDecimal> maxima) {}
    public record Candidate(Person person, Evidence evidence, List<String> skills, BigDecimal cost, BigDecimal price) {}

    public static boolean linked(String linkage, String code) {
        return linkage != null && code != null && Arrays.stream(linkage.split("[,|;\\s]+" )).anyMatch(code::equals);
    }
    public static boolean mentions(String text, String skill) {
        return text != null && skill != null && !skill.isBlank()
            && Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(skill) + "(?![\\p{L}\\p{N}_])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find();
    }
    public static int workplace(String work, String home, Map<String, String> parents) {
        if (work == null || work.isBlank()) return 5;
        String a = root(work, parents), b = root(home, parents);
        return a != null && a.equals(b) ? 10 : 6;
    }
    private static String root(String code, Map<String, String> parents) {
        var seen = new HashSet<String>();
        while (code != null && parents.containsKey(code) && seen.add(code)) {
            String parent = parents.get(code);
            if (parent == null || parent.isBlank()) return code;
            code = parent;
        }
        return null;
    }
    public static int performance(Evidence history) {
        if (history.endedCount() == 0) return 3;
        return Math.max(0, Math.min(5, BigDecimal.valueOf(history.extendedCount()).multiply(BigDecimal.valueOf(5))
            .divide(BigDecimal.valueOf(history.endedCount()), 0, RoundingMode.HALF_UP).intValue()
            - history.hrWithdrawalCount() * 2));
    }
    public static int cost(BigDecimal cost, BigDecimal price) {
        if (cost == null || price == null || price.signum() <= 0) return 3;
        return BigDecimal.valueOf(5).subtract(cost.divide(price, 12, RoundingMode.HALF_UP)
            .subtract(new BigDecimal("0.6")).multiply(BigDecimal.TEN)).setScale(0, RoundingMode.HALF_UP)
            .max(BigDecimal.ONE).min(BigDecimal.valueOf(5)).intValue();
    }
    public static Map<String, ScoreItem> score(Context context, Candidate candidate) {
        Person person = candidate.person();
        Evidence evidence = candidate.evidence();
        boolean direct = context.jobCode().equals(person.jobCode()) || context.jobCode().equals(person.subJobCode());
        boolean related = context.jobs().stream().filter(j -> context.jobCode().equals(j.code))
            .anyMatch(j -> linked(j.relatedJobs, person.jobCode()) || linked(j.relatedJobs, person.subJobCode()));
        var owned = new HashSet<String>();
        candidate.skills().forEach(s -> owned.add(s.toLowerCase(Locale.ROOT)));
        var required = context.skills().stream().filter(s -> linked(s.requiredJobs, context.jobCode())).toList();
        long matching = required.stream().filter(s -> owned.contains(s.codeName.toLowerCase(Locale.ROOT))).count();
        int skill = required.isEmpty() ? 0 : BigDecimal.valueOf(matching).multiply(BigDecimal.valueOf(16))
            .divide(BigDecimal.valueOf(required.size()), 0, RoundingMode.HALF_UP).intValue();
        boolean feature = candidate.skills().stream().anyMatch(s -> mentions(context.target().feature(), s))
            || context.skills().stream().filter(s -> owned.contains(s.codeName.toLowerCase(Locale.ROOT)))
                .anyMatch(s -> mentions(context.target().feature(), s.code));
        if (feature) skill += 4;
        int selected = gradeIndex(context.grades(), context.gradeCode());
        int actual = gradeIndex(context.grades(), person.gradeCode());
        int grade = actual < 0 ? 2 : actual == selected ? 10 : Math.abs(actual - selected) == 1 ? 5 : 2;
        int availability = evidence.withdrawalDate() == null || evidence.withdrawalDate().isBefore(context.month().atDay(1))
            ? 10 : evidence.lowExtension() ? 6 : 2;
        var base = List.of(direct ? 25 : related ? 12 : 3, skill,
            evidence.sameCustomer() ? 15 : evidence.sameIndustry() ? 9 : 6, grade, performance(evidence),
            cost(candidate.cost(), candidate.price()), availability,
            workplace(context.target().workAreaCode(), person.homeAreaCode(), context.parents()));
        var result = new LinkedHashMap<String, ScoreItem>();
        for (int i = 0; i < DIMENSIONS.size(); i++) {
            String key = DIMENSIONS.get(i);
            BigDecimal maximum = context.maxima().get(key);
            BigDecimal awarded = BigDecimal.valueOf(base.get(i)).multiply(maximum)
                .divide(BigDecimal.valueOf(BASE_MAXIMA.get(i)), 0, RoundingMode.HALF_UP).min(maximum).max(BigDecimal.ZERO);
            result.put(key, new ScoreItem(NAMES.get(i), awarded, maximum));
        }
        return result;
    }
    private static int gradeIndex(List<Item> grades, String code) {
        for (int i = 0; i < grades.size(); i++) if (grades.get(i).code.equals(code)) return i;
        return -1;
    }
    public static Map<String, ScoreItem> restore(Map<String, AiRecommendationLogCodec.Score> scores) {
        var result = new LinkedHashMap<String, ScoreItem>();
        for (int i = 0; i < DIMENSIONS.size(); i++) {
            var score = scores.get(DIMENSIONS.get(i));
            result.put(DIMENSIONS.get(i), new ScoreItem(NAMES.get(i), plain(score.score()), plain(score.maximumScore())));
        }
        return result;
    }
    /** 기록에서 읽은 10은 1E+1로 줄어 있으므로 처음 응답과 같게 10으로 돌린다. */
    private static BigDecimal plain(BigDecimal value) {
        return value.scale() < 0 ? value.setScale(0) : value;
    }
    /** 카드 아래 추천 이유(화면 시안 UI-30 `.why` 그대로): 주 직무 같음 → 같은 고객사 경험 → 투입 가능 → 보유 스킬. */
    public static String reason(Context context, Candidate candidate) {
        var parts = new ArrayList<String>();
        if (context.jobCode().equals(candidate.person().jobCode())) parts.add("요청 직무와 주 직무가 같습니다");
        Evidence evidence = candidate.evidence();
        if (evidence.sameCustomer() && context.target().customerName() != null)
            parts.add(context.target().customerName() + " 프로젝트 경험이 있습니다");
        // withdrawalDate = 오늘 진행 중인 투입의 철수일(없으면 null = 진행 중 투입 없음). 지난 투입의 철수일은 쓰지 않는다.
        if (evidence.withdrawalDate() == null) parts.add("지금 대기 중입니다");
        else if (evidence.withdrawalDate().isBefore(context.month().atDay(1)))
            parts.add(evidence.withdrawalDate() + " 철수 후 바로 투입할 수 있습니다");
        else parts.add("시작월에 다른 프로젝트에 투입 중입니다");
        if (!candidate.skills().isEmpty()) parts.add("보유 스킬: " + String.join(", ", candidate.skills()));
        return String.join(". ", parts) + ".";
    }
}
