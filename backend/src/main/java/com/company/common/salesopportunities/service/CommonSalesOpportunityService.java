package com.company.common.salesopportunities.service;

import com.company.common.common.SalesPipelineCodes;
import com.company.common.salesopportunities.dto.SalesOpportunityContracts.*;
import com.company.common.salesopportunities.mapper.SalesOpportunityMapper;
import com.company.common.salesopportunities.mapper.SalesOpportunityRows.*;
import com.company.common.support.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommonSalesOpportunityService {
    private final SalesOpportunityMapper mapper;
    private final SalesPipelineCodes codes;
    private final SalesOpportunityCalculations calculations;
    private final SalesOpportunityProjects projects;
    private final BusinessCalendar calendar;
    private final CurrentBusinessActor actor;

    public CommonSalesOpportunityService(SalesOpportunityMapper mapper, SalesPipelineCodes codes,
        SalesOpportunityCalculations calculations, SalesOpportunityProjects projects,
        BusinessCalendar calendar, CurrentBusinessActor actor) {
        this.mapper = mapper;
        this.codes = codes;
        this.calculations = calculations;
        this.projects = projects;
        this.calendar = calendar;
        this.actor = actor;
    }
    private Header required(String id, boolean lock) {
        if (id == null || id.isBlank() || id.length() > 20) throw SalesOpportunityRules.invalid("opportunityId");
        var row = lock ? mapper.lock(id) : mapper.select(id);
        if (row == null) throw new CodeFailure(404, "opportunityId", "영업 기회를 찾을 수 없습니다.");
        return row;
    }
    static final String HANDED_OFF = "투입 배정으로 넘긴 후보입니다. 투입 상세에서 확인해 주세요.";
    private record Prepared(Input input, String manualYn) {}
    /**
     * 가배정·필요 인력을 바꾸는 저장만 쓰는 잠금. 철수 리스트(UI-05) 가배정 저장과 같은 순서
     * (프로젝트 표 → 그 사람 줄 → 기회 줄, 각각 ID 순)로 잠가 서로 기다리다 멈추지 않게 한다.
     * 기회 줄은 고치는 기회와, 그 사람들이 지금 가배정된 다른 기회(옮기며 풀고 수정 시각을 바꿈)만 잠근다.
     */
    private void lockTentative(String id, Collection<String> developers) {
        projects.lockSources();
        var people = new TreeSet<>(developers);
        var opportunities = new TreeSet<String>();
        if (id != null) opportunities.add(id);
        if (!people.isEmpty()) {
            mapper.lockDevelopers(people);
            mapper.developersReservations(people).forEach(r -> opportunities.add(r.opportunityId()));
        }
        if (!opportunities.isEmpty()) mapper.lockOpportunities(opportunities);
    }
    private static Set<String> developers(Input in) {
        var ids = new TreeSet<String>();
        in.needs().forEach(n -> ids.addAll(n.tentativeDeveloperIds()));
        return ids;
    }
    /** 수주로 투입 배정 후보가 된(CNVT) 사람은 다른 기회에 가배정하지 않는다(UI-05 저장과 같은 규칙). */
    private boolean handedOff(String developerId, String opportunityId) {
        return mapper.developerConversions(developerId).stream().anyMatch(r -> !r.opportunityId().equals(opportunityId));
    }
    private boolean reservedHere(String developerId, String opportunityId) {
        return opportunityId != null && mapper.developerReservations(developerId).stream()
            .anyMatch(r -> opportunityId.equals(r.opportunityId()));
    }
    private Header version(String id, Instant modifiedAt) {
        var row = required(id, true);
        if (!Objects.equals(row.modifiedAt(), modifiedAt)) throw CodeFailure.conflict();
        return row;
    }
    private void ongoing(Header row) {
        if (!"ING".equals(row.status())) throw CodeFailure.invalid("opportunityStatusCode", "진행 중 기회에서만 처리할 수 있습니다.");
    }
    private void selectable(String group, String value, String field) {
        if (value != null && codes.selectable(group).stream().noneMatch(item -> value.equals(item.code))) {
            if (field.startsWith("needs[")) {
                int row = Integer.parseInt(field.substring(6, field.indexOf(']'))) + 1;
                String label = "JOB".equals(group) ? "직무" : "등급";
                throw CodeFailure.invalid(field, row + "번째 줄의 " + label + " 칸을 확인해 주세요.");
            }
            throw SalesOpportunityRules.invalid(field);
        }
    }
    private static String month(String value) { return value.substring(0, 4) + "-" + value.substring(4); }
    public Opportunity getCommonSalesOpportunity(String id) { return detail(required(id, false)); }
    private Opportunity detail(Header row) {
        var reserved = mapper.reservations(row.opportunityId());
        var needs = mapper.needs(row.opportunityId()).stream().map(n -> new Need(n.needSn(), n.jobCode(), n.gradeCode(),
            n.requiredCount(), month(n.startMonth()), n.months(), n.monthlyMm(), n.estimatedUnitPrice(),
            reserved.stream().filter(t -> t.needSn() == n.needSn()).map(Reservation::developerId).distinct().toList())).toList();
        var jobs = needs.stream().map(n -> codes.name("JOB", n.jobCode())).distinct().toList();
        boolean stale = "ING".equals(row.status()) && Duration.between(row.modifiedAt(), calendar.now())
            .compareTo(Duration.ofDays(14)) > 0;
        return new Opportunity(row.opportunityId(), row.opportunityName(), row.customerId(), row.customerName(),
            row.salesPartId(), row.stageCode(), row.winningProbability(), row.expectedAmount(), row.contractTypeCode(),
            month(row.startMonth()), row.expectedMonths() == null ? 6 : row.expectedMonths(), row.workplace(),
            row.workAreaCode(), row.workAreaName(), row.nextAction(), row.nextActionDueDate(), row.memo(), needs, jobs, stale, row.approvalStatus(),
            row.modifiedAt(), row.projectId(), row.resultDate(), row.status(), row.lossReasonCode(),
            mapper.history(row.opportunityId()), row.lossMemo(), row.profitRate(), row.approvedProfitRate(),
            calculations.approvalRate());
    }
    /** SFR-129-03·09: 계약유형은 턴키(TK)·월단위(MO)·파견(DP)만 고른다(내부 IN 제외). */
    private void contractType(String value) {
        selectable("CTRT_TYPE", value, "contractTypeCode");
        if (value != null && !Set.of("TK", "MO", "DP").contains(value))
            throw CodeFailure.invalid("contractTypeCode", "계약유형은 턴키·월단위·파견 중에서 골라 주세요.");
    }
    /**
     * SFR-129-07: 확률은 단계 기본값(STEP_PRBL_1~4), 직접 고친 확률이면 유지한다.
     * 비우면(새 기회는 0도) 단계 기본값. 고칠 때 확률을 그대로 두고 단계만 바꾸면, 직접 고친 확률이 아니면 새 단계 기본값.
     */
    private Prepared probability(Input in, Header old) {
        BigDecimal stageDefault = calculations.stageProbability(in.stageCode());
        BigDecimal given = in.winningProbability();
        if (given == null || old == null && given.signum() == 0) return new Prepared(withProbability(in, stageDefault), "N");
        if (old != null && given.compareTo(old.winningProbability()) == 0) {
            if (!old.stageCode().equals(in.stageCode()) && !"Y".equals(old.manualYn()))
                return new Prepared(withProbability(in, stageDefault), "N");
            return new Prepared(in, old.manualYn());
        }
        return new Prepared(in, given.compareTo(stageDefault) == 0 ? "N" : "Y");
    }
    private Prepared prepare(Input in, Header old) {
        if (!mapper.customer(in.customerId())) throw SalesOpportunityRules.invalid("customerId");
        if (!mapper.part(in.salesPartId())) throw SalesOpportunityRules.invalid("salesPartId");
        selectable("SALES_STEP", in.stageCode(), "stageCode");
        contractType(in.contractTypeCode());
        // 근무지 지역(2026-11-25): 사용 중인 행정구역만. 고치지 않은 저장값은 나중에 쓰지 않게 된 구역이어도 그대로 둔다.
        if (in.workAreaCode() != null && !(old != null && in.workAreaCode().equals(old.workAreaCode()))
            && !mapper.area(in.workAreaCode()))
            throw com.company.common.projects.service.ProjectRules.invalidWorkArea();
        String opportunityId = old == null ? null : old.opportunityId();
        var existing = old == null ? List.<NeedRow>of() : mapper.needs(old.opportunityId());
        var used = new HashSet<Integer>();
        existing.forEach(n -> used.add(n.needSn()));
        var retained = new HashSet<Integer>();
        var prepared = new ArrayList<Need>();
        for (int index = 0; index < in.needs().size(); index++) {
            var need = in.needs().get(index);
            String prefix = "needs[" + index + "].";
            selectable("JOB", need.jobCode(), prefix + "jobCode");
            selectable("GRADE", need.gradeCode(), prefix + "gradeCode");
            Integer sn = need.needSn();
            if (sn != null && (old == null || existing.stream().noneMatch(n -> n.needSn().equals(need.needSn()))
                || !retained.add(sn))) throw SalesOpportunityRules.row(index, "needSn");
            if (sn == null) {
                int next = 1;
                while (used.contains(next)) next++;
                if (next > 999) throw SalesOpportunityRules.row(index, "needSn");
                sn = next;
                used.add(sn);
                retained.add(sn);
            }
            // SFR-129-03·식-84: 비운 예상 단가는 계산에서만 직무 기준 단가로 바꾼다(저장은 비운 그대로).
            // 기준 단가가 없으면 계산할 수 없으므로 저장 전에 여기서 막는다.
            try { calculations.price(need); }
            catch (CodeFailure failure) { throw SalesOpportunityRules.row(index, "estimatedUnitPrice"); }
            for (String dev : need.tentativeDeveloperIds()) {
                if (!mapper.developer(dev)) throw SalesOpportunityRules.row(index, "tentativeDeveloperIds");
                if (!reservedHere(dev, opportunityId) && handedOff(dev, opportunityId))
                    throw new CodeFailure(409, prefix + "tentativeDeveloperIds", HANDED_OFF);
            }
            prepared.add(new Need(sn, need.jobCode(), need.gradeCode(), need.requiredCount(), need.startMonth(),
                need.months(), need.monthlyMm(), need.estimatedUnitPrice(), need.tentativeDeveloperIds()));
        }
        for (var n : existing) if (!retained.contains(n.needSn()) && mapper.historicalNeed(old.opportunityId(), n.needSn()) > 0)
            throw CodeFailure.invalid("needs", "투입 후보 이력이 있는 필요 인력 줄은 지울 수 없습니다.");
        return probability(new Input(in.opportunityName(), in.customerId(), in.salesPartId(), in.stageCode(),
            in.winningProbability(), in.expectedAmount(), in.contractTypeCode(), in.startMonth(), in.expectedMonths(),
            in.workplace(), in.workAreaCode(), in.nextAction(), in.nextActionDueDate(), in.memo(), List.copyOf(prepared)), old);
    }
    private static Input withProbability(Input in, BigDecimal probability) {
        return new Input(in.opportunityName(), in.customerId(), in.salesPartId(), in.stageCode(), probability,
            in.expectedAmount(), in.contractTypeCode(), in.startMonth(), in.expectedMonths(), in.workplace(),
            in.workAreaCode(), in.nextAction(), in.nextActionDueDate(), in.memo(), in.needs());
    }
    private void history(String id, String action, BigDecimal profit, String reason) {
        mapper.appendHistory(new ApprovalWrite(id, action, profit, reason, actor.userId()));
    }
    private void saveNeeds(String id, Input in, List<NeedRow> existing) {
        var retained = in.needs().stream().map(Need::needSn).toList();
        for (var n : existing) if (!retained.contains(n.needSn())) {
            mapper.releaseNeed(id, n.needSn(), actor.userId());
            mapper.deleteReleasedNeedReservations(id, n.needSn());
            mapper.removeNeed(id, n.needSn());
        }
        for (var n : in.needs()) {
            var write = new NeedWrite(id, n, actor.userId());
            if (existing.stream().anyMatch(e -> e.needSn().equals(n.needSn()))) mapper.updateNeed(write);
            else mapper.insertNeed(write);
        }
        var current = mapper.reservations(id);
        // 같은 줄에 이미 있는 가배정은 그대로 둔다. 시작월(BGN_YM)은 철수 리스트(UI-05)가
        // max(철수 다음 달, 줄 시작월)로 넣었을 수 있어 저장마다 줄 시작월로 덮지 않는다.
        var kept = new HashSet<String>();
        for (var reservation : current) {
            if (!"ACT".equals(reservation.status())) continue;
            var need = in.needs().stream().filter(n -> n.needSn() == reservation.needSn()).findFirst();
            if (need.isEmpty()) continue;
            if (!need.get().tentativeDeveloperIds().contains(reservation.developerId()))
                mapper.release(reservation.tentativeId(), actor.userId());
            else kept.add(reservation.needSn() + "|" + reservation.developerId());
        }
        // 새 가배정, 또는 다른 줄로 옮긴 가배정만 줄 시작월로 쓴다.
        for (var need : in.needs()) for (String developer : need.tentativeDeveloperIds()) {
            if (kept.contains(need.needSn() + "|" + developer)) continue;
            transfer(id, new Tentative(null, developer, need.needSn(), need.startMonth()));
        }
    }
    /** 이미 있는 O-nnnn 중 가장 큰 번호 + 1(형식이 다른 옛 ID는 건너뛴다). */
    private String nextOpportunityId() {
        int number = mapper.nextOpportunityNumber();
        if (number < 1 || number > 9999)
            throw CodeFailure.invalid("opportunityId", "영업기회ID 발급 범위(O-0001~O-9999)를 넘었습니다.");
        return String.format(Locale.ROOT, "O-%04d", number);
    }
    @Transactional
    public Opportunity postCommonSalesOpportunity(Input request) {
        lockTentative(null, developers(request));
        var prepared = prepare(request, null);
        var in = prepared.input();
        var evaluation = calculations.evaluate(in.needs());
        var status = SalesOpportunityCalculations.approval(null, null, evaluation);
        stageAllowed(in.opportunityName(), null, in.stageCode(), status);
        // 공통규칙 2.7 영업기회ID O-nnnn 순번. lockTentative가 잡은 프로젝트 표 잠금(SHARE ROW EXCLUSIVE) 안이라
        // 새 기회 등록은 한 번에 하나씩 번호를 받는다.
        String id = nextOpportunityId();
        if (mapper.insert(new Write(id, in, SalesOpportunityCalculations.storedProfit(evaluation), status,
            prepared.manualYn(), actor.userId())) != 1) throw CodeFailure.conflict();
        saveNeeds(id, in, List.of());
        if ("WAIT".equals(status)) history(id, "REQ", SalesOpportunityCalculations.storedProfit(evaluation), null);
        return detail(required(id, false));
    }
    @Transactional
    public Opportunity putCommonSalesOpportunity(String id, Input request, Instant modifiedAt) {
        if (id == null || id.isBlank() || id.length() > 20) throw SalesOpportunityRules.invalid("opportunityId");
        lockTentative(id, developers(request));
        var old = version(id, modifiedAt);
        ongoing(old);
        var prepared = prepare(request, old);
        var in = prepared.input();
        var evaluation = calculations.evaluate(in.needs());
        var status = SalesOpportunityCalculations.approval(old.approvalStatus(), old.approvedProfitRate(), evaluation);
        stageAllowed(old.opportunityName(), old.stageCode(), in.stageCode(), status);
        var profit = SalesOpportunityCalculations.storedProfit(evaluation);
        var existing = mapper.needs(id);
        if (mapper.update(new Write(id, in, profit, status, prepared.manualYn(), actor.userId())) != 1) throw CodeFailure.conflict();
        saveNeeds(id, in, existing);
        if ("OK".equals(old.approvalStatus()) && "NONE".equals(status)) history(id, "AUTO_REL", profit, null);
        else if ("WAIT".equals(status) && !"WAIT".equals(old.approvalStatus())) history(id, "REQ", profit, null);
        return detail(required(id, false));
    }
    /**
     * SFR-129-07: 견적 승인 대기·반려 기회는 협상 이상에 둘 수 없다. 저장 뒤의(새로 계산한) 승인 상태로 본다.
     * 이미 협상 이상에 있던 옛 자료가 아래 단계로 내려가는 것은 막지 않는다.
     */
    private void stageAllowed(String name, String oldStage, String stage, String approvalStatus) {
        List<String> stages = List.of("FIND", "PROP", "NEGO", "PRE");
        if (Set.of("WAIT", "REJ").contains(approvalStatus) && Set.of("NEGO", "PRE").contains(stage)
            && (oldStage == null || stages.indexOf(stage) >= stages.indexOf(oldStage)))
            throw CodeFailure.invalid("stageCode", "「" + name + "」은 견적 승인 전이라 협상 단계로 옮길 수 없습니다.");
    }
    @Transactional
    public Opportunity putCommonSalesOpportunityStage(String id, Instant modifiedAt, String stage) {
        var old = version(id, modifiedAt);
        ongoing(old);
        selectable("SALES_STEP", stage, "stageCode");
        stageAllowed(old.opportunityName(), old.stageCode(), stage, old.approvalStatus());
        var probability = "Y".equals(old.manualYn()) ? old.winningProbability() : calculations.stageProbability(stage);
        if (mapper.stage(new StageWrite(id, stage, probability, actor.userId())) != 1) throw CodeFailure.conflict();
        return detail(required(id, false));
    }
    @Transactional
    public Opportunity approve(String id, Instant modifiedAt) { return approval(id, modifiedAt, "OK", null); }
    @Transactional
    public Opportunity reject(String id, Instant modifiedAt, String reason) { return approval(id, modifiedAt, "REJ", reason); }
    private Opportunity approval(String id, Instant modifiedAt, String status, String reason) {
        // 기회 줄 잠금(version) 안에서 이력 번호(HIST_SN)를 매긴다.
        var old = version(id, modifiedAt);
        ongoing(old);
        if (!"WAIT".equals(old.approvalStatus())) throw CodeFailure.invalid("approvalStatusCode", "승인 대기 중인 견적만 처리할 수 있습니다.");
        // 승인 이력의 이익률(TB_SL_APRV_HIST.PRFT_RT)은 비울 수 없다. 저장된 예상 이익률이 없으면 500 대신 알린다.
        if (old.profitRate() == null)
            throw CodeFailure.invalid("expectedProfitRate", "예상 이익률을 다시 계산해 저장한 뒤 "
                + ("OK".equals(status) ? "승인" : "반려") + "해 주세요.");
        var write = new ApprovalWrite(id, status, old.profitRate(), reason, actor.userId());
        if (mapper.approval(write) != 1) throw CodeFailure.conflict();
        mapper.appendHistory(write);
        if ("REJ".equals(status)) {
            String text = "「" + old.opportunityName() + "」 견적이 반려됐습니다" + (reason == null || reason.isBlank() ? "" : ": " + reason);
            if (text.codePointCount(0, text.length()) > 300) text = text.substring(0, text.offsetByCodePoints(0, 299)) + "…";
            mapper.notifyOwner(new Notification(id, old.creator(), text, actor.userId()));
        }
        return detail(required(id, false));
    }
    private void validNeed(String id, Tentative input) {
        if (!mapper.developer(input.developerId())) throw SalesOpportunityRules.invalid("developerId");
        if (mapper.needs(id).stream().noneMatch(n -> n.needSn() == input.needSn())) throw SalesOpportunityRules.invalid("needSn");
    }
    public Warnings check(String id, Tentative input) {
        var old = required(id, false);
        if (!Objects.equals(old.modifiedAt(), input.modifiedAt())) throw CodeFailure.conflict();
        validNeed(id, input);
        return warnings(id, input.needSn(), input.developerId(), input.startMonth());
    }
    /** 아직 저장하지 않은 줄(새 기회·새 줄)의 점검. needSn이 없어 경고의 needSn은 null(이 줄)이다. */
    public Warnings check(DeveloperCheck input) {
        if (input.opportunityId() != null) required(input.opportunityId(), false);
        if (!mapper.developer(input.developerId())) throw SalesOpportunityRules.invalid("developerId");
        return warnings(input.opportunityId(), null, input.developerId(), input.startMonth());
    }
    private Warnings warnings(String id, Integer needSn, String developer, String startMonth) {
        var warnings = new ArrayList<Warning>();
        boolean here = false;
        for (var r : mapper.developerReservations(developer)) {
            if (!r.opportunityId().equals(id)) warnings.add(new Warning(needSn, developer,
                "「" + r.opportunityName() + "」에 가배정되어 있습니다. 저장하면 이 기회로 옮깁니다."));
            else {
                here = true;
                if (!Objects.equals(r.needSn(), needSn)) {
                    warnings.add(new Warning(needSn, developer, "같은 사람이 두 줄에 있습니다."));
                    warnings.add(new Warning(r.needSn(), developer, "같은 사람이 두 줄에 있습니다."));
                }
            }
        }
        // 저장하면 막히는 경우(투입 배정 후보로 넘긴 사람)도 줄 아래에 미리 보인다.
        if (!here && handedOff(developer, id)) warnings.add(new Warning(needSn, developer, HANDED_OFF));
        if (mapper.confirmedMm(developer, startMonth.replace("-", "")).compareTo(BigDecimal.ONE) >= 0)
            warnings.add(new Warning(needSn, developer, "해당 월에 1.0 MM 이상 확정 투입되어 있습니다."));
        return new Warnings(List.copyOf(warnings));
    }
    private void transfer(String id, Tentative input) {
        var previous = mapper.developerReservations(input.developerId()).stream()
            .filter(old -> !id.equals(old.opportunityId()))
            .map(old -> new com.company.common.common.TentativeTransfers.Previous(Long.toString(old.tentativeId()), old.opportunityId())).toList();
        com.company.common.common.TentativeTransfers.apply(
            new com.company.common.common.TentativeTransfers.Mutation(previous, id, () ->
                mapper.tentative(new TentativeWrite(id, input.needSn(), input.developerId(), input.startMonth(), actor.userId()))),
            previousId -> mapper.release(Long.parseLong(previousId), actor.userId()), opp -> mapper.touch(opp, actor.userId()));
    }
    @Transactional
    public Opportunity tentative(String id, Tentative input) {
        if (id == null || id.isBlank() || id.length() > 20) throw SalesOpportunityRules.invalid("opportunityId");
        lockTentative(id, List.of(input.developerId()));
        ongoing(version(id, input.modifiedAt()));
        validNeed(id, input);
        if (!reservedHere(input.developerId(), id) && handedOff(input.developerId(), id))
            throw new CodeFailure(409, "developerId", HANDED_OFF);
        transfer(id, input);
        return detail(required(id, false));
    }
    @Transactional
    public Opportunity win(String id, Win input) {
        // 프로젝트 만들기가 프로젝트 표를 먼저 잠그므로 기회 줄보다 먼저 잠근다(UI-05 저장과 같은 순서).
        projects.lockSources();
        var old = version(id, input.modifiedAt());
        ongoing(old);
        contractType(input.contractTypeCode());
        if (!input.contractEndDate().isAfter(input.contractStartDate())) throw SalesOpportunityRules.invalid("contractEndDate");
        // 인원 계획 PLAN_MM은 NUMERIC(6,2)라 직무별 합이 9,999.99를 넘으면 저장할 수 없다(500 대신 입력 오류).
        if (mapper.manpowerPlans(id).stream().anyMatch(mm -> mm != null && mm.compareTo(new BigDecimal("9999.99")) > 0))
            throw CodeFailure.invalid("needs", "직무별 인원 계획(인원 × 월 MM × 개월 합)이 9,999.99 MM을 넘습니다. 필요 인력 줄을 확인해 주세요.");
        String projectId = projects.create(old, input);
        var link = new ProjectLink(id, projectId, calendar.today(), actor.userId());
        mapper.linkProject(link);
        mapper.manpower(link);
        mapper.convert(id, actor.userId());
        return detail(required(id, false));
    }
    @Transactional
    public Opportunity lose(String id, Instant modifiedAt, String reason, String memo) {
        // 실주·사용 중지·다시 사용은 그 기회 줄만 잠근다(가배정은 그 기회 것만 푼다).
        ongoing(version(id, modifiedAt));
        selectable("LOSE_RSN", reason, "lossReasonCode");
        mapper.result(new ResultWrite(id, "LOSE", calendar.today(), reason, memo, actor.userId()));
        mapper.releaseAll(id, actor.userId());
        return detail(required(id, false));
    }
    @Transactional
    public Opportunity stop(String id, Instant modifiedAt) {
        ongoing(version(id, modifiedAt));
        mapper.result(new ResultWrite(id, "STOP", calendar.today(), null, null, actor.userId()));
        mapper.releaseAll(id, actor.userId());
        return detail(required(id, false));
    }
    @Transactional
    public Opportunity resume(String id, Instant modifiedAt) {
        var old = version(id, modifiedAt);
        if (!"STOP".equals(old.status())) throw CodeFailure.invalid("opportunityStatusCode", "사용 중지한 기회만 다시 사용할 수 있습니다.");
        mapper.result(new ResultWrite(id, "ING", null, null, null, actor.userId()));
        return detail(required(id, false));
    }
}
