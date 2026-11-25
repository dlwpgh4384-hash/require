package com.company.common.airecommendations.service;

import com.company.common.airecommendations.dto.AiRecommendationContracts.*;
import com.company.common.airecommendations.mapper.AiRecommendationMapper;
import com.company.common.airecommendations.mapper.AiRecommendationRows.*;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Pay;
import com.company.common.personalcostprofits.service.PersonalCostProfitRules;
import com.company.common.salesopportunities.mapper.SalesOpportunityMapper;
import com.company.common.support.service.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static com.company.common.airecommendations.mapper.AiRecommendationRows.NO_EVIDENCE;

@Service
public class CommonAiRecommendationService {
    private final AiRecommendationMapper mapper;
    private final SalesOpportunityMapper opportunities;
    private final AiRecommendationResources resources;
    private final CurrentBusinessActor actor;
    private final TransactionTemplate transaction;

    public CommonAiRecommendationService(AiRecommendationMapper mapper, SalesOpportunityMapper opportunities,
        AiRecommendationResources resources, CurrentBusinessActor actor, PlatformTransactionManager transactions) {
        this.mapper = mapper;
        this.opportunities = opportunities;
        this.resources = resources;
        this.actor = actor;
        this.transaction = new TransactionTemplate(transactions);
    }

    /** 같은 requestId는 DB 잠금(pg_advisory_xact_lock) 안에서 TB_SL_RCMD_LOG 기록을 다시 돌려준다(추천 1번 = 기록 1번). */
    public Result postCommonAiRecommendation(Input input) {
        if (input == null) throw CodeFailure.invalid("targetId", "추천 조건을 확인해 주세요.");
        text(input.requestId(), "requestId", Integer.MAX_VALUE);
        return transaction.execute(status -> {
            String fingerprint = AiRecommendationLogCodec.fingerprint(input.requestId());
            mapper.lockRequest(fingerprint);
            Condition condition = normalize(input);
            var existing = mapper.replay(fingerprint, condition.targetType(), condition.targetId());
            if (!existing.isEmpty()) {
                Result restored = restore(existing);
                if (!condition.equals(restored.condition())) throw CodeFailure.invalid("requestId", "같은 추천 요청의 조건이 다릅니다. 새 요청으로 실행해 주세요.");
                return restored;
            }
            String id = mapper.nextId();
            var cards = calculate(condition);
            int rank = 0;
            for (Card card : cards) {
                var scores = new LinkedHashMap<String, AiRecommendationLogCodec.Score>();
                card.scores().forEach((key, score) -> scores.put(key,
                    new AiRecommendationLogCodec.Score(score.score(), score.maximumScore())));
                String snapshot = resources.codec().encode(new AiRecommendationLogCodec.Snapshot(scores,
                    new AiRecommendationLogCodec.Metadata(fingerprint, condition.needSn(), null)));
                var row = new Log(id, ++rank, condition.targetType(), condition.targetId(), condition.jobCode(),
                    condition.startMonth().replace("-", ""), condition.gradeCode(), condition.needSn(),
                    card.developerId(), card.totalScore(), snapshot, resources.codec().reason(card.reason()),
                    null, null, null, null);
                if (mapper.insert(new LogWrite(row, actor.userId())) != 1) throw CodeFailure.conflict();
            }
            return new Result(id, condition, cards);
        });
    }

    Condition normalize(Input input) {
        if (!Set.of("project", "opportunity").contains(Objects.requireNonNullElse(input.targetType(), "")))
            throw CodeFailure.invalid("targetType", "대상을 골라 주세요.");
        text(input.targetId(), "targetId", 20);
        code("JOB", input.jobCode(), "jobCode");
        code("GRADE", input.gradeCode(), "gradeCode");
        YearMonth month = month(input.startMonth());
        Target target = requiredTarget(input.targetType(), input.targetId());
        if ("project".equals(input.targetType())) {
            // 프로젝트 시작월 = max(요청 월, 이번 달(한국 달력), 프로젝트 시작월)(점검 목록 L-13). 지난 달(마감 달)은
            // 확정 투입 월 MM(TB_PJ_ASGN_MON)이 없어 방금 배정한 사람도 다시 추천되므로 쓰지 않는다.
            YearMonth start = resources.calendar().fromDatabaseMonth(target.startMonth());
            YearMonth current = resources.calendar().currentMonth();
            if (start.isBefore(current)) start = current;
            if (month.isBefore(start)) month = start;
            if (target.endMonth() != null && month.isAfter(resources.calendar().fromDatabaseMonth(target.endMonth())))
                throw CodeFailure.invalid("startMonth", "시작월이 프로젝트 종료월 뒤입니다.");
            if (input.needSn() != null) throw CodeFailure.invalid("needSn", "기회의 필요 인력 줄만 선택할 수 있습니다.");
        } else if (input.needSn() != null && opportunities.needs(input.targetId()).stream()
            .noneMatch(n -> Objects.equals(n.needSn(), input.needSn()))) {
            throw CodeFailure.invalid("needSn", "이 기회의 필요 인력 줄을 골라 주세요.");
        }
        return new Condition(input.targetType(), input.targetId(), input.jobCode(), month.toString(), input.gradeCode(), input.needSn());
    }
    Target requiredTarget(String type, String id) {
        Target target = mapper.target(type, id);
        if (target == null || !"ING".equals(target.status()) || "IN".equals(target.contractTypeCode()))
            throw CodeFailure.invalid("targetId", "진행 중인 대상을 골라 주세요.");
        return target;
    }
    void code(String group, String code, String field) {
        if (code == null || resources.codes().selectable(group).stream().noneMatch(c -> code.equals(c.code)))
            throw CodeFailure.invalid(field, "유효한 값을 골라 주세요.");
    }
    static void text(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum)
            throw CodeFailure.invalid(field, "입력값을 확인해 주세요.");
    }
    static YearMonth month(String value) {
        try {
            if (value == null || !value.matches("[0-9]{4}-[0-9]{2}")) throw new IllegalArgumentException();
            YearMonth month = YearMonth.parse(value);
            if (month.getYear() < 1) throw new IllegalArgumentException();
            return month;
        } catch (RuntimeException exception) { throw CodeFailure.invalid("startMonth", "올바른 시작월을 입력해 주세요."); }
    }
    /** 후보 근거(투입 이력·급여·단가·스킬·가배정)는 후보 id 목록으로 표마다 한 번씩 읽는다. */
    private List<Card> calculate(Condition condition) {
        resources.definitions().definitions();
        var month = month(condition.startMonth());
        var target = requiredTarget(condition.targetType(), condition.targetId());
        var parents = new HashMap<String, String>();
        mapper.areas().forEach(a -> parents.put(a.code(), a.parentCode()));
        var context = new AiRecommendationRules.Context(target, condition.jobCode(), condition.gradeCode(), month,
            resources.codes().selectable("JOB"), resources.codes().selectable("GRADE"), resources.codes().selectable("SKILL"),
            parents, resources.definitions().currentMaxima());
        var people = mapper.candidates(new Filter(condition, month.atDay(1), month.atEndOfMonth(), resources.calendar().today(), false))
            .stream().filter(p -> !("DP".equals(target.contractTypeCode()) && Set.of("FL", "OS").contains(p.employeeTypeCode())))
            .toList();
        if (people.isEmpty()) return List.of();
        var ids = people.stream().map(Person::developerId).toList();
        String yearMonth = month.toString().replace("-", "");
        var evidence = new HashMap<String, Evidence>();
        mapper.evidences(new EvidencesFilter(ids, target.customerId(), target.industryCode(),
            resources.calendar().today(), month.atDay(1))).forEach(r -> evidence.put(r.developerId(), r.evidence()));
        var pays = new HashMap<String, Pay>();
        mapper.payrolls(ids, yearMonth).forEach(p -> pays.putIfAbsent(p.developerId(), p));
        var prices = new HashMap<String, BigDecimal>();
        mapper.prices(ids, yearMonth).forEach(p -> prices.putIfAbsent(p.developerId(), p.monthlyPrice()));
        var skills = new HashMap<String, List<String>>();
        mapper.skills(ids).forEach(s -> skills.computeIfAbsent(s.developerId(), k -> new ArrayList<>()).add(s.skillName()));
        var extras = extras(ids, condition);
        var result = new ArrayList<Card>();
        for (Person person : people) {
            var pay = pays.get(person.developerId());
            BigDecimal cost = pay == null ? null : PersonalCostProfitRules.payroll(null, pay,
                new PersonalCostProfitRules.Rates(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), true).get("personalCost");
            var candidate = new AiRecommendationRules.Candidate(person,
                evidence.getOrDefault(person.developerId(), NO_EVIDENCE),
                List.copyOf(skills.getOrDefault(person.developerId(), List.of())), cost, prices.get(person.developerId()));
            var scores = AiRecommendationRules.score(context, candidate);
            result.add(card(person, scores, AiRecommendationRules.reason(context, candidate), extras));
        }
        return result.stream().sorted(Comparator.comparing(Card::totalScore).reversed()
            .thenComparing(c -> c.scores().get("availability").score(), Comparator.reverseOrder())
            .thenComparing(Card::managementNumber).thenComparing(Card::developerId)).limit(5).toList();
    }
    /** 다른 기회 가배정 이름(사람마다 첫 줄)과 투입 배정 후보(CNVT) 여부. */
    private record Extras(Map<String, String> otherTentatives, Set<String> converted) {}
    private Extras extras(Collection<String> ids, Condition condition) {
        var other = new HashMap<String, String>();
        for (var r : opportunities.developersReservations(ids)) {
            if (!"opportunity".equals(condition.targetType()) || !condition.targetId().equals(r.opportunityId()))
                other.putIfAbsent(r.developerId(), r.opportunityName());
        }
        return new Extras(other, new HashSet<>(mapper.conversions(ids)));
    }
    private Card card(Person person, Map<String, ScoreItem> scores, String reason, Extras extras) {
        return new Card(person.developerId(), person.developerName(), person.managementNumber(), person.jobName(),
            person.gradeName(), person.employeeTypeName(), scores.values().stream().map(ScoreItem::score)
                .reduce(BigDecimal.ZERO, BigDecimal::add), scores, reason,
            extras.otherTentatives().get(person.developerId()), extras.converted().contains(person.developerId()));
    }
    private Result restore(List<Log> logs) {
        Log first = logs.get(0);
        var snapshot = resources.codec().decode(first.scoreDetail());
        var condition = new Condition(first.targetType(), first.targetId(), first.jobCode(),
            resources.calendar().fromDatabaseMonth(first.startMonth()).toString(), first.gradeCode(), snapshot.metadata().originalNeedSn());
        var ids = logs.stream().map(Log::developerId).distinct().toList();
        var people = new HashMap<String, Person>();
        mapper.people(ids).forEach(p -> people.put(p.developerId(), p));
        var extras = extras(ids, condition);
        var cards = logs.stream().map(log -> {
            Person person = people.get(log.developerId());
            if (person == null) throw CodeFailure.conflict();
            return card(person, AiRecommendationRules.restore(resources.codec().decode(log.scoreDetail()).scores()),
                log.reason(), extras);
        }).toList();
        return new Result(first.recommendationId(), condition, cards);
    }
}
