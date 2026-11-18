package com.company.common.withdrawals.service;

import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.withdrawals.dto.WithdrawalContracts.*;
import com.company.common.withdrawals.mapper.WithdrawalMapper;
import com.company.common.withdrawals.mapper.WithdrawalRows.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WithdrawalRecommendationService {
    /** 시안: 부족(수요 − 공급)이 0.4명을 넘는 달만 알린다. */
    private static final BigDecimal SHORTAGE_NOTICE = new BigDecimal("0.4");
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

    /**
     * 한 요청 동안 함께 쓰는 식·공통코드·필요 인력 줄. 필요 인력 줄은 처음 쓸 때 한 번만 읽는다
     * (목록의 사람마다 다시 읽지 않음). 가배정 저장은 잠근 뒤에 처음 읽게 된다.
     */
    public final class Context {
        private final WithdrawalCalculationRules.Settings settings = rules.settings();
        private final WithdrawalCodes.Snapshot lookup = new WithdrawalCodes.Snapshot(codes);
        private List<Need> needs;

        private Context() {}

        public WithdrawalCalculationRules.Settings rules() { return settings; }
        public WithdrawalCodes.Snapshot codes() { return lookup; }
        public List<Need> needs() {
            if (needs == null) needs = mapper.needs();
            return needs;
        }
    }

    public Context context() {
        return new Context();
    }

    public boolean eligible(Source source, Context context) {
        return "ACT".equals(source.statusCode()) && !"H".equals(source.extensionPossibilityCode())
            && context.rules().nearTerm(source.endDate(), calendar.today());
    }

    /** 식-79 조건 1(시작 범위, 이미 시작한 자리는 끝나기 전까지)과 같은·관련 직무, 진행 중 기회. */
    public boolean matches(Source source, Need need, Context context) {
        YearMonth start = calendar.fromDatabaseMonth(need.startMonth());
        return "ING".equals(need.statusCode())
            && (Objects.equals(source.primaryJobCode(), need.jobCode())
                || context.codes().relatedJob(source.primaryJobCode(), need.jobCode()))
            && context.rules().candidatePeriod(source.endDate(), start, need.durationMonths());
    }

    /** 식-79 조건 2: 남은 자리 = 필요 인원 − 이 줄의 가배정(ACT·CNVT). 이 사람 자신의 자리는 빼고 센다. */
    public static int remaining(Need need, List<Reservation> current) {
        int own = (int) current.stream().filter(r -> sameNeed(r, need)).count();
        return need.requiredCount() - Math.max(0, need.occupiedCount() - own);
    }

    public List<Need> candidates(Source source, List<Reservation> current, boolean replaceCurrent, Context context) {
        if (!eligible(source, context) || (!current.isEmpty() && !replaceCurrent)
            || current.stream().anyMatch(r -> "CNVT".equals(r.statusCode()))) return new ArrayList<>();
        return context.needs().stream().filter(n -> matches(source, n, context))
            .filter(n -> remaining(n, current) > 0).toList();
    }

    public static boolean sameNeed(Reservation reservation, Need need) {
        return reservation.opportunityId().equals(need.opportunityId()) && reservation.needSn() == need.needSn();
    }

    public Recommendations recommendations(Source source, boolean replaceCurrent) {
        var context = context();
        var current = mapper.reservations(source.developerId());
        var candidates = candidates(source, current, replaceCurrent, context);
        var items = candidates.stream().map(n -> recommendation(source, n, current, context))
            .sorted(Comparator.comparing(Recommendation::score).reversed()
                .thenComparing(Recommendation::startMonth)
                .thenComparing(Recommendation::opportunityId).thenComparingInt(Recommendation::needSn))
            .limit(5).toList();
        return new Recommendations(items, shortages(source, context), target(source, current, context));
    }

    private RecommendationTarget target(Source source, List<Reservation> current, Context context) {
        var reservation = current.isEmpty() ? null : current.get(0);
        return new RecommendationTarget(source.assignmentId(), source.name(),
            context.codes().jobName(source.primaryJobCode(), source.jobName()), source.gradeName(),
            source.projectName(), source.endDate(), mapper.skills(source.developerId()),
            context.codes().extensionName(source.extensionPossibilityCode()), source.endDate().plusDays(1),
            reservation == null ? null : reservation.opportunityId(),
            reservation == null ? null : reservation.opportunityName(),
            reservation == null ? null : calendar.fromDatabaseMonth(reservation.startMonth()).toString());
    }

    public Recommendation recommendation(Source source, Need need, List<Reservation> current, Context context) {
        var settings = context.rules();
        YearMonth seat = calendar.fromDatabaseMonth(need.startMonth());
        var start = settings.tentativeStartMonth(source.endDate(), seat);
        int gap = Math.toIntExact(ChronoUnit.MONTHS.between(YearMonth.from(source.endDate()).plusMonths(1), start));
        Integer developerGrade = context.codes().gradeOrder(source.gradeCode());
        Integer requestedGrade = context.codes().gradeOrder(need.gradeCode() == null ? "MD" : need.gradeCode());
        Integer distance = developerGrade == null || requestedGrade == null ? null
            : Math.abs(developerGrade - requestedGrade);
        boolean sameJob = Objects.equals(source.primaryJobCode(), need.jobCode());
        boolean related = !sameJob && context.codes().relatedJob(source.primaryJobCode(), need.jobCode());
        boolean customer = source.customerId() != null && source.customerId().equals(need.customerId());
        BigDecimal score = settings.aiRecommendScore(new WithdrawalCalculationRules.ScoreInput(
            sameJob, related, distance, gap, customer, need.winningProbability()));
        // 시안 추천 이유 글자 그대로
        var reasons = new ArrayList<String>();
        reasons.add(sameJob ? "직무 일치" : "관련 직무");
        if (Integer.valueOf(0).equals(distance)) reasons.add("등급 일치");
        else if (Integer.valueOf(1).equals(distance)) reasons.add("등급 한 단계 차이");
        else if (distance != null) reasons.add("등급 차이 큼");
        reasons.add(gap == 0 ? "공백 없음" : "공백 " + gap + "개월");
        if (customer) reasons.add("같은 고객사");
        if (settings.probabilityBonus(need.winningProbability()))
            reasons.add("수주 확률 " + need.winningProbability().stripTrailingZeros().toPlainString() + "%");
        int waiting = settings.waitingDays(source.endDate(), start);
        boolean tentative = current.stream().anyMatch(r -> r.opportunityId().equals(need.opportunityId()));
        return new Recommendation(need.needSn(), need.opportunityId(), need.opportunityName(),
            need.jobName(), need.gradeName(), seat.toString(), score, reasons, waiting,
            waiting == 0 ? "바로 이어서 투입" : "대기 예상 " + waiting + "일", remaining(need, current),
            need.customerName(), need.stageName(), tentative);
    }

    public List<Shortage> shortages(Source source, Context context) {
        var settings = context.rules();
        var result = new ArrayList<Shortage>();
        YearMonth from = YearMonth.from(source.endDate()).plusMonths(1);
        int window = settings.candidateWindowMonths();
        String job = source.primaryJobCode();
        String jobName = context.codes().jobName(job, source.jobName());
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
            BigDecimal shortage = settings.demand(opportunities, projects).subtract(settings.supply(people, month));
            if (shortage.compareTo(SHORTAGE_NOTICE) > 0)
                result.add(new Shortage(month.toString(), jobName, shortage.setScale(1, RoundingMode.HALF_UP)));
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
