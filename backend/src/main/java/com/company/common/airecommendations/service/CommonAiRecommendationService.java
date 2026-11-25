package com.company.common.airecommendations.service;

import com.company.common.airecommendations.dto.AiRecommendationContracts.*;
import com.company.common.airecommendations.mapper.AiRecommendationMapper;
import com.company.common.airecommendations.mapper.AiRecommendationRows.*;
import com.company.common.common.AiRecommendationExecutionJournal;
import com.company.common.common.AiRecommendationLogCodec;
import com.company.common.personalcostprofits.service.PersonalCostProfitRules;
import com.company.common.salesopportunities.mapper.SalesOpportunityMapper;
import com.company.common.support.service.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    public Result postCommonAiRecommendation(Input input) {
        if (input == null) throw CodeFailure.invalid("targetId", "추천 조건을 확인해 주세요.");
        text(input.requestId(), "requestId", Integer.MAX_VALUE);
        return resources.journal().locked(input.requestId(), entry -> {
            Result result = transaction.execute(status -> {
                String fingerprint = AiRecommendationExecutionJournal.fingerprint(input.requestId());
                mapper.lockRequest(fingerprint);
                Condition condition = normalize(input);
                var tree = resources.json().valueToTree(condition);
                var receipt = entry.matching(tree);
                var existing = mapper.replay(fingerprint);
                if (receipt != null && receipt.result() != null) {
                    return resources.json().convertValue(receipt.result(), Result.class);
                }
                if (!existing.isEmpty()) {
                    Result restored = restore(existing);
                    if (!condition.equals(restored.condition())) throw CodeFailure.invalid("requestId", "같은 추천 요청의 조건이 다릅니다. 새 요청으로 실행해 주세요.");
                    if (receipt == null) entry.pending(restored.recommendationId(), tree);
                    return restored;
                }
                if (receipt != null) entry.discardPending();
                String id = mapper.nextId();
                var cards = calculate(condition);
                entry.pending(id, tree);
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
            if (entry.read().result() == null) {
                entry.complete(resources.json().valueToTree(result));
            }
            return result;
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
            YearMonth start = resources.calendar().fromDatabaseMonth(target.startMonth());
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
    private List<Card> calculate(Condition condition) {
        resources.definitions().definitions();
        var month = month(condition.startMonth());
        var target = requiredTarget(condition.targetType(), condition.targetId());
        var parents = new HashMap<String, String>();
        mapper.areas().forEach(a -> parents.put(a.code(), a.parentCode()));
        var context = new AiRecommendationRules.Context(target, condition.jobCode(), condition.gradeCode(), month,
            resources.codes().selectable("JOB"), resources.codes().selectable("GRADE"), resources.codes().selectable("SKILL"),
            parents, resources.definitions().currentMaxima());
        var result = new ArrayList<Card>();
        for (Person person : mapper.candidates(new Filter(condition, month.atDay(1), month.atEndOfMonth(), resources.calendar().today(), false))) {
            if ("DP".equals(target.contractTypeCode()) && Set.of("FL", "OS").contains(person.employeeTypeCode())) continue;
            var evidence = mapper.evidence(new EvidenceFilter(person.developerId(), target.customerId(), target.industryCode(),
                resources.calendar().today(), month.atDay(1)));
            var pay = mapper.payroll(person.developerId(), month.toString().replace("-", ""));
            BigDecimal cost = pay == null ? null : PersonalCostProfitRules.payroll(null, pay,
                new PersonalCostProfitRules.Rates(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), true).get("personalCost");
            var price = mapper.price(person.developerId(), month.toString().replace("-", ""));
            BigDecimal rate = price == null ? null : price.monthlyPrice();
            var scores = AiRecommendationRules.score(context, new AiRecommendationRules.Candidate(person, evidence,
                mapper.skills(person.developerId()), cost, rate));
            result.add(card(person, scores, AiRecommendationRules.reason(scores), condition, new CostBasis(cost, rate)));
        }
        return result.stream().sorted(Comparator.comparing(Card::totalScore).reversed()
            .thenComparing(c -> c.scores().get("availability").score(), Comparator.reverseOrder())
            .thenComparing(Card::managementNumber).thenComparing(Card::developerId)).limit(5).toList();
    }
    private Card card(Person person, Map<String, ScoreItem> scores, String reason, Condition condition, CostBasis cost) {
        var other = opportunities.developerReservations(person.developerId()).stream()
            .filter(r -> !"opportunity".equals(condition.targetType()) || !condition.targetId().equals(r.opportunityId()))
            .map(r -> r.opportunityName()).findFirst().orElse(null);
        boolean converted = !opportunities.developerConversions(person.developerId()).isEmpty();
        return new Card(person.developerId(), person.developerName(), person.managementNumber(), person.jobName(),
            person.gradeName(), person.employeeTypeName(), scores.values().stream().map(ScoreItem::score)
                .reduce(BigDecimal.ZERO, BigDecimal::add), scores, reason, other, converted, cost);
    }
    private Result restore(List<Log> logs) {
        Log first = logs.get(0);
        var snapshot = resources.codec().decode(first.scoreDetail());
        var condition = new Condition(first.targetType(), first.targetId(), first.jobCode(),
            resources.calendar().fromDatabaseMonth(first.startMonth()).toString(), first.gradeCode(), snapshot.metadata().originalNeedSn());
        var cards = logs.stream().map(log -> card(mapper.person(log.developerId()),
            AiRecommendationRules.restore(resources.codec().decode(log.scoreDetail()).scores()), log.reason(), condition, null)).toList();
        return new Result(first.recommendationId(), condition, cards);
    }
}
