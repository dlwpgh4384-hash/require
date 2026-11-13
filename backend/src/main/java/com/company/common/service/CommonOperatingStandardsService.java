package com.company.common.service;

import com.company.common.dto.OperatingStandardsContracts.*;
import com.company.common.dto.UserContracts.History;
import com.company.common.mapper.OperatingStandardRow;
import com.company.common.mapper.OperatingStandardsMapper;
import com.company.common.mapper.UserMapper;
import com.company.common.mapper.UserPreferencePeriodSourceMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.company.common.service.OperatingStandardsRules.*;

@Service
@Transactional(readOnly = true)
public class CommonOperatingStandardsService {
    private final OperatingStandardsMapper mapper;
    private final UserMapper management;
    private final UserPreferencePeriodSourceMapper periods;

    public CommonOperatingStandardsService(OperatingStandardsMapper mapper, UserMapper management,
        UserPreferencePeriodSourceMapper periods) {
        this.mapper = mapper;
        this.management = management;
        this.periods = periods;
    }

    public Result getCommonOperatingStandards() {
        var rows = mapper.selectStandards();
        if (rows.isEmpty()) return new Result(rows.stream().map(row -> new Item(row.standardCode,
            row.standardName, row.value.toPlainString(), row.unit, publicMonth(row.month), false, false,
            row.changedBy)).toList(), lastClosed(periods.selectClosedMonths()));
        var currentMonth = mapper.selectCurrentMonth();
        var state = index(rows);
        String base = baseMonth(state, currentMonth);
        var closed = new HashSet<>(periods.selectClosedMonths());
        var items = new ArrayList<Item>();
        for (var row : rows) {
            if (!editable(row.standardCode)) continue;
            boolean cash = immediate(row.standardCode);
            if (cash && current(state, row.standardCode, currentMonth) != row) continue;
            items.add(new Item(row.standardCode, row.standardName,
                "CASH_BASE_YM".equals(row.standardCode) ? publicMonth(cashMonth(row.value)) : row.value.toPlainString(),
                row.unit, cash ? null : publicMonth(row.month),
                isClosed(cash ? base : row.month, base, closed), cash, row.changedBy));
        }
        return new Result(items, lastClosed(closed));
    }

    private String lastClosed(java.util.Collection<String> closed) {
        return closed.stream().max(String::compareTo).map(month -> publicMonth(month)).orElse(null);
    }

    @Transactional
    public Result putCommonOperatingStandards(Input input) {
        management.lockManagement();
        mapper.lockStandardsAndPeriods();
        if (input == null || input.items() == null || input.items().isEmpty()) throw invalid("items");
        var state = index(mapper.selectStandards());
        var next = new LinkedHashMap<String, TreeMap<String, OperatingStandardRow>>();
        state.forEach((code, history) -> next.put(code, new TreeMap<>(history)));
        String now = mapper.selectCurrentMonth();
        String base = baseMonth(state, now);
        Set<String> closed = new HashSet<>(periods.selectClosedMonths());
        String earliest = base;
        while (closed.contains(earliest))
            earliest = physicalMonth(YearMonth.parse(publicMonth(earliest)).plusMonths(1).toString());
        var plans = new ArrayList<Plan>();
        var seen = new HashSet<String>();
        for (var change : input.items()) {
            if (change == null || change.standardCode() == null || !editable(change.standardCode()))
                throw invalid("standardCode");
            if (!Set.of("add", "update", "delete").contains(Objects.toString(change.action(), "")))
                throw invalid("action");
            var history = state.get(change.standardCode());
            if (history == null || history.isEmpty()) throw invalid("standardCode");
            boolean cash = immediate(change.standardCode());
            String month;
            if (cash) {
                if (change.effectiveMonth() != null) throw invalid("effectiveMonth");
                if (!"update".equals(change.action())) throw invalid("action");
                requireOpen(base, base, closed);
                var old = current(state, change.standardCode(), now);
                if (old == null) throw invalid("standardCode");
                month = old.month;
            } else {
                month = physicalMonth(change.effectiveMonth());
                requireOpen(month, base, closed);
                if (month.compareTo(earliest) < 0) throw closedFailure();
            }
            if (!seen.add(change.standardCode() + ":" + month)) throw invalid("standardCode");
            var old = history.get(month);
            if ("add".equals(change.action()) ? old != null : old == null) throw invalid("effectiveMonth");
            BigDecimal value = "delete".equals(change.action()) ? null : number(change);
            if ("CASH_BASE_YM".equals(change.standardCode())) requireOpen(cashMonth(value), base, closed);
            var template = old == null ? history.lastEntry().getValue() : old;
            var mutation = new Mutation(change.standardCode(), month, value,
                template.standardName, template.unit, template.description);
            plans.add(new Plan(change.action(), mutation, old));
            if (value == null) next.get(change.standardCode()).remove(month);
            else {
                var row = new OperatingStandardRow();
                row.standardCode = mutation.standardCode(); row.month = month;
                row.value = value; row.standardName = mutation.standardName();
                row.unit = mutation.unit(); row.description = mutation.description();
                next.get(change.standardCode()).put(month, row);
            }
        }
        validateIntervals(next, plans, base, closed);
        for (var plan : plans) {
            var value = plan.value();
            String before = plan.before() == null ? null : plan.before().value.toPlainString();
            String after = value.value() == null ? null : value.value().toPlainString();
            if (plan.before() != null && value.value() != null
                && plan.before().value.compareTo(value.value()) == 0) continue;
            int affected = switch (plan.action()) {
                case "add" -> mapper.insertStandard(value);
                case "update" -> mapper.updateStandard(value);
                case "delete" -> mapper.deleteStandard(value);
                default -> throw invalid("action");
            };
            if (affected != 1) throw invalid("standardCode");
            String type = switch (plan.action()) { case "add" -> "NEW"; case "delete" -> "DEL"; default -> "UPD"; };
            management.insertHistory(new History("TB_SY_OPER_STD", value.standardCode() + ":" + value.month(),
                type, value.standardCode() + ":" + publicMonth(value.month()), before, after));
        }
        return getCommonOperatingStandards();
    }

    private void validateIntervals(Map<String, TreeMap<String, OperatingStandardRow>> state,
        List<Plan> plans, String base, Set<String> closed) {
        var changed = plans.stream().filter(plan -> !immediate(plan.value().standardCode())).toList();
        if (changed.isEmpty()) return;
        String first = changed.stream().map(plan -> plan.value().month()).min(String::compareTo).orElseThrow();
        var points = new TreeSet<String>();
        points.add(first);
        state.values().forEach(history -> history.keySet().stream().filter(month -> month.compareTo(first) >= 0)
            .forEach(points::add));
        var codes = new HashSet<String>();
        changed.forEach(plan -> codes.add(plan.value().standardCode()));
        for (var month : points) {
            // A closed boundary may be followed by an open interval with the same effective values.
            String open = month;
            while (isClosed(open, base, closed)) {
                if (open.compareTo(base) < 0) open = base;
                else open = physicalMonth(YearMonth.parse(publicMonth(open)).plusMonths(1).toString());
            }
            if (codes.stream().anyMatch(AI::contains)) {
                BigDecimal sum = BigDecimal.ZERO;
                for (var code : AI) {
                    var row = current(state, code, open);
                    if (row == null || row.value.signum() < 0) throw grouped(code, "AI 배점 합계는 100이어야 합니다.");
                    sum = sum.add(row.value);
                }
                if (sum.compareTo(new BigDecimal("100")) != 0)
                    throw grouped("AI_SCORE", "AI 배점 합계는 100이어야 합니다.");
            }
            if (codes.stream().anyMatch(DUN::contains)) {
                var one = current(state, DUN.get(0), open);
                var two = current(state, DUN.get(1), open);
                var three = current(state, DUN.get(2), open);
                if (one == null || two == null || three == null || one.value.compareTo(two.value) >= 0
                    || two.value.compareTo(three.value) >= 0)
                    throw grouped("DUN_D", "미수 알림은 1단계 < 2단계 < 3단계로 입력해 주세요.");
            }
        }
    }
    private CodeFailure grouped(String code, String message) {
        return CodeFailure.invalid("value", code + ": " + message);
    }
    private Map<String, TreeMap<String, OperatingStandardRow>> index(List<OperatingStandardRow> rows) {
        var result = new LinkedHashMap<String, TreeMap<String, OperatingStandardRow>>();
        for (var row : rows) result.computeIfAbsent(row.standardCode, key -> new TreeMap<>()).put(row.month, row);
        return result;
    }
    private OperatingStandardRow current(Map<String, TreeMap<String, OperatingStandardRow>> state,
        String code, String month) {
        var history = state.get(code);
        var entry = history == null ? null : history.floorEntry(month);
        return entry == null ? null : entry.getValue();
    }
    private String baseMonth(Map<String, TreeMap<String, OperatingStandardRow>> state, String month) {
        var cash = current(state, "CASH_BASE_YM", month);
        if (cash == null) throw invalid("standardCode");
        return cashMonth(cash.value);
    }
    private boolean isClosed(String month, String base, Set<String> closed) {
        return month.compareTo(base) < 0 || closed.contains(month);
    }
    private void requireOpen(String month, String base, Set<String> closed) {
        if (isClosed(month, base, closed)) throw closedFailure();
    }
    private CodeFailure closedFailure() {
        return CodeFailure.invalid("effectiveMonth", "마감한 달에는 적용할 수 없습니다.");
    }
    private record Plan(String action, Mutation value, OperatingStandardRow before) {}
}
