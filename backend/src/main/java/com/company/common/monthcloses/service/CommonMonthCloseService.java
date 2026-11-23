package com.company.common.monthcloses.service;

import com.company.common.monthcloses.dto.MonthCloseContracts.*;
import com.company.common.monthcloses.mapper.MonthCloseMapper;
import com.company.common.partsettlements.dto.PartSettlementContracts.CloseInput;
import com.company.common.partsettlements.service.PartSettlementAutoConfirmService;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
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
    private final MonthCloseCheckRecorder recorder;
    public CommonMonthCloseService(MonthCloseMapper mapper, MonthCloseCheckService checks,
        MonthCloseSnapshotService snapshots, PartSettlementAutoConfirmService settlements,
        CurrentBusinessActor actor, MonthClosePeriodService period, MonthCloseCheckRecorder recorder) {
        this.mapper = mapper;
        this.checks = checks;
        this.snapshots = snapshots;
        this.settlements = settlements;
        this.actor = actor;
        this.period = period;
        this.recorder = recorder;
    }
    private record View(MonthClose close, List<StoredCheck> stored, boolean recordable) {}
    /** GET is also [다시 점검]: an open month's six results are saved to TB_CL_CLOSE_CHK in a separate transaction. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MonthClose getCommonMonthClose(String yearMonth) {
        var month = MonthCloseRules.month(yearMonth);
        var view = view(month);
        if (view.recordable()) recorder.record(period.db(month), view.stored(), actor.userId());
        return view.close();
    }
    private MonthClose read(YearMonth month) { return view(month).close(); }
    private View view(YearMonth month) {
        String db = period.db(month);
        var row = mapper.selectHeader(db);
        YearMonth base = period.baseOrNull();
        // Same judgement as UserPreferencePeriodBasis: months before CASH_BASE_YM are migrated, i.e. closed.
        boolean implied = base != null && month.isBefore(base);
        boolean stored = row != null && "CLOSE".equals(row.state());
        boolean closed = implied || stored;
        Instant modifiedAt = row == null ? null : row.modifiedAt();
        Instant closedAt = stored ? row.closedAt() : null;
        String closedBy = stored ? row.closedBy() : null;
        if (implied)
            return new View(new MonthClose(month.toString(), true, false, modifiedAt, closedAt, closedBy,
                0, 0, List.of()), List.of(), false);
        boolean reopenable = stored && Objects.equals(db, mapper.latestClosedMonth());
        var result = checks.calculate(month);
        boolean recordable = !closed && base != null && !month.isAfter(period.currentMonth());
        return new View(new MonthClose(month.toString(), closed, reopenable, modifiedAt, closedAt, closedBy,
            result.checks().size(), result.remaining().size(), result.checks()), result.stored(), recordable);
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
        affected(mapper.insertAccess(period.db(month), actor.userId(), "CLOSE"));
        return read(month);
    }
    @Transactional(rollbackFor = Exception.class)
    public MonthClose putCommonMonthCloseReopen(String yearMonth, ReopenInput input) {
        var month = MonthCloseRules.month(yearMonth);
        // Role first: a non-administrator never takes the month locks or learns the current version.
        if (!MonthCloseRules.canReopen(actor.roles()))
            throw CodeFailure.invalid("reason", "시스템 관리자만 마감을 해제할 수 있습니다.");
        MonthCloseRules.reason(input.reason());
        period.lock();
        var old = current(month, input.modifiedAt());
        if (old == null || !"CLOSE".equals(old.state()) || month.isBefore(period.base())
            || !Objects.equals(period.db(month), mapper.latestClosedMonth()))
            throw CodeFailure.invalid("yearMonth", "가장 최근 마감월만 해제할 수 있습니다.");
        snapshots.unfix(period.db(month));
        affected(mapper.saveClose(new Mutation(period.db(month), "WORK", actor.userId(), input.modifiedAt())));
        affected(mapper.insertHistory(new History(period.db(month), "OPEN", input.reason(), null, actor.userId())));
        affected(mapper.insertAccess(period.db(month), actor.userId(), "UNCLOSE"));
        return read(month);
    }
    private Header current(YearMonth month, Instant version) {
        var old = mapper.selectHeader(period.db(month));
        MonthCloseRules.version(old == null ? null : old.modifiedAt(), version);
        return old;
    }
    private static void affected(int count) { if (count != 1) throw CodeFailure.conflict(); }
}
