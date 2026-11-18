package com.company.common.withdrawals.service;

import com.company.common.clients.service.ClientRules;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.support.service.*;
import com.company.common.withdrawals.dto.WithdrawalContracts.*;
import com.company.common.withdrawals.mapper.WithdrawalMapper;
import com.company.common.withdrawals.mapper.WithdrawalRows.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WithdrawalTentativeService {
    private final WithdrawalMapper mapper;
    private final ProjectMapper projects;
    private final WithdrawalRecommendationService recommendations;
    private final WithdrawalCalculationRules rules;
    private final BusinessCalendar calendar;
    private final CurrentBusinessActor actor;

    public WithdrawalTentativeService(WithdrawalMapper mapper, ProjectMapper projects,
        WithdrawalRecommendationService recommendations, WithdrawalCalculationRules rules,
        BusinessCalendar calendar, CurrentBusinessActor actor) {
        this.mapper = mapper;
        this.projects = projects;
        this.recommendations = recommendations;
        this.rules = rules;
        this.calendar = calendar;
        this.actor = actor;
    }

    public TentativeResult current(String assignmentId) {
        var source = recommendations.required(assignmentId);
        return new TentativeResult(view(source, one(mapper.reservations(source.developerId()))));
    }

    static Reservation one(List<Reservation> current) {
        if (current.size() > 1) throw new CodeFailure(409, "needSn", "여러 가배정이 있습니다. 기존 배정을 확인해 주세요.");
        return current.isEmpty() ? null : current.get(0);
    }

    public TentativeAssignment view(Source source, Reservation reservation) {
        return reservation == null ? null : new TentativeAssignment(source.assignmentId(), source.developerId(),
            reservation.opportunityId(), reservation.opportunityName(), reservation.needSn(),
            calendar.fromDatabaseMonth(reservation.startMonth()).toString());
    }

    @Transactional
    public TentativeResult save(String assignmentId, JsonNode body) {
        ClientRules.object(body, Set.of("needSn"));
        JsonNode value = body.get("needSn");
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
            || value.intValue() < 1 || value.intValue() > 999) {
            throw CodeFailure.invalid("needSn", "추천 자리의 필요 인력 줄을 골라 주세요.");
        }
        int needSn = value.intValue();
        Source observed = recommendations.required(assignmentId);
        projects.lockSources();
        if (mapper.lockDeveloper(observed.developerId()) == null) throw CodeFailure.conflict();
        Source source = mapper.lockSource(assignmentId);
        if (source == null || !Objects.equals(observed.developerId(), source.developerId())) throw CodeFailure.conflict();
        var reservations = mapper.reservations(source.developerId());
        Reservation current = one(reservations);
        mapper.lockNeeds();
        var needs = mapper.needs();
        // The current composite identity is retained for idempotency, not guessed from NEED_SN.
        var matching = needs.stream().filter(n -> n.needSn() == needSn)
            .filter(n -> current != null && WithdrawalRecommendationService.sameNeed(current, n)
                || recommendations.eligible(source) && recommendations.matches(source, n)
                    && n.occupiedCount() < n.requiredCount()).toList();
        if (matching.size() > 1) throw CodeFailure.invalid("needSn", "여러 기회의 필요 인력 줄이 같습니다. 기회를 구분할 수 없는 선택입니다.");
        if (matching.isEmpty()) {
            boolean full = needs.stream().filter(n -> n.needSn() == needSn)
                .anyMatch(n -> recommendations.matches(source, n) && n.occupiedCount() >= n.requiredCount());
            if (full) throw new CodeFailure(409, "needSn", "남은 자리가 없습니다. 다시 불러와 주세요.");
            throw CodeFailure.invalid("needSn", "현재 인력에게 맞는 추천 자리를 골라 주세요.");
        }
        Need target = matching.get(0);
        if (current != null && WithdrawalRecommendationService.sameNeed(current, target)) {
            return new TentativeResult(view(source, current));
        }
        if (current != null && "CNVT".equals(current.statusCode())) {
            throw new CodeFailure(409, "needSn", "투입 배정으로 넘긴 후보입니다. 투입 상세에서 확인해 주세요.");
        }
        if (!recommendations.eligible(source) || !recommendations.matches(source, target)) {
            throw CodeFailure.invalid("needSn", "현재 인력은 신규 추천 대상이 아닙니다.");
        }
        if (target.occupiedCount() >= target.requiredCount()) {
            throw new CodeFailure(409, "needSn", "남은 자리가 없습니다. 다시 불러와 주세요.");
        }
        var start = rules.tentativeStartMonth(source.endDate(), calendar.fromDatabaseMonth(target.startMonth()));
        if (current != null && mapper.release(current.tentativeId(), actor.userId()) != 1) throw CodeFailure.conflict();
        if (mapper.insertReservation(new ReservationWrite(source.developerId(), target.opportunityId(),
            target.needSn(), calendar.toDatabaseMonth(start), actor.userId())) != 1) throw CodeFailure.conflict();
        return new TentativeResult(view(source, one(mapper.reservations(source.developerId()))));
    }
}
