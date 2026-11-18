package com.company.common.withdrawals.service;

import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.withdrawals.dto.WithdrawalContracts.*;
import com.company.common.withdrawals.mapper.WithdrawalMapper;
import com.company.common.withdrawals.mapper.WithdrawalRows.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WithdrawalRecommendationService {
    private final WithdrawalMapper mapper;
    private final WithdrawalCalculationRules rules;
    private final WithdrawalCodes codes;
    private final BusinessCalendar calendar;

    public WithdrawalRecommendationService(WithdrawalMapper mapper, WithdrawalCalculationRules rules,
        WithdrawalCodes codes, BusinessCalendar calendar) {
        this.mapper = mapper;
        this.rules = rules;
        this.codes = codes;
        this.calendar = calendar;
    }

    public boolean eligible(Source source) {
        return "ACT".equals(source.statusCode()) && !"H".equals(source.extensionPossibilityCode())
            && rules.nearTerm(source.endDate(), calendar.today());
    }

    public boolean matches(Source source, Need need) {
        YearMonth start = calendar.fromDatabaseMonth(need.startMonth());
        YearMonth available = YearMonth.from(source.endDate()).plusMonths(1);
        return "ING".equals(need.statusCode())
            && (Objects.equals(source.primaryJobCode(), need.jobCode())
                || codes.relatedJob(source.primaryJobCode(), need.jobCode()))
            && !start.isBefore(available)
            && rules.candidatePeriod(source.endDate(), start, need.durationMonths());
    }

    public List<Need> candidates(Source source, List<Reservation> current, boolean replaceCurrent) {
        if (!eligible(source) || (!current.isEmpty() && !replaceCurrent)
            || current.stream().anyMatch(r -> "CNVT".equals(r.statusCode()))) return new ArrayList<>();
        return mapper.needs().stream().filter(n -> matches(source, n))
            .filter(n -> n.occupiedCount() < n.requiredCount())
            .filter(n -> current.stream().noneMatch(r -> sameNeed(r, n))).toList();
    }

    public static boolean sameNeed(Reservation reservation, Need need) {
        return reservation.opportunityId().equals(need.opportunityId()) && reservation.needSn() == need.needSn();
    }

    public Recommendations recommendations(Source source, boolean replaceCurrent) {
        var current = mapper.reservations(source.developerId());
        var candidates = candidates(source, current, replaceCurrent);
        var items = candidates.stream().map(n -> recommendation(source, n))
            .sorted(Comparator.comparing(Recommendation::score).reversed()
                .thenComparing(Recommendation::startMonth)
                .thenComparing(Recommendation::opportunityId).thenComparingInt(Recommendation::needSn))
            .limit(5).toList();
        return new Recommendations(items, shortages(source));
    }

    public Recommendation recommendation(Source source, Need need) {
        var start = rules.tentativeStartMonth(source.endDate(), calendar.fromDatabaseMonth(need.startMonth()));
        int gap = Math.toIntExact(ChronoUnit.MONTHS.between(YearMonth.from(source.endDate()).plusMonths(1), start));
        Integer developerGrade = gradeOrder(source.gradeCode());
        Integer requestedGrade = gradeOrder(need.gradeCode() == null ? "MD" : need.gradeCode());
        Integer distance = developerGrade == null || requestedGrade == null ? null
            : Math.abs(developerGrade - requestedGrade);
        boolean sameJob = Objects.equals(source.primaryJobCode(), need.jobCode());
        boolean related = !sameJob && codes.relatedJob(source.primaryJobCode(), need.jobCode());
        boolean customer = source.customerId() != null && source.customerId().equals(need.customerId());
        BigDecimal score = rules.aiRecommendScore(new WithdrawalCalculationRules.ScoreInput(
            sameJob, related, distance, gap, customer, need.winningProbability()));
        var reasons = new ArrayList<String>();
        reasons.add(sameJob ? "직무 일치" : "관련 직무");
        if (Integer.valueOf(0).equals(distance)) reasons.add("등급 일치");
        else if (Integer.valueOf(1).equals(distance)) reasons.add("등급 한 단계 차이");
        reasons.add("공백 " + gap + "개월");
        if (customer) reasons.add("같은 고객사");
        int waiting = rules.waitingDays(source.endDate(), start);
        return new Recommendation(need.needSn(), need.opportunityId(), need.opportunityName(),
            need.jobName(), need.gradeName(), start.toString(), score, reasons, waiting,
            waiting == 0 ? "바로 이어서 투입" : "대기 예상 " + waiting + "일");
    }

    private Integer gradeOrder(String grade) {
        if (grade == null) return null;
        return codes.options("GRADE").stream().filter(c -> grade.equals(c.code))
            .map(c -> c.sortOrd).filter(Objects::nonNull).findFirst().orElse(null);
    }

    public List<Shortage> shortages(Source source) {
        var result = new ArrayList<Shortage>();
        YearMonth from = YearMonth.from(source.endDate()).plusMonths(1);
        int window = rules.candidateWindowMonths();
        String job = source.primaryJobCode();
        String jobName = codes.options("JOB").stream().filter(c -> job.equals(c.code))
            .map(c -> c.codeName).findFirst().orElse(source.jobName());
        for (int i = 0; i <= window; i++) {
            YearMonth month = from.plusMonths(i);
            var filter = new MonthFilter(job, calendar.toDatabaseMonth(month), month.atDay(1), month.atEndOfMonth());
            var extensions = new HashMap<String, List<WithdrawalCalculationRules.Extension>>();
            for (var row : mapper.extensions(filter)) {
                extensions.computeIfAbsent(row.developerId(), key -> new ArrayList<>())
                    .add(new WithdrawalCalculationRules.Extension(row.monthlyMm(), row.extensionPossibilityCode()));
            }
            var people = mapper.supply(filter).stream().map(p -> new WithdrawalCalculationRules.SupplyPerson(
                p.confirmedMm(), extensions.getOrDefault(p.developerId(), Collections.emptyList()))).toList();
            var opportunities = mapper.opportunityDemand(filter).stream().map(n ->
                new WithdrawalCalculationRules.OpportunityDemand(n.requiredCount(), n.monthlyMm(), n.winningProbability())).toList();
            var projects = mapper.projectDemand(filter).stream().map(p ->
                new WithdrawalCalculationRules.ProjectDemand(p.plannedMm(), p.confirmedMm(), p.remainingMonths())).toList();
            BigDecimal shortage = rules.demand(opportunities, projects).subtract(rules.supply(people, month)).max(BigDecimal.ZERO);
            if (shortage.signum() > 0) result.add(new Shortage(month.toString(), jobName, shortage));
        }
        return result;
    }

    public Source required(String id) {
        com.company.common.assignments.service.CommonAssignmentService.identifier(id, "assignmentId");
        Source source = mapper.source(id);
        if (source == null) throw CodeFailure.invalid("assignmentId", "투입 배정을 찾을 수 없습니다.");
        return source;
    }
}
