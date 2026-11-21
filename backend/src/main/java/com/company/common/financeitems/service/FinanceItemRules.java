package com.company.common.financeitems.service;

import com.company.common.financeitems.dto.FinanceItemContracts.*;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class FinanceItemRules {
    private FinanceItemRules() {}
    public static YearMonth month(String value) {
        try {
            if (value == null || !value.matches("[0-9]{6}")) throw new IllegalArgumentException();
            var result = YearMonth.parse(value, DateTimeFormatter.ofPattern("uuuuMM"));
            if (result.getYear() < 1) throw new IllegalArgumentException();
            return result;
        } catch (RuntimeException failure) { throw CodeFailure.invalid("yearMonth", "연월을 확인해 주세요."); }
    }
    public static String db(YearMonth month) { return month.format(DateTimeFormatter.ofPattern("uuuuMM")); }
    public static int year(String value) {
        if (value == null || !value.matches("[0-9]{4}") || Integer.parseInt(value) < 1 || Integer.parseInt(value) > 9998)
            throw CodeFailure.invalid("taxYear", "과세연도를 확인해 주세요.");
        return Integer.parseInt(value);
    }
    public static PaymentKey paymentKey(String value) {
        if (value == null || !value.matches("[0-9]{4}:(MID|CNFM)"))
            throw CodeFailure.invalid("paymentId", "납부 항목을 확인해 주세요.");
        year(value.substring(0,4));
        return new PaymentKey(value.substring(0,4), value.substring(5));
    }
    public static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    public static void amount(BigDecimal value, String field) {
        if (value == null) throw CodeFailure.invalid(field, "금액을 입력해 주세요");
        if (value.stripTrailingZeros().scale() > 0 || value.abs().compareTo(new BigDecimal("999999999999999")) > 0)
            throw CodeFailure.invalid(field, "원 단위 정수 금액을 입력해 주세요.");
    }
    public static void text(String value, String field, int max, boolean required) {
        if ((required && (value == null || value.isBlank())) || (value != null && value.codePointCount(0,value.length()) > max))
            throw CodeFailure.invalid(field, "입력 내용을 확인해 주세요.");
    }
    public static Item copy(Item row, YearMonth month, boolean auto) {
        var date = row.cashDate() == null ? null : month.atDay(Math.min(row.cashDate().getDayOfMonth(),month.lengthOfMonth()));
        return new Item(null,null,row.classification(),row.itemCode(),row.itemName(),row.amount(),row.vatAmount(),date,
            row.repeatYn(),auto ? "Y" : "N",null,row.memo());
    }
    public static String itemKey(Item row) { return row.classification()+":"+row.itemName(); }
    public static Map<String,BigDecimal> allocation(int year, BigDecimal annual, Set<String> closed,
        java.util.function.Function<String,BigDecimal> fixed) {
        var result = new LinkedHashMap<String,BigDecimal>();
        var open = new ArrayList<String>();
        BigDecimal remainder = annual;
        for (int m=1;m<=12;m++) {
            String key = db(YearMonth.of(year,m));
            if (closed.contains(key)) {
                var value = zero(fixed.apply(key));
                result.put(key,value);
                remainder = remainder.subtract(value);
            } else open.add(key);
        }
        if (!open.isEmpty()) {
            var share = remainder.divide(BigDecimal.valueOf(open.size()),0,RoundingMode.DOWN);
            for (String key:open) result.put(key,share);
            result.put(open.get(open.size()-1),remainder.subtract(share.multiply(BigDecimal.valueOf(open.size()-1))));
        }
        return result;
    }
    public static void affected(int count) { if (count != 1) throw CodeFailure.conflict(); }
}
