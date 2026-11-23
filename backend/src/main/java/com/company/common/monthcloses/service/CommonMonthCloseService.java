package com.company.common.monthcloses.service;

import com.company.common.monthcloses.dto.MonthCloseContracts.*;
import com.company.common.monthcloses.mapper.MonthCloseMapper;
import com.company.common.partsettlements.dto.PartSettlementContracts.CloseInput;
import com.company.common.partsettlements.service.PartSettlementAutoConfirmService;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommonMonthCloseService {
    private final MonthCloseMapper mapper;
    private final MonthCloseCheckService checks;
    private final MonthCloseSnapshotService snapshots;
    private final PartSettlementAutoConfirmService settlements;
    private final CurrentBusinessActor actor;
    private final MonthClosePeriodService period;
    public CommonMonthCloseService(MonthCloseMapper mapper, MonthCloseCheckService checks,
        MonthCloseSnapshotService snapshots, PartSettlementAutoConfirmService settlements,
        CurrentBusinessActor actor, MonthClosePeriodService period) {
        this.mapper = mapper;
        this.checks = checks;
        this.snapshots = snapshots;
        this.settlements = settlements;
        this.actor = actor;
        this.period = period;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MonthClose getCommonMonthClose(String yearMonth) {
        return read(MonthCloseRules.month(yearMonth));
    }
    private MonthClose read(YearMonth month) {
        var row = mapper.selectHeader(period.db(month));
        var result = checks.calculate(month);
        boolean closed = row != null && "CLOSE".equals(row.state());
        return new MonthClose(month.toString(), closed, row == null ? null : row.modifiedAt(),
            closed ? row.closedAt() : null, closed ? row.closedBy() : null,
            result.checks().size(), result.remaining().size(), result.checks());
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReopenImpact getCommonMonthCloseReopenImpact(String yearMonth) {
        var month = MonthCloseRules.month(yearMonth);
        return new ReopenImpact(yearMonth, mapper.adjustmentCount(period.db(month), period.db(month.plusMonths(1))));
    }
    @Transactional(rollbackFor = Exception.class)
    public MonthClose putCommonMonthCloseConfirm(String yearMonth, ConfirmInput input) {
        var month = MonthCloseRules.month(yearMonth);
        period.lock();
        current(month, input.modifiedAt());
        period.confirmable(month);
        var remaining = checks.calculate(month).remaining();
        String names = String.join(",", remaining);
        if (names.codePointCount(0, names.length()) > 500)
            throw CodeFailure.invalid("source", "남은 점검 이름의 저장 범위를 확인해 주세요.");
        snapshots.freeze(month, period.db(period.base()), actor.userId());
        var targets = settlements.getCommonPartSettlementCloseChecks(yearMonth);
        settlements.putCommonPartSettlementCloseConfirm(new CloseInput(yearMonth, targets.items()));
        affected(mapper.saveClose(new Mutation(period.db(month), "CLOSE", actor.userId(), input.modifiedAt())));
        affected(mapper.insertHistory(new History(period.db(month), "CLOSE", null, names, actor.userId())));
        affected(mapper.insertAccess(period.db(month), actor.userId()));
        return read(month);
    }
    @Transactional(rollbackFor = Exception.class)
    public MonthClose putCommonMonthCloseReopen(String yearMonth, ReopenInput input) {
        var month = MonthCloseRules.month(yearMonth);
        MonthCloseRules.reason(input.reason());
        period.lock();
        var old = current(month, input.modifiedAt());
        if (!MonthCloseRules.canReopen(actor.roles()))
            throw CodeFailure.invalid("reason", "시스템 관리자만 마감을 해제할 수 있습니다.");
        if (old == null || !"CLOSE".equals(old.state())
            || !Objects.equals(period.db(month), mapper.latestClosedMonth()))
            throw CodeFailure.invalid("yearMonth", "가장 최근 마감월만 해제할 수 있습니다.");
        snapshots.unfix(period.db(month));
        affected(mapper.saveClose(new Mutation(period.db(month), "WORK", actor.userId(), input.modifiedAt())));
        affected(mapper.insertHistory(new History(period.db(month), "OPEN", input.reason(), null, actor.userId())));
        return read(month);
    }
    private Header current(YearMonth month, Instant version) {
        var old = mapper.selectHeader(period.db(month));
        MonthCloseRules.version(old == null ? null : old.modifiedAt(), version);
        return old;
    }
    private static void affected(int count) { if (count != 1) throw CodeFailure.conflict(); }
}
