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
    private final BusinessCalendar calendar;
    private final CurrentBusinessActor actor;

    public WithdrawalTentativeService(WithdrawalMapper mapper, ProjectMapper projects,
        WithdrawalRecommendationService recommendations, BusinessCalendar calendar, CurrentBusinessActor actor) {
        this.mapper = mapper;
        this.projects = projects;
        this.recommendations = recommendations;
        this.calendar = calendar;
        this.actor = actor;
    }

    public TentativeResult current(String assignmentId) {
        var source = recommendations.required(assignmentId);
        return new TentativeResult(view(source, one(mapper.reservations(source.developerId()))));
    }

    static Reservation one(List<Reservation> current) {
        if (current.stream().map(Reservation::opportunityId).distinct().count() > 1)
            throw new CodeFailure(409, "needSn", "여러 기회에 가배정이 있습니다. 기존 배정을 확인해 주세요.");
        return current.stream().min(Comparator.comparingInt(Reservation::needSn)).orElse(null);
    }

    public TentativeAssignment view(Source source, Reservation reservation) {
        return reservation == null ? null : new TentativeAssignment(source.assignmentId(), source.developerId(),
            reservation.opportunityId(), reservation.opportunityName(), reservation.needSn(),
            calendar.fromDatabaseMonth(reservation.startMonth()).toString());
    }

    /**
     * 추천 카드의 자리(기회 ID + NEED_SN)로 가배정한다. 필요 인력 줄의 키는 (OPP_ID, NEED_SN)이라 둘 다 받는다.
     * 한 사람은 한 기회에만 가배정되고, 바꾸면 이전 가배정을 풀고(REL) 새 자리를 차지한다. 확정 배정·MM 1.0에는 넣지 않는다.
     */
    @Transactional
    public TentativeResult save(String assignmentId, JsonNode body) {
        ClientRules.object(body, Set.of("opportunityId", "needSn"));
        JsonNode value = body.get("needSn");
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
            || value.intValue() < 1 || value.intValue() > 999) {
            throw CodeFailure.invalid("needSn", "추천 자리의 필요 인력 줄을 골라 주세요.");
        }
        int needSn = value.intValue();
        JsonNode opportunity = body.get("opportunityId");
        if (opportunity == null || !opportunity.isTextual() || opportunity.textValue().isBlank()
            || opportunity.textValue().length() > 20) {
            throw CodeFailure.invalid("opportunityId", "추천 자리의 영업 기회를 골라 주세요.");
        }
        String opportunityId = opportunity.textValue();
        Source observed = recommendations.required(assignmentId);
        projects.lockSources();
        if (mapper.lockDeveloper(observed.developerId()) == null) throw CodeFailure.conflict();
        Source source = mapper.lockSource(assignmentId);
        if (source == null || !Objects.equals(observed.developerId(), source.developerId())) throw CodeFailure.conflict();
        var reservations = mapper.reservations(source.developerId());
        if (reservations.size() > 1) throw new CodeFailure(409, "needSn", "여러 가배정이 있습니다. 영업 기회에서 줄을 선택해 주세요.");
        Reservation current = one(reservations);
        mapper.lockNeeds();
        // 잠근 뒤에 필요 인력 줄을 처음 읽는다(남은 자리를 최신 값으로 센다).
        var context = recommendations.context();
        Need target = context.needs().stream()
            .filter(n -> n.needSn() == needSn && opportunityId.equals(n.opportunityId())).findFirst().orElse(null);
        if (target == null) throw CodeFailure.invalid("needSn", "현재 인력에게 맞는 추천 자리를 골라 주세요.");
        if (current != null && WithdrawalRecommendationService.sameNeed(current, target)) {
            return new TentativeResult(view(source, current));
        }
        if (current != null && "CNVT".equals(current.statusCode())) {
            throw new CodeFailure(409, "needSn", "투입 배정으로 넘긴 후보입니다. 투입 상세에서 확인해 주세요.");
        }
        if (!recommendations.eligible(source, context) || !recommendations.matches(source, target, context)) {
            throw CodeFailure.invalid("needSn", "현재 인력은 신규 추천 대상이 아닙니다.");
        }
        if (WithdrawalRecommendationService.remaining(target, reservations) <= 0) {
            throw new CodeFailure(409, "needSn", "남은 자리가 없습니다. 다시 불러와 주세요.");
        }
        var start = context.rules().tentativeStartMonth(source.endDate(), calendar.fromDatabaseMonth(target.startMonth()));
        var affected = new TreeSet<String>();
        if (current != null) {
            if (mapper.release(current.tentativeId(), actor.userId()) != 1) throw CodeFailure.conflict();
            affected.add(current.opportunityId());
        }
        if (mapper.insertReservation(new ReservationWrite(source.developerId(), target.opportunityId(),
            target.needSn(), calendar.toDatabaseMonth(start), actor.userId())) != 1) throw CodeFailure.conflict();
        affected.add(opportunityId);
        affected.forEach(id -> mapper.touchOpportunity(id, actor.userId()));
        var saved = mapper.reservations(source.developerId()).stream()
            .filter(r -> opportunityId.equals(r.opportunityId()) && r.needSn() == needSn).findFirst().orElse(null);
        return new TentativeResult(view(source, saved));
    }
}
