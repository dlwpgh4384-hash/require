package com.company.common.withdrawals.service;

import com.company.common.assignments.mapper.AssignmentMapper;
import com.company.common.assignments.service.CommonAssignmentService;
import com.company.common.clients.service.ClientRules;
import com.company.common.support.service.*;
import com.company.common.withdrawals.dto.WithdrawalContracts.*;
import com.company.common.withdrawals.mapper.WithdrawalMapper;
import com.company.common.withdrawals.mapper.WithdrawalRows.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommonWithdrawalService {
    private final WithdrawalMapper mapper;
    private final AssignmentMapper assignments;
    private final CommonAssignmentService assignmentService;
    private final WithdrawalRecommendationService recommendations;
    private final WithdrawalTentativeService tentative;
    private final WithdrawalCodes codes;
    private final WithdrawalCalculationRules rules;
    private final BusinessCalendar calendar;

    public CommonWithdrawalService(WithdrawalMapper mapper, AssignmentMapper assignments,
        CommonAssignmentService assignmentService, WithdrawalRecommendationService recommendations,
        WithdrawalTentativeService tentative, WithdrawalCodes codes,
        WithdrawalCalculationRules rules, BusinessCalendar calendar) {
        this.mapper = mapper;
        this.assignments = assignments;
        this.assignmentService = assignmentService;
        this.recommendations = recommendations;
        this.tentative = tentative;
        this.codes = codes;
        this.rules = rules;
        this.calendar = calendar;
    }

    public Instant now() { return calendar.now(); }
    public String userId() { return assignmentService.userId(); }

    private ListFilter filter(Filter filter, String period) {
        if (filter.year() == null || filter.year() < 1 || filter.year() > 9999) {
            throw CodeFailure.invalid("year", "연도를 확인해 주세요.");
        }
        if (filter.extensionPossibilityCode() != null) codes.extensionStorageCode(filter.extensionPossibilityCode());
        String job = filter.jobCode();
        if (job != null && !job.isEmpty() && codes.options("JOB").stream().noneMatch(c -> job.equals(c.code))) {
            throw CodeFailure.invalid("jobCode", "등록된 직무를 골라 주세요.");
        }
        LocalDate from = null, through = null;
        LocalDate today = calendar.today();
        LocalDate boundary = calendar.currentMonth().plusMonths(rules.nearTermMonthOffset()).atEndOfMonth();
        if (period != null && !period.isEmpty()) {
            switch (period) {
                case "3개월 안" -> { from = today; through = boundary; }
                case "3개월 뒤" -> from = boundary.plusDays(1);
                case "이미 철수" -> through = today.minusDays(1);
                default -> throw CodeFailure.invalid("period", "기간을 골라 주세요.");
            }
        }
        return new ListFilter(filter.year(), from, through, filter.extensionPossibilityCode(),
            job == null || job.isEmpty() ? null : job);
    }

    public WithdrawalList getCommonWithdrawals(Filter filter) {
        var rows = mapper.list(filter(filter, filter.period()));
        var items = rows.stream().map(this::row).toList();
        Banner banner = null;
        if (filter.year() == calendar.today().getYear()) {
            var distinct = new LinkedHashMap<String, Source>();
            for (var source : mapper.list(filter(filter, "3개월 안"))) {
                var current = mapper.reservations(source.developerId());
                if (rules.bannerCandidate(source.endDate(), calendar.today(), source.extensionPossibilityCode(), !current.isEmpty())) {
                    distinct.putIfAbsent(source.developerId(), source);
                }
            }
            int matched = (int) distinct.values().stream()
                .filter(source -> !recommendations.candidates(source, Collections.emptyList(), false).isEmpty()).count();
            banner = new Banner(distinct.size(), matched);
        }
        return new WithdrawalList(items, items.size(), banner);
    }

    private Row row(Source source) {
        var current = mapper.reservations(source.developerId());
        var reservation = WithdrawalTentativeService.one(current);
        int days = rules.remainingDays(source.endDate(), calendar.today());
        int count = days < 0 || reservation != null ? 0 : recommendations.candidates(source, current, false).size();
        var early = assignments.history(source.assignmentId()).stream().filter(h -> "EARLY".equals(h.type()))
            .map(h -> h.effectiveDate() + " · " + Objects.requireNonNullElse(h.afterValue(), "")).toList();
        return new Row(source.assignmentId(), source.developerId(), source.name(), source.projectName(),
            source.jobName(), source.endDate(), days, days < 0, source.extensionCount(), early,
            codes.extensionResponseCode(source.extensionPossibilityCode()), codes.extensionName(source.extensionPossibilityCode()),
            new NextAssignment(reservation == null ? null : reservation.opportunityName(), count, reservation != null), source.modifiedAt());
    }

    public SharedList getCommonWithdrawalAssignments(String developerId, String opportunityId) {
        if (developerId != null) CommonAssignmentService.identifier(developerId, "developerId");
        if (opportunityId != null) CommonAssignmentService.identifier(opportunityId, "opportunityId");
        var rows = mapper.shared(new SharedFilter(developerId, opportunityId)).stream().map(source -> {
            var current = WithdrawalTentativeService.one(mapper.reservations(source.developerId()));
            return new SharedRow(source.assignmentId(), source.developerId(), source.name(),
                codes.extensionResponseCode(source.extensionPossibilityCode()), codes.extensionName(source.extensionPossibilityCode()),
                tentative.view(source, current));
        }).toList();
        return new SharedList(rows);
    }

    @Transactional
    public ExtensionResult putCommonWithdrawalExtensionPossibility(String assignmentId, JsonNode body) {
        CommonAssignmentService.identifier(assignmentId, "assignmentId");
        ClientRules.object(body, Set.of("extensionPossibilityCode", "modifiedAt"));
        JsonNode code = body.get("extensionPossibilityCode");
        if (code == null || !code.isTextual()) throw CodeFailure.invalid("extensionPossibilityCode", "연장 가능성을 골라 주세요.");
        codes.extensionStorageCode(code.textValue());
        CommonAssignmentService.token(body);
        var bulk = JsonNodeFactory.instance.objectNode();
        bulk.set("extensionPossibilityCode", code);
        var item = bulk.putArray("assignments").addObject().put("assignmentId", assignmentId);
        item.set("modifiedAt", body.get("modifiedAt"));
        var result = assignmentService.putCommonAssignmentExtensionPossibility(bulk).items().get(0);
        return new ExtensionResult(result.assignmentId(), codes.extensionResponseCode(result.extensionPossibilityCode()), result.modifiedAt());
    }
}
