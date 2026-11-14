package com.company.common.parts;

import com.company.common.dto.UserContracts.History;
import com.company.common.mapper.UserMapper;
import com.company.common.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.service.CalculationRuleService;
import com.company.common.service.ClientPartPolicy;
import com.company.common.service.CodeFailure;
import com.company.common.service.CurrentBusinessActor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static com.company.common.parts.PartContracts.*;
import static com.company.common.parts.PartRules.invalid;

@Service
public class CommonPartService {
    private static final DateTimeFormatter DB_MONTH = DateTimeFormatter.ofPattern("uuuuMM");
    private final PartMapper mapper;
    private final UserMapper management;
    private final UserPreferencePeriodSourceMapper periods;
    private final CalculationRuleService calculations;
    private final CurrentBusinessActor actor;
    private final TransactionTemplate creation;

    public CommonPartService(PartMapper mapper, UserMapper management,
        UserPreferencePeriodSourceMapper periods, CalculationRuleService calculations,
        CurrentBusinessActor actor, PlatformTransactionManager transactions) {
        this.mapper = mapper;
        this.management = management;
        this.periods = periods;
        this.calculations = calculations;
        this.actor = actor;
        this.creation = new TransactionTemplate(transactions);
        this.creation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public Items<Part> getCommonParts() {
        String now = mapper.selectCurrentMonth();
        return new Items<>(mapper.selectParts(now).stream().map(row -> item(row, now)).toList());
    }
    @Transactional(readOnly = true)
    public Options getCommonPartOptions() {
        YearMonth now = fromDb(mapper.selectCurrentMonth());
        String closed = lastClosed();
        return new Options(mapper.selectLeaders().stream()
            .map(row -> new Leader(row.userId, row.userName)).toList(),
            calculations.effectiveStandard("PART_DSTRB_RT", now).value(),
            calculations.effectiveStandard("PART_SALES_EXPS", now).value(),
            publicMonth(closed), earliest(closed).toString());
    }
    @Transactional(readOnly = true)
    public Items<Standard> getCommonPartStandards(String id) {
        var master = require(id);
        String now = mapper.selectCurrentMonth();
        return new Items<>(mapper.selectPeriods(id).stream().map(row -> new Standard(id,
            row.leaderUserId, row.leaderName, row.distributionRate, row.salesExpense,
            publicMonth(row.month), publicMonth(row.endMonth), master.modifiedAt,
            row.month.compareTo(now) > 0 ? "예정"
                : row.endMonth != null && row.endMonth.compareTo(now) < 0 ? "지난" : "적용 중")).toList());
    }
    @Transactional(readOnly = true)
    public Items<Change> getCommonPartChangeHistory(String id) {
        require(id);
        return new Items<>(mapper.selectHistory(id).stream().map(row -> new Change(
            row.changedAt, row.item, row.before, row.after, row.changedBy)).toList());
    }

    public Part postCommonPart(Create input) {
        if (!ClientPartPolicy.canCreatePart(actor.roles()))
            throw invalid("partName", "관리자 또는 재무 담당자만 파트를 추가할 수 있습니다.");
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return creation.execute(status -> create(input));
            } catch (DuplicateKeyException collision) {
                if (attempt == 2) throw CodeFailure.conflict();
            }
        }
        throw CodeFailure.conflict();
    }
    private Part create(Create input) {
        lock();
        uniqueName(input.partName(), null);
        validate(input.values());
        // Defaults are read again under the same management lock, never applied to an existing part.
        getCommonPartOptions();
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        affected(mapper.insertPart(id, input.partName()));
        String month = db(PartRules.month(input.values().effectiveMonth()));
        affected(mapper.insertPeriod(new PeriodMutation(id, month, null, input.values())));
        history("TB_BS_PART", id, "NEW", "파트명", null, input.partName());
        periodHistory(id, month, null, input.values());
        return item(require(id), mapper.selectCurrentMonth());
    }
    @Transactional
    public Part putCommonPart(String id, Rename input) {
        lock();
        var old = current(id, input.modifiedAt());
        uniqueName(input.partName(), id);
        if (!old.partName.equals(input.partName())) {
            affected(mapper.touch(id, input.modifiedAt()));
            affected(mapper.rename(id, input.partName()));
            history("TB_BS_PART", id, "UPD", "파트명", old.partName, input.partName());
        }
        return item(require(id), mapper.selectCurrentMonth());
    }
    @Transactional
    public Part putCommonPartStop(String id, Version input) {
        lock();
        var old = current(id, input.modifiedAt());
        if (old.personCount > 0 || old.projectCount > 0)
            throw invalid("partId", "소속 인력과 진행 프로젝트를 먼저 다른 파트로 이동해 주세요.");
        if ("Y".equals(old.useYn)) {
            affected(mapper.touch(id, input.modifiedAt()));
            affected(mapper.stop(id));
            history("TB_BS_PART", id, "UPD", "사용 여부", old.useYn, "N");
        }
        return item(require(id), mapper.selectCurrentMonth());
    }
    @Transactional
    public Items<Standard> putCommonPartStandards(String id, StandardInput input) {
        lock();
        current(id, input.modifiedAt());
        validate(input.values());
        calculations.definition("식-00");
        calculations.definition("식-45");
        String month = db(PartRules.month(input.values().effectiveMonth()));
        var rows = mapper.selectPeriods(id);
        var same = rows.stream().filter(row -> row.month.equals(month)).findFirst().orElse(null);
        var before = applicable(rows, month);
        if (before != null && unchanged(before, input.values()))
            throw invalid("distributionRate", "지금 기준과 같습니다");
        if (same != null && month.compareTo(mapper.selectCurrentMonth()) <= 0)
            throw invalid("effectiveMonth", "같은 시작월 수정은 미래 예정 기준만 가능합니다.");
        boolean amountChanged = before == null
            || before.distributionRate.compareTo(input.values().distributionRate()) != 0
            || before.salesExpense.compareTo(input.values().salesExpense()) != 0;
        if (amountChanged) {
            String confirmed = mapper.selectLastConfirmed(id, month);
            if (confirmed != null) throw invalid("effectiveMonth", "확정 정산을 취소하거나 "
                + fromDb(confirmed).plusMonths(1) + " 이후를 선택해 주세요.");
        }
        var next = rows.stream().filter(row -> row.month.compareTo(month) > 0)
            .min(Comparator.comparing(row -> row.month)).orElse(null);
        String end = next == null ? null : db(fromDb(next.month).minusMonths(1));
        affected(mapper.touch(id, input.modifiedAt()));
        if (same != null) {
            affected(mapper.updatePeriod(new PeriodMutation(id, month, same.endMonth, input.values())));
        } else {
            var previous = rows.stream().filter(row -> row.month.compareTo(month) < 0)
                .max(Comparator.comparing(row -> row.month)).orElse(null);
            if (previous != null) {
                String previousEnd = db(fromDb(month).minusMonths(1));
                affected(mapper.endPeriod(id, previous.month, previousEnd));
                history("TB_BS_PART_STD", id + ":" + previous.month, "UPD", "적용 종료월",
                    publicMonth(previous.endMonth), publicMonth(previousEnd));
            }
            affected(mapper.insertPeriod(new PeriodMutation(id, month, end, input.values())));
            history("TB_BS_PART_STD", id + ":" + month, "NEW", "적용 기간", null,
                publicMonth(month) + " ~ " + Objects.toString(publicMonth(end), "계속"));
        }
        periodHistory(id, month, before, input.values());
        return getCommonPartStandards(id);
    }
    private void lock() {
        management.lockManagement();
        mapper.lockSources();
    }
    private void validate(Values values) {
        if (PartRules.month(values.effectiveMonth()).isBefore(earliest(lastClosed())))
            throw invalid("effectiveMonth", "마지막 마감 다음 달(마감이 없으면 자금수지 시작월)부터 선택해 주세요.");
        if (mapper.selectLeaders().stream().noneMatch(row -> row.userId.equals(values.leaderUserId())))
            throw invalid("leaderUserId", "사용 중인 영업 파트장 사용자를 선택해 주세요.");
    }
    private YearMonth earliest(String closed) {
        BigDecimal value = periods.selectCashBaseYm();
        if (value == null) throw invalid("effectiveMonth", "자금수지 시작월을 확인해 주세요.");
        YearMonth base;
        try { base = fromDb(value.toBigIntegerExact().toString()); }
        catch (RuntimeException failure) { throw invalid("effectiveMonth", "자금수지 시작월을 확인해 주세요."); }
        return closed == null || fromDb(closed).plusMonths(1).isBefore(base)
            ? base : fromDb(closed).plusMonths(1);
    }
    private String lastClosed() {
        return periods.selectClosedMonths().stream().max(String::compareTo).orElse(null);
    }
    private void uniqueName(String name, String exceptId) {
        if (mapper.nameExists(name, exceptId)) throw invalid("partName", "이미 쓰고 있는 파트명입니다.");
    }
    private PartRows.Master require(String id) {
        if (id == null || id.isBlank() || id.length() > 20) throw invalid("partId", "파트를 확인해 주세요.");
        var row = mapper.selectMaster(id);
        if (row == null) throw invalid("partId", "파트를 찾을 수 없습니다.");
        return row;
    }
    private PartRows.Master current(String id, Instant token) {
        var row = require(id);
        if (!Objects.equals(row.modifiedAt, token)) throw CodeFailure.conflict();
        return row;
    }
    private Part item(PartRows.Master master, String now) {
        var rows = mapper.selectPeriods(master.partId);
        var row = applicable(rows, now);
        if (row == null) row = rows.stream().min(Comparator.comparing(value -> value.month))
            .orElseThrow(() -> invalid("partId", "파트의 기준 기간을 확인해 주세요."));
        var next = rows.stream().filter(value -> value.month.compareTo(now) > 0)
            .min(Comparator.comparing(value -> value.month)).orElse(null);
        String scheduled = next == null ? null : publicMonth(next.month) + "월부터 "
            + next.distributionRate.stripTrailingZeros().toPlainString() + "%";
        return new Part(master.partId, master.partName, row.leaderUserId, row.leaderName,
            row.distributionRate, row.salesExpense, publicMonth(row.month), publicMonth(row.endMonth),
            master.useYn, master.modifiedAt, master.personCount, master.projectCount, scheduled);
    }
    private PartRows.Period applicable(List<PartRows.Period> rows, String month) {
        return rows.stream().filter(row -> row.month.compareTo(month) <= 0
            && (row.endMonth == null || row.endMonth.compareTo(month) >= 0))
            .max(Comparator.comparing(row -> row.month)).orElse(null);
    }
    private boolean unchanged(PartRows.Period before, Values after) {
        return before.leaderUserId.equals(after.leaderUserId())
            && before.distributionRate.compareTo(after.distributionRate()) == 0
            && before.salesExpense.compareTo(after.salesExpense()) == 0;
    }
    private void periodHistory(String id, String month, PartRows.Period before, Values after) {
        String key = id + ":" + month;
        historyIfChanged(key, "파트장", before == null ? null : before.leaderUserId, after.leaderUserId());
        historyIfChanged(key, "이익 배분율", before == null ? null : before.distributionRate.stripTrailingZeros().toPlainString(),
            after.distributionRate().stripTrailingZeros().toPlainString());
        historyIfChanged(key, "공통관리비", before == null ? null : before.salesExpense.stripTrailingZeros().toPlainString(),
            after.salesExpense().stripTrailingZeros().toPlainString());
    }
    private void historyIfChanged(String key, String field, String before, String after) {
        if (!Objects.equals(before, after)) history("TB_BS_PART_STD", key, before == null ? "NEW" : "UPD", field, before, after);
    }
    private void history(String table, String key, String type, String field, String before, String after) {
        management.insertHistory(new History(table, key, type, field, before, after));
    }
    private void affected(int count) { if (count != 1) throw CodeFailure.conflict(); }
    private static String db(YearMonth month) { return month.format(DB_MONTH); }
    private static YearMonth fromDb(String month) { return YearMonth.parse(month, DB_MONTH); }
    private static String publicMonth(String month) { return month == null ? null : fromDb(month).toString(); }
}
