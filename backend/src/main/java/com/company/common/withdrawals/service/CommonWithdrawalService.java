package com.company.common.withdrawals.service;

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
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommonWithdrawalService {
    private final WithdrawalMapper mapper;
    private final CommonAssignmentService assignmentService;
    private final WithdrawalRecommendationService recommendations;
    private final WithdrawalTentativeService tentative;
    private final WithdrawalCodes codes;
    private final BusinessCalendar calendar;

    public CommonWithdrawalService(WithdrawalMapper mapper, CommonAssignmentService assignmentService,
        WithdrawalRecommendationService recommendations, WithdrawalTentativeService tentative,
        WithdrawalCodes codes, BusinessCalendar calendar) {
        this.mapper = mapper;
        this.assignmentService = assignmentService;
        this.recommendations = recommendations;
        this.tentative = tentative;
        this.codes = codes;
        this.calendar = calendar;
    }

    public Instant now() { return calendar.now(); }
    public String userId() { return assignmentService.userId(); }
    /** 철수 리스트 엑셀을 받으면 접근 기록(DNLD)을 남긴다(다른 목록 엑셀과 같음, 2026-11-19 엑셀 점검에서 찾음). */
    @Transactional
    public void recordDownload(int count, Integer year) { mapper.insertAccess(userId(), "철수 리스트 " + (year == null ? "" : year + "년 ") + count + "건"); }

    private ListFilter filter(Filter filter, String period, WithdrawalRecommendationService.Context context) {
        if (filter.year() == null || filter.year() < 1 || filter.year() > 9999) {
            throw CodeFailure.invalid("year", "연도를 확인해 주세요.");
        }
        if (filter.extensionPossibilityCode() != null) codes.extensionStorageCode(filter.extensionPossibilityCode());
        String job = filter.jobCode();
        if (job != null && !job.isEmpty() && !context.codes().hasJob(job)) {
            throw CodeFailure.invalid("jobCode", "등록된 직무를 골라 주세요.");
        }
        LocalDate from = null, through = null;
        LocalDate today = calendar.today();
        LocalDate boundary = boundary(context);
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

    private LocalDate boundary(WithdrawalRecommendationService.Context context) {
        return calendar.currentMonth().plusMonths(context.rules().nearTermMonthOffset()).atEndOfMonth();
    }

    /**
     * 목록은 줄마다 따로 읽지 않는다: 가배정·조기 철수 이력은 한 번의 묶음 조회로, 식·공통코드·필요 인력 줄은
     * 요청마다 한 번만 읽는다(투입 배정 목록과 같은 방식).
     */
    public WithdrawalList getCommonWithdrawals(Filter filter) {
        var context = recommendations.context();
        var rows = mapper.list(filter(filter, filter.period(), context));
        LocalDate today = calendar.today();
        boolean currentYear = filter.year() == today.getYear();
        // 안내 띠(식-82)는 조건과 관계없이 오늘부터 3개월 안 철수 예정 배정 전체를 센다(시안 nextSummary).
        List<Source> soon = currentYear
            ? mapper.list(new ListFilter(null, today, boundary(context), null, null)) : List.of();
        var developers = new LinkedHashSet<String>();
        rows.forEach(s -> developers.add(s.developerId()));
        soon.forEach(s -> developers.add(s.developerId()));
        Map<String, List<Reservation>> reserved = developers.isEmpty() ? Map.of()
            : mapper.reservationsFor(developers).stream().collect(Collectors.groupingBy(Reservation::developerId));
        var assignmentIds = rows.stream().map(Source::assignmentId).distinct().toList();
        Map<String, List<EarlyRow>> early = assignmentIds.isEmpty() ? Map.of()
            : mapper.earlyHistory(assignmentIds).stream().collect(Collectors.groupingBy(EarlyRow::assignmentId));
        var items = rows.stream().map(source -> row(source,
            reserved.getOrDefault(source.developerId(), List.of()),
            early.getOrDefault(source.assignmentId(), List.of()), context, today)).toList();
        Banner banner = null;
        if (currentYear) {
            int unassigned = 0, matched = 0;
            for (var source : soon) {
                var current = reserved.getOrDefault(source.developerId(), List.of());
                if (!context.rules().bannerCandidate(source.endDate(), today, source.extensionPossibilityCode(), !current.isEmpty())) continue;
                unassigned++;
                if (recommendations.candidates(source, List.of(), false, context).size() >= context.rules().minimumSeats()) matched++;
            }
            banner = new Banner(soon.size(), unassigned, matched);
        }
        return new WithdrawalList(items, items.size(), banner);
    }

    private Row row(Source source, List<Reservation> current, List<EarlyRow> earlyRows,
        WithdrawalRecommendationService.Context context, LocalDate today) {
        var reservation = WithdrawalTentativeService.one(current);
        int days = context.rules().remainingDays(source.endDate(), today);
        boolean target = reservation == null && recommendations.eligible(source, context);
        int count = target ? recommendations.candidates(source, current, false, context).size() : 0;
        // 시안 다음 배정 칸의 작은 글자: 대상이 아니면 그 까닭, 대상인데 자리가 없으면 「맞는 자리 없음」.
        String note = reservation != null || days < 0 ? null
            : target ? (count == 0 ? "맞는 자리 없음" : null)
            : "H".equals(source.extensionPossibilityCode()) ? "연장 가능성 높음" : "3개월 뒤 철수";
        var early = earlyRows.stream()
            .map(h -> h.effectiveDate() + " · " + Objects.requireNonNullElse(h.afterValue(), "")).toList();
        return new Row(source.assignmentId(), source.developerId(), source.name(), source.projectName(),
            source.jobName(), source.endDate(), days, days < 0, source.extensionCount(), early,
            codes.extensionResponseCode(source.extensionPossibilityCode()),
            context.codes().extensionName(source.extensionPossibilityCode()),
            new NextAssignment(reservation == null ? null : reservation.opportunityName(), count,
                reservation != null, target, note),
            source.modifiedAt(), source.projectId(), source.managementNumber());
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
