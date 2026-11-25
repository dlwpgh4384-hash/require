package com.company.common.commonops.service;

import com.company.common.commonops.mapper.BusinessPlanComparisonMapper;
import com.company.common.commonops.mapper.BusinessPlanComparisonRows.Target;
import com.company.common.commonops.mapper.BusinessPlanComparisonRows.TargetWrite;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.uploads.dto.UploadContracts.*;
import com.company.common.uploads.dto.UploadWorkbookContracts.TabularRow;
import com.company.common.uploads.service.UploadForm;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Component;

/** SFR-118-04 목표 업로드(B.10): 한 해 1~12월 매출·영업이익 목표, PART_ID='ALL'만. 실적(TB_CO_MON_PL)은 건드리지 않는다. */
@Component
public class BusinessPlanUploadForm implements UploadForm {
    public static final List<String> HEADERS = List.of("연월", "매출 목표", "영업이익 목표");
    /** 결과 창 「목표가 빈 달」을 화면이 고르는 경고 이유. */
    public static final String EMPTY_TARGET = "목표가 빈 달 — 오류 없이 0(목표 없음)으로 올립니다.";
    /** 확인창 「마감한 달 목표가 바뀝니다」를 화면이 고르는 경고 이유. */
    public static final String CLOSED_CHANGED = "마감한 달 목표가 지금 목표와 다릅니다.";
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("uuuuMM");
    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("uuuu-MM");
    private static final BigDecimal LIMIT = new BigDecimal("999999999999999");
    private final BusinessPlanComparisonMapper mapper;
    private final UserPreferencePeriodSourceMapper periods;
    private final CurrentBusinessActor actor;

    public BusinessPlanUploadForm(BusinessPlanComparisonMapper mapper, UserPreferencePeriodSourceMapper periods,
        CurrentBusinessActor actor) {
        this.mapper = mapper;
        this.periods = periods;
        this.actor = actor;
    }
    /** 반영할 한 달 목표. closedChanged: 마감한 달인데 지금 목표와 다른 줄. */
    public record PlanMonth(int row, YearMonth month, BigDecimal revenueTarget, BigDecimal operatingProfitTarget,
        boolean closed, boolean closedChanged) {}

    public String formTypeCode() { return "B10"; }
    public String targetName() { return "사업계획 목표"; }
    public String screenId() { return "UI-18"; }
    public List<String> headers() { return HEADERS; }
    public String templateFileName() { return "business-plan-template.xlsx"; }
    public String templateNote() {
        return "연월(YYYY-MM)·매출 목표·영업이익 목표, 그해 1~12월 12줄입니다. 금액은 원 단위 정수로 적습니다. "
            + "빈칸은 오류 없이 0(목표 없음)으로 올라가고 그 달 달성률은 「–」로 보입니다. 실적은 바뀌지 않습니다.";
    }
    public List<List<String>> templateRows(Integer year) {
        if (year == null) return List.of();
        int first = startYear();
        if (year < first || year > 9999)
            throw CodeFailure.invalid("year", first + "년 이후의 연도를 골라 주세요.");
        var rows = new ArrayList<List<String>>();
        for (int m = 1; m <= 12; m++) rows.add(List.of(YearMonth.of(year, m).format(SHOWN), "", ""));
        return rows;
    }
    public void lock() { mapper.lockTargets(); }

    private int startYear() {
        var value = periods.selectCashBaseYm();
        try { return YearMonth.parse(value.toBigIntegerExact().toString(), YM).getYear(); }
        catch (RuntimeException failure) { throw CodeFailure.invalid("file", "운영 시작월을 확인해 주세요."); }
    }
    private static YearMonth parseMonth(String value) {
        String compact = value.trim().replace("-", "");
        if (compact.isEmpty()) throw new IllegalArgumentException("연월을 입력해 주세요.");
        if (!compact.matches("[0-9]{4}(0[1-9]|1[0-2])"))
            throw new IllegalArgumentException("연월 형식이 맞지 않습니다. YYYY-MM으로 적어 주세요.");
        return YearMonth.parse(compact, YM);
    }
    /** 빈칸은 null(→ 0, 목표 없음). */
    private static BigDecimal amount(String value, boolean signed) {
        String text = value.trim().replace(",", "");
        if (text.isEmpty()) return null;
        if (!text.matches(signed ? "[+-]?[0-9]+" : "\\+?[0-9]+"))
            throw new IllegalArgumentException(signed ? "금액은 원 단위 정수로 입력해 주세요."
                : "매출 목표는 0 이상의 원 단위 정수로 입력해 주세요.");
        var amount = new BigDecimal(text);
        if (amount.abs().compareTo(LIMIT) > 0) throw new IllegalArgumentException("금액은 15자리 이내로 입력해 주세요.");
        return amount;
    }
    private static String clip(String value) {
        String shown = value.isBlank() ? "(빈칸)" : value;
        return shown.substring(0, Math.min(shown.length(), 200));
    }

    public Inspection inspect(List<TabularRow> rows, YearMonth ignored) {
        int first = startYear();
        var counts = new HashMap<YearMonth, Integer>();
        var years = new TreeSet<Integer>();
        for (var row : rows) {
            try {
                var month = parseMonth(row.values().get(0));
                counts.merge(month, 1, Integer::sum);
                years.add(month.getYear());
            } catch (RuntimeException invalid) { /* 줄 검사에서 오류로 남긴다. */ }
        }
        // 한 해 파일: 가장 많은 연도를 그 파일의 연도로 보고, 다른 연도 줄은 오류로 남긴다.
        Integer year = years.isEmpty() ? null : years.stream()
            .max(Comparator.comparingLong(y -> counts.entrySet().stream().filter(e -> e.getKey().getYear() == y)
                .mapToLong(Map.Entry::getValue).sum())).orElseThrow();
        Set<String> closed = new HashSet<>(periods.selectClosedMonths());
        var existing = new HashMap<String, Target>();
        if (year != null) for (var target : mapper.targets(year)) existing.put(target.yearMonth(), target);
        boolean registered = !existing.isEmpty();
        var records = new ArrayList<PlanMonth>();
        var issues = new ArrayList<Issue>();
        int warned = 0;
        for (var row : rows) {
            int column = 0;
            try {
                var values = row.values();
                var month = parseMonth(values.get(0));
                if (counts.get(month) > 1) throw new IllegalArgumentException("같은 파일의 연월이 중복되었습니다.");
                if (month.getYear() < first) throw new IllegalArgumentException(first + "년부터 올릴 수 있습니다.");
                if (month.getYear() != year) throw new IllegalArgumentException("한 파일에는 한 해(" + year + "년) 목표만 올려 주세요.");
                column = 1;
                BigDecimal revenue = amount(values.get(1), false);
                column = 2;
                BigDecimal profit = amount(values.get(2), true);
                boolean empty = revenue == null || profit == null;
                revenue = revenue == null ? BigDecimal.ZERO : revenue;
                profit = profit == null ? BigDecimal.ZERO : profit;
                String ym = month.format(YM);
                boolean isClosed = closed.contains(ym);
                var before = existing.get(ym);
                boolean changed = isClosed && registered && (before == null
                    ? revenue.signum() != 0 || profit.signum() != 0
                    : before.revenueTarget().compareTo(revenue) != 0
                        || before.operatingProfitTarget().compareTo(profit) != 0);
                records.add(new PlanMonth(row.rowNumber(), month, revenue, profit, isClosed, changed));
                String shown = month.format(SHOWN);
                if (empty) issues.add(new Issue(row.rowNumber(), "연월", shown, EMPTY_TARGET, "경고"));
                if (changed) issues.add(new Issue(row.rowNumber(), "연월", shown, CLOSED_CHANGED, "경고"));
                if (empty || changed) warned++;
            } catch (RuntimeException failure) {
                int c = Math.min(column, 2);
                issues.add(new Issue(row.rowNumber(), HEADERS.get(c), clip(row.values().get(c)),
                    failure instanceof IllegalArgumentException ? failure.getMessage() : "입력값을 확인해 주세요.", "오류"));
            }
        }
        int errors = rows.size() - records.size();
        // 파일에 줄 자체가 없는 달도 빈칸과 같이 0(목표 없음)으로 올리고 「목표가 빈 달」로 알린다(SFR-118-04·Q-18-3,
        // 예전 목표가 남아 새 계획과 섞이지 않게, 2026-11-24). 오류 줄이 있으면 그 달은 오류로 남긴다.
        if (year != null && errors == 0) {
            for (int m = 1; m <= 12; m++) {
                var month = YearMonth.of(year, m);
                if (counts.containsKey(month)) continue;
                String ym = month.format(YM);
                boolean isClosed = closed.contains(ym);
                var before = existing.get(ym);
                boolean changed = isClosed && registered && before != null
                    && (before.revenueTarget().signum() != 0 || before.operatingProfitTarget().signum() != 0);
                records.add(new PlanMonth(0, month, BigDecimal.ZERO, BigDecimal.ZERO, isClosed, changed));
                String shown = month.format(SHOWN);
                issues.add(new Issue(0, "연월", shown, EMPTY_TARGET, "경고"));
                if (changed) issues.add(new Issue(0, "연월", shown, CLOSED_CHANGED, "경고"));
                warned++;
            }
        }
        return new Inspection(records, issues, records.size() - warned, warned, errors, rows.size());
    }

    public Map<Integer, String> apply(Inspection inspection, YearMonth month, long uploadId) {
        return apply(inspection, month, uploadId, null);
    }
    /** closedMonthPolicy: 마감한 달 목표가 바뀐 파일만 필요. keep = 마감 안 된 달만, replace = 1~12월 새 파일대로. */
    public Map<Integer, String> apply(Inspection inspection, YearMonth ignored, long uploadId, String closedMonthPolicy) {
        if (inspection.errorCount() != 0) throw CodeFailure.invalid("file", "오류가 있는 파일은 반영할 수 없습니다.");
        var records = inspection.records().stream().map(PlanMonth.class::cast).toList();
        boolean closedChanged = records.stream().anyMatch(PlanMonth::closedChanged);
        if (closedChanged && closedMonthPolicy == null)
            throw CodeFailure.invalid("closedMonthPolicy", "마감한 달 목표를 바꿀지 골라 주세요.");
        boolean keepClosed = KEEP_CLOSED.equals(closedMonthPolicy);
        String user = actor.userId();
        for (var record : records) {
            if (keepClosed && record.closed()) continue;
            if (mapper.upsertTarget(new TargetWrite(record.month().format(YM), record.revenueTarget(),
                record.operatingProfitTarget(), uploadId, user)) != 1)
                throw new IllegalStateException("목표를 저장하지 못했습니다.");
        }
        return Map.of();
    }
}
