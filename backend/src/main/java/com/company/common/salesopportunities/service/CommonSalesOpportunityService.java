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
    private void lockSources() {
        projects.lockSources();
        mapper.lockDevelopers();
        mapper.lockOpportunities();
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
            row.nextAction(), row.nextActionDueDate(), row.memo(), needs, jobs, stale, row.approvalStatus(),
            row.modifiedAt(), row.projectId(), row.resultDate(), row.status(), row.lossReasonCode(),
            mapper.history(row.opportunityId()), row.lossMemo());
    }
    private Input prepare(Input in, Header old) {
        if (!mapper.customer(in.customerId())) throw SalesOpportunityRules.invalid("customerId");
        if (!mapper.part(in.salesPartId())) throw SalesOpportunityRules.invalid("salesPartId");
        selectable("SALES_STEP", in.stageCode(), "stageCode");
        selectable("CTRT_TYPE", in.contractTypeCode(), "contractTypeCode");
        if (old != null) stageAllowed(old, in.stageCode());
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
                || !retained.add(sn))) throw SalesOpportunityRules.invalid(prefix + "needSn");
            if (sn == null) {
                int next = 1;
                while (used.contains(next)) next++;
                if (next > 999) throw SalesOpportunityRules.invalid(prefix + "needSn");
                sn = next;
                used.add(sn);
                retained.add(sn);
            }
            BigDecimal price;
            try { price = calculations.price(need); }
            catch (CodeFailure failure) { throw CodeFailure.invalid(prefix + "estimatedUnitPrice", (index + 1) + "번째 줄의 단가 칸을 확인해 주세요."); }
            for (String dev : need.tentativeDeveloperIds()) if (!mapper.developer(dev))
                throw SalesOpportunityRules.invalid(prefix + "tentativeDeveloperIds");
            prepared.add(new Need(sn, need.jobCode(), need.gradeCode(), need.requiredCount(), need.startMonth(),
                need.months(), need.monthlyMm(), price, need.tentativeDeveloperIds()));
        }
        for (var n : existing) if (!retained.contains(n.needSn()) && mapper.historicalNeed(old.opportunityId(), n.needSn()) > 0)
            throw CodeFailure.invalid("needs", "투입 후보 이력이 있는 필요 인력 줄은 지울 수 없습니다.");
        return new Input(in.opportunityName(), in.customerId(), in.salesPartId(), in.stageCode(), in.winningProbability(),
            in.expectedAmount(), in.contractTypeCode(), in.startMonth(), in.expectedMonths(), in.workplace(),
            in.nextAction(), in.nextActionDueDate(), in.memo(), List.copyOf(prepared));
    }
    private String manual(Input in, Header old) {
        if (old != null && in.winningProbability().compareTo(old.winningProbability()) == 0) return old.manualYn();
        return in.winningProbability().compareTo(calculations.stageProbability(in.stageCode())) == 0 ? "N" : "Y";
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
        for (var reservation : current) {
            if (!"ACT".equals(reservation.status())) continue;
            var need = in.needs().stream().filter(n -> n.needSn() == reservation.needSn()).findFirst();
            if (need.isPresent() && !need.get().tentativeDeveloperIds().contains(reservation.developerId()))
                mapper.release(reservation.tentativeId(), actor.userId());
        }
        for (var need : in.needs()) for (String developer : need.tentativeDeveloperIds()) {
            transfer(id, new Tentative(null, developer, need.needSn(), need.startMonth()));
        }
    }
    @Transactional
    public Opportunity postCommonSalesOpportunity(Input request) {
        lockSources();
        var in = prepare(request, null);
        var evaluation = calculations.evaluate(in.needs());
        var status = SalesOpportunityCalculations.approval(null, null, evaluation);
        String id;
        do {
            id = "O" + UUID.randomUUID().toString().replace("-", "").substring(0, 19);
        } while (mapper.insert(new Write(id, in, SalesOpportunityCalculations.storedProfit(evaluation), status,
            manual(in, null), actor.userId())) == 0);
        saveNeeds(id, in, List.of());
        if ("WAIT".equals(status)) history(id, "REQ", SalesOpportunityCalculations.storedProfit(evaluation), null);
        return detail(required(id, false));
    }
    @Transactional
    public Opportunity putCommonSalesOpportunity(String id, Input request, Instant modifiedAt) {
        lockSources();
        var old = version(id, modifiedAt);
        ongoing(old);
        var in = prepare(request, old);
        var evaluation = calculations.evaluate(in.needs());
        var status = SalesOpportunityCalculations.approval(old.approvalStatus(), old.approvedProfitRate(), evaluation);
        var profit = SalesOpportunityCalculations.storedProfit(evaluation);
        var existing = mapper.needs(id);
        if (mapper.update(new Write(id, in, profit, status, manual(in, old), actor.userId())) != 1) throw CodeFailure.conflict();
        saveNeeds(id, in, existing);
        if ("OK".equals(old.approvalStatus()) && "NONE".equals(status)) history(id, "AUTO_REL", profit, null);
        else if ("WAIT".equals(status) && !"WAIT".equals(old.approvalStatus())) history(id, "REQ", profit, null);
        return detail(required(id, false));
    }
    private void stageAllowed(Header old, String stage) {
        List<String> stages = List.of("FIND", "PROP", "NEGO", "PRE");
        if (Set.of("WAIT", "REJ").contains(old.approvalStatus())
            && Set.of("NEGO", "PRE").contains(stage) && stages.indexOf(stage) > stages.indexOf(old.stageCode()))
            throw CodeFailure.invalid("stageCode", "「" + old.opportunityName() + "」은 견적 승인 전이라 협상 단계로 옮길 수 없습니다.");
    }
    @Transactional
    public Opportunity putCommonSalesOpportunityStage(String id, Instant modifiedAt, String stage) {
        lockSources();
        var old = version(id, modifiedAt);
        ongoing(old);
        selectable("SALES_STEP", stage, "stageCode");
        stageAllowed(old, stage);
        var probability = "Y".equals(old.manualYn()) ? old.winningProbability() : calculations.stageProbability(stage);
        if (mapper.stage(new StageWrite(id, stage, probability, actor.userId())) != 1) throw CodeFailure.conflict();
        return detail(required(id, false));
    }
    @Transactional
    public Opportunity approve(String id, Instant modifiedAt) { return approval(id, modifiedAt, "OK", null); }
    @Transactional
    public Opportunity reject(String id, Instant modifiedAt, String reason) { return approval(id, modifiedAt, "REJ", reason); }
    private Opportunity approval(String id, Instant modifiedAt, String status, String reason) {
        lockSources();
        var old = version(id, modifiedAt);
        ongoing(old);
        if (!"WAIT".equals(old.approvalStatus())) throw CodeFailure.invalid("approvalStatusCode", "승인 대기 중인 견적만 처리할 수 있습니다.");
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
        var warnings = new ArrayList<Warning>();
        for (var r : mapper.developerReservations(input.developerId())) {
            if (!id.equals(r.opportunityId())) warnings.add(new Warning(input.needSn(), input.developerId(),
                "「" + r.opportunityName() + "」에 가배정되어 있습니다. 저장하면 이 기회로 옮깁니다."));
            else if (r.needSn() != input.needSn()) {
                warnings.add(new Warning(input.needSn(), input.developerId(), "같은 사람이 두 줄에 있습니다."));
                warnings.add(new Warning(r.needSn(), input.developerId(), "같은 사람이 두 줄에 있습니다."));
            }
        }
        if (mapper.confirmedMm(input.developerId(), input.startMonth().replace("-", "")).compareTo(BigDecimal.ONE) >= 0)
            warnings.add(new Warning(input.needSn(), input.developerId(), "해당 월에 1.0 MM 이상 확정 투입되어 있습니다."));
        return new Warnings(List.copyOf(warnings));
    }
    private void transfer(String id, Tentative input) {
        var affected = new TreeSet<String>();
        for (var old : mapper.developerReservations(input.developerId())) {
            if (!id.equals(old.opportunityId())) {
                mapper.release(old.tentativeId(), actor.userId());
                affected.add(old.opportunityId());
            }
        }
        mapper.tentative(new TentativeWrite(id, input.needSn(), input.developerId(), input.startMonth(), actor.userId()));
        affected.add(id);
        affected.forEach(opp -> mapper.touch(opp, actor.userId()));
    }
    @Transactional
    public Opportunity tentative(String id, Tentative input) {
        lockSources();
        ongoing(version(id, input.modifiedAt()));
        validNeed(id, input);
        transfer(id, input);
        return detail(required(id, false));
    }
    @Transactional
    public Opportunity win(String id, Win input) {
        lockSources();
        var old = version(id, input.modifiedAt());
        ongoing(old);
        selectable("CTRT_TYPE", input.contractTypeCode(), "contractTypeCode");
        if (!input.contractEndDate().isAfter(input.contractStartDate())) throw SalesOpportunityRules.invalid("contractEndDate");
        String projectId = projects.create(old, input);
        var link = new ProjectLink(id, projectId, calendar.today(), actor.userId());
        mapper.linkProject(link);
        mapper.manpower(link);
        mapper.convert(id, actor.userId());
        return detail(required(id, false));
    }
    @Transactional
    public Opportunity lose(String id, Instant modifiedAt, String reason, String memo) {
        lockSources();
        ongoing(version(id, modifiedAt));
        selectable("LOSE_RSN", reason, "lossReasonCode");
        mapper.result(new ResultWrite(id, "LOSE", calendar.today(), reason, memo, actor.userId()));
        mapper.releaseAll(id, actor.userId());
        return detail(required(id, false));
    }
    @Transactional
    public Opportunity stop(String id, Instant modifiedAt) {
        lockSources();
        ongoing(version(id, modifiedAt));
        mapper.result(new ResultWrite(id, "STOP", calendar.today(), null, null, actor.userId()));
        mapper.releaseAll(id, actor.userId());
        return detail(required(id, false));
    }
    @Transactional
    public Opportunity resume(String id, Instant modifiedAt) {
        lockSources();
        var old = version(id, modifiedAt);
        if (!"STOP".equals(old.status())) throw CodeFailure.invalid("opportunityStatusCode", "사용 중지한 기회만 다시 사용할 수 있습니다.");
        mapper.result(new ResultWrite(id, "ING", null, null, null, actor.userId()));
        return detail(required(id, false));
    }
}
