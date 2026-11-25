package com.company.common.airecommendations.service;

import com.company.common.airecommendations.dto.AiRecommendationContracts.*;
import com.company.common.airecommendations.mapper.AiRecommendationMapper;
import com.company.common.airecommendations.mapper.AiRecommendationRows.*;
import com.company.common.common.TentativeTransfers;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.salesopportunities.mapper.SalesOpportunityMapper;
import com.company.common.support.service.*;
import com.company.common.withdrawals.dto.WithdrawalContracts.TentativeAssignment;
import com.company.common.withdrawals.mapper.WithdrawalMapper;
import com.company.common.withdrawals.mapper.WithdrawalRows.ReservationWrite;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AiRecommendationActionService {
    private final AiRecommendationMapper mapper;
    private final SalesOpportunityMapper opportunities;
    private final WithdrawalMapper withdrawals;
    private final ProjectMapper projects;
    private final AiRecommendationResources resources;
    private final CurrentBusinessActor actor;

    public AiRecommendationActionService(AiRecommendationMapper mapper, SalesOpportunityMapper opportunities,
        WithdrawalMapper withdrawals, ProjectMapper projects, AiRecommendationResources resources, CurrentBusinessActor actor) {
        this.mapper = mapper; this.opportunities = opportunities; this.withdrawals = withdrawals;
        this.projects = projects; this.resources = resources; this.actor = actor;
    }
    private Log candidate(String id, String developerId) {
        if (id == null || !id.matches("[1-9][0-9]{0,11}")) throw CodeFailure.invalid("recommendationId", "추천 실행을 확인해 주세요.");
        CommonAiRecommendationService.text(developerId, "developerId", 20);
        Log log = mapper.lockCandidate(id, developerId);
        if (log == null) throw CodeFailure.invalid("developerId", "추천 후보를 확인해 주세요.");
        return log;
    }
    private void active(Log log) {
        Target target = mapper.target(log.targetType(), log.targetId());
        if (target == null || !"ING".equals(target.status())) throw CodeFailure.invalid("targetId", "진행 중인 대상을 골라 주세요.");
        if (log.userActCode() != null && !"TENT".equals(log.userActCode()))
            throw CodeFailure.invalid("developerId", "이미 처리한 추천 후보입니다.");
    }
    public AssignmentResult assignment(String id, AssignmentInput input) {
        CommonAiRecommendationService.text(input.assignmentId(), "assignmentId", 20);
        Log log = candidate(id, input.developerId());
        var assignment = mapper.assignment(input.assignmentId());
        if ("EXCL".equals(log.userActCode())) throw CodeFailure.invalid("developerId", "이미 제외한 추천 후보입니다.");
        if (assignment == null) throw CodeFailure.invalid("assignmentId", "저장한 투입을 확인해 주세요.");
        if (!"project".equals(log.targetType()) || !log.targetId().equals(assignment.projectId())
            || !log.developerId().equals(assignment.developerId()))
            throw CodeFailure.invalid("assignmentId", "추천 후보와 투입의 개발자·프로젝트가 일치하지 않습니다.");
        if (mapper.action(new Action(id, input.developerId(), "ASGN", log.needSn(), null, actor.userId())) != 1) throw CodeFailure.conflict();
        return new AssignmentResult(id, input.developerId(), input.assignmentId(), "ASGN");
    }
    public ExclusionResult exclusion(String id, ExclusionInput input) {
        if (input.reasonCode() == null || resources.codes().selectable("RCMD_EXCL").stream()
            .noneMatch(c -> c.code.equals(input.reasonCode()))) throw CodeFailure.invalid("reasonCode", "제외 사유를 골라 주세요.");
        Log observed = mapper.candidate(validId(id), input.developerId());
        if (observed == null) throw CodeFailure.invalid("developerId", "추천 후보를 확인해 주세요.");
        projects.lockSources();
        if ("opportunity".equals(observed.targetType())) opportunities.lock(observed.targetId());
        else projects.lockProject(observed.targetId());
        Log log = candidate(id, input.developerId());
        active(log);
        String detail = resources.codec().exclusionActive(log.scoreDetail(), true);
        if (mapper.exclude(new ExclusionWrite(new Action(id, input.developerId(), "EXCL", log.needSn(), input.reasonCode(), actor.userId()), detail)) != 1)
            throw CodeFailure.conflict();
        Log saved = mapper.candidate(id, input.developerId());
        return new ExclusionResult(id, input.developerId(), true, saved.reasonCode(), saved.actedBy(), saved.actedAt());
    }
    private static String validId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,11}")) throw CodeFailure.invalid("recommendationId", "추천 실행을 확인해 주세요.");
        return id;
    }
    public TentativeResult tentative(String id, TentativeInput input) {
        CommonAiRecommendationService.text(input.developerId(), "developerId", 20);
        if (input.needSn() == null || input.needSn() < 1 || input.needSn() > 999)
            throw CodeFailure.invalid("needSn", "이 기회의 필요 인력 줄을 골라 주세요.");
        Log observed = mapper.candidate(validId(id), input.developerId());
        if (observed == null || !"opportunity".equals(observed.targetType()))
            throw CodeFailure.invalid("developerId", "기회의 추천 후보를 확인해 주세요.");
        projects.lockSources();
        if (withdrawals.lockDeveloper(input.developerId()) == null) throw CodeFailure.conflict();
        var current = withdrawals.reservations(input.developerId());
        var affected = new TreeSet<String>(); affected.add(observed.targetId());
        current.forEach(r -> affected.add(r.opportunityId()));
        opportunities.lockOpportunities(affected);
        withdrawals.lockNeeds();
        Log log = candidate(id, input.developerId());
        active(log);
        if (current.stream().anyMatch(r -> "CNVT".equals(r.statusCode()))
            || !opportunities.developerConversions(input.developerId()).isEmpty())
            throw new CodeFailure(409, "needSn", "투입 배정으로 넘긴 후보입니다. 투입 상세에서 확인해 주세요.");
        var need = opportunities.needs(log.targetId()).stream().filter(n -> Objects.equals(n.needSn(), input.needSn()))
            .findFirst().orElseThrow(() -> CodeFailure.invalid("needSn", "이 기회의 필요 인력 줄을 골라 주세요."));
        var same = current.stream().filter(r -> r.opportunityId().equals(log.targetId()) && r.needSn() == input.needSn()).findFirst();
        String startMonth;
        String released = current.stream().filter(r -> !r.opportunityId().equals(log.targetId()))
            .map(r -> r.opportunityName()).findFirst().orElse(null);
        if (same.isPresent()) startMonth = same.get().startMonth();
        else {
            YearMonth month = resources.calendar().fromDatabaseMonth(log.startMonth());
            var condition = new Condition(log.targetType(), log.targetId(), log.jobCode(), month.toString(), log.gradeCode(), log.needSn());
            var target = mapper.target(log.targetType(), log.targetId());
            var person = mapper.person(log.developerId());
            boolean eligible = mapper.candidates(new Filter(condition, month.atDay(1), month.atEndOfMonth(), resources.calendar().today(), true))
                .stream().anyMatch(p -> p.developerId().equals(log.developerId()));
            if (!eligible || "DP".equals(target.contractTypeCode()) && Set.of("FL", "OS").contains(person.employeeTypeCode()))
                throw CodeFailure.invalid("developerId", "현재 인력은 신규 추천 대상이 아닙니다.");
            var evidence = mapper.evidence(new EvidenceFilter(log.developerId(), target.customerId(), target.industryCode(), resources.calendar().today(), month.atDay(1)));
            if (evidence.withdrawalDate() != null) {
                YearMonth after = YearMonth.from(evidence.withdrawalDate()).plusMonths(1);
                if (after.isAfter(month)) month = after;
            }
            startMonth = resources.calendar().toDatabaseMonth(month);
            String savedMonth = startMonth;
            var previous = current.stream().filter(r -> "ACT".equals(r.statusCode()))
                .map(r -> new TentativeTransfers.Previous(r.tentativeId(), r.opportunityId())).toList();
            TentativeTransfers.apply(new TentativeTransfers.Mutation(previous, log.targetId(), () -> {
                if (withdrawals.insertReservation(new ReservationWrite(log.developerId(), log.targetId(), input.needSn(), savedMonth, actor.userId(), "UI-30")) != 1)
                    throw CodeFailure.conflict();
            }), old -> withdrawals.release(old, actor.userId()), opportunity -> withdrawals.touchOpportunity(opportunity, actor.userId()));
        }
        if (mapper.action(new Action(id, input.developerId(), "TENT", input.needSn(), null, actor.userId())) != 1) throw CodeFailure.conflict();
        var header = opportunities.select(log.targetId());
        int occupied = (int) opportunities.reservations(log.targetId()).stream().filter(r -> r.needSn() == input.needSn()).count();
        int excess = Math.max(0, occupied - need.requiredCount());
        String jobName = resources.codes().name("JOB", need.jobCode());
        String name = mapper.person(log.developerId()).developerName();
        String message = name + "님을 「" + header.opportunityName() + "」 " + input.needSn() + "번 " + jobName + " 자리에 가배정했습니다."
            + (released == null ? "" : " 이전 「" + released + "」 가배정을 해제했습니다.")
            + (excess == 0 ? "" : " 이 자리는 " + excess + "명을 넘었습니다.")
            + " 영업 기회 상세에서 가배정 인력을 볼 수 있습니다.";
        return new TentativeResult(new TentativeAssignment("", log.developerId(), log.targetId(), header.opportunityName(),
            input.needSn(), resources.calendar().fromDatabaseMonth(startMonth).toString()), released, excess, message);
    }
}
