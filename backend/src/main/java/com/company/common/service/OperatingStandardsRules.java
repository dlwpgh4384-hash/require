package com.company.common.service;

import com.company.common.dto.OperatingStandardsContracts.Change;
import com.company.common.dto.OperatingStandardsContracts.Input;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class OperatingStandardsRules {
    private OperatingStandardsRules() {}
    public static final List<String> AI = List.of("AI_SCORE_JOB", "AI_SCORE_SKILL", "AI_SCORE_CUST",
        "AI_SCORE_GRADE", "AI_SCORE_PERF", "AI_SCORE_COST", "AI_SCORE_AVAIL", "AI_SCORE_LOC");
    public static final List<String> DUN = List.of("DUN_D1", "DUN_D2", "DUN_D3");
    private static final Set<String> EDITABLE = Set.of("PART_DSTRB_RT", "PART_SALES_EXPS", "VAT_RT",
        "TGT_UTIL_RT", "TGT_MRGN_RT", "LOW_MRGN_GAP", "DUN_D1", "DUN_D2", "DUN_D3",
        "WTHDR_NOTI_D", "EXT_PRBL_H", "EXT_PRBL_M", "EXT_PRBL_L", "EXT_PRBL_NULL",
        "STEP_PRBL_1", "STEP_PRBL_2", "STEP_PRBL_3", "STEP_PRBL_4", "MIN_CASH",
        "CASH_BASE_YM", "CASH_BASE_AMT", "BILL_MISS_RT", "BILL_MISS_MIN",
        "AI_SCORE_JOB", "AI_SCORE_SKILL", "AI_SCORE_CUST", "AI_SCORE_GRADE", "AI_SCORE_PERF",
        "AI_SCORE_COST", "AI_SCORE_AVAIL", "AI_SCORE_LOC",
        // 시안(최종) 운영 기준값 표에 있는 나머지 12개(2026-11-14): DML 43개 전부를 관리한다.
        "WTHLD_RT", "QUOTE_APRV_RT", "QUOTE_REAPRV_GAP", "BDGT_WARN_RT", "SHORT_PERSON",
        "SURPLUS_PERSON", "HIRE_LEAD_MON", "COST_OVER_UPRC_RT", "MM_OVER", "LOCK_FAIL_CNT",
        "SESSION_MIN", "PWD_CHG_DAY");
    private static final DateTimeFormatter PHYSICAL = DateTimeFormatter.ofPattern("uuuuMM");
    public static boolean editable(String code) { return EDITABLE.contains(code); }
    public static boolean immediate(String code) {
        return "CASH_BASE_YM".equals(code) || "CASH_BASE_AMT".equals(code);
    }
    public static String physicalMonth(String value) {
        if (value == null || !value.matches("[0-9]{4}-(0[1-9]|1[0-2])")) throw invalid("effectiveMonth");
        try {
            var month = YearMonth.parse(value);
            if (month.getYear() < 1) throw invalid("effectiveMonth");
            return month.format(PHYSICAL);
        } catch (java.time.DateTimeException failure) { throw invalid("effectiveMonth"); }
    }
    public static String publicMonth(String value) {
        return YearMonth.parse(value, PHYSICAL).toString();
    }
    public static String cashMonth(BigDecimal value) {
        try {
            String month = value.toBigIntegerExact().toString();
            physicalMonth(publicMonth(month));
            return month;
        } catch (RuntimeException failure) { throw invalid("value"); }
    }
    public static BigDecimal number(Change change) {
        if (change.value() == null) throw invalid("value");
        String text = change.value();
        if ("CASH_BASE_YM".equals(change.standardCode()) && text.matches("[0-9]{4}-[0-9]{2}"))
            text = physicalMonth(text);
        if (!text.matches("-?[0-9]+(?:\\.[0-9]+)?") || text.length() > 40) throw invalid("value");
        BigDecimal value = new BigDecimal(text).stripTrailingZeros();
        if (value.scale() > 4 || value.precision() - value.scale() > 11) throw invalid("value");
        if ("CASH_BASE_YM".equals(change.standardCode())) cashMonth(value);
        if (change.standardCode().startsWith("EXT_PRBL_") || change.standardCode().startsWith("STEP_PRBL_")) {
            if (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0)
                throw CodeFailure.invalid("value", change.standardCode() + ": 확률은 0~100으로 입력해 주세요.");
        }
        return value;
    }
    public static Input parse(JsonNode body) {
        object(body, Set.of("items"));
        var items = body.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) throw invalid("items");
        var result = new ArrayList<Change>();
        var identities = new HashSet<String>();
        for (var node : items) {
            object(node, Set.of("standardCode", "value", "effectiveMonth", "action", "modifiedAt"));
            String code = text(node, "standardCode");
            if (!editable(code)) throw invalid("standardCode");
            String action = text(node, "action");
            if (!Set.of("add", "update", "delete").contains(action)) throw invalid("action");
            if (!node.has("effectiveMonth")) throw invalid("effectiveMonth");
            String month = node.get("effectiveMonth").isNull() ? null : text(node, "effectiveMonth");
            if (immediate(code)) {
                if (month != null) throw invalid("effectiveMonth");
                if (!"update".equals(action)) throw invalid("action");
            } else physicalMonth(month);
            if (!identities.add(code + ":" + month)) throw invalid("standardCode");
            if (node.has("value") && !node.get("value").isTextual()) throw invalid("value");
            String value = "delete".equals(action) ? null : text(node, "value");
            String modifiedAt = node.has("modifiedAt") && !node.get("modifiedAt").isNull()
                ? text(node, "modifiedAt") : null;
            var change = new Change(code, value, month, action, modifiedAt);
            if (!"delete".equals(action)) number(change);
            result.add(change);
        }
        return new Input(List.copyOf(result));
    }
    private static void object(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) throw invalid("body");
        node.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) throw invalid(field); });
    }
    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw invalid(field);
        return value.asText();
    }
    public static CodeFailure invalid(String field) {
        return CodeFailure.invalid(field, "운영 기준값의 " + field + " 입력을 확인해 주세요.");
    }
}
