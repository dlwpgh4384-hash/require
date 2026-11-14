package com.company.common.developers.service;

import com.company.common.clients.service.ClientRules;
import com.company.common.developers.dto.DeveloperContracts.*;
import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DeveloperRules {
    private DeveloperRules() {}
    private static final Set<String> FIELDS = Set.of("managementNumber", "name", "employeeTypeCode",
        "organizationId", "gradeCode", "primaryJobCode", "secondaryJobCode", "positionCode",
        "directClassCode", "salesPartId", "hireDate", "monthlyRate", "monthlyPay", "skillCodes",
        "homeAreaCode", "homeAddress");
    public static CodeFailure invalid(String field) {
        return CodeFailure.invalid(field, "입력 값을 확인해 주세요.");
    }
    public static String text(JsonNode body, String field, int max, boolean required) {
        var node = body.get(field);
        if (node == null || node.isNull()) {
            if (required) throw invalid(field);
            return null;
        }
        if (!node.isTextual()) throw invalid(field);
        String value = node.textValue().trim();
        if (value.codePointCount(0, value.length()) > max || (required && value.isEmpty())) throw invalid(field);
        return value.isEmpty() ? null : value;
    }
    public static LocalDate date(JsonNode body, String field, boolean required) {
        String value = text(body, field, 10, required);
        if (value == null) return null;
        try { return LocalDate.parse(value); }
        catch (RuntimeException failure) { throw invalid(field); }
    }
    private static BigDecimal amount(JsonNode body, String field) {
        var node = body.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isNumber()) throw invalid(field);
        BigDecimal value = node.decimalValue();
        if (value.signum() < 0 || value.stripTrailingZeros().scale() > 0 || value.precision() > 15)
            throw invalid(field);
        return value;
    }
    public static Input input(JsonNode body, boolean editing) {
        var allowed = new HashSet<>(FIELDS);
        if (editing) allowed.remove("managementNumber");
        ClientRules.object(body, allowed);
        String number = editing ? null : text(body, "managementNumber", 20, false);
        if (number != null && !number.matches("[A-Za-z0-9-]{1,20}")) throw invalid("managementNumber");
        if (!body.has("organizationId") || !body.get("organizationId").isTextual()) throw invalid("organizationId");
        String organization = body.get("organizationId").textValue().trim();
        if (organization.length() > 20) throw invalid("organizationId");
        return new Input(number, text(body,"name",50,true), text(body,"employeeTypeCode",10,true),
            organization, text(body,"gradeCode",10,false), text(body,"primaryJobCode",10,true),
            text(body,"secondaryJobCode",10,false), text(body,"positionCode",10,false),
            text(body,"directClassCode",10,true), text(body,"salesPartId",20,true), date(body,"hireDate",false),
            amount(body,"monthlyRate"), amount(body,"monthlyPay"), strings(body,"skillCodes",false),
            text(body,"homeAreaCode",10,false), text(body,"homeAddress",50,false));
    }
    private static List<String> strings(JsonNode body, String field, boolean required) {
        var node = body.get(field);
        if (node == null && !required) return java.util.Collections.emptyList();
        if (node == null || !node.isArray() || (required && node.isEmpty())) throw invalid(field);
        var result = new ArrayList<String>();
        for (var item : node) {
            if (!item.isTextual() || item.textValue().isBlank() || item.textValue().length() > 20
                || result.contains(item.textValue())) throw invalid(field);
            result.add(item.textValue());
        }
        return List.copyOf(result);
    }
    public static StatusInput status(JsonNode body) {
        ClientRules.object(body, Set.of("statusName","date","endDate","reason"));
        String name = text(body,"statusName",10,true);
        if (!Set.of("휴직","퇴사","재직").contains(name)) throw invalid("statusName");
        LocalDate end = date(body,"endDate",false);
        if (!"휴직".equals(name) && end != null) throw invalid("endDate");
        return new StatusInput(name, date(body,"date",true), end, text(body,"reason",300,false));
    }
    public static UseInput use(JsonNode body) {
        ClientRules.object(body, Set.of("developerIds","useYn"));
        String use = text(body,"useYn",1,true);
        if (!Set.of("Y","N").contains(use)) throw invalid("useYn");
        return new UseInput(strings(body,"developerIds",true),use);
    }
    public static Filter filter(Filter f) {
        if (f.workStatusName() != null && !f.workStatusName().isBlank()
            && !Set.of("투입중","철수예정","대기","휴직","퇴사").contains(f.workStatusName()))
            throw invalid("workStatusName");
        return new Filter(blank(f.employeeTypeCode()),blank(f.jobCode()),blank(f.workStatusName()),
            blank(f.directClassCode()),blank(f.q()),f.includeStopped());
    }
    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
