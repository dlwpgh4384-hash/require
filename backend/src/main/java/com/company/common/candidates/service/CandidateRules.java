package com.company.common.candidates.service;

import com.company.common.candidates.dto.CandidateContracts.*;
import com.company.common.clients.service.ClientRules;
import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public final class CandidateRules {
    private CandidateRules() {}
    private static final Set<String> FIELDS = Set.of("name", "desiredMonthlyRate", "employeeTypeCode",
        "companyId", "phone", "email", "privacyAgreement", "primaryJobCode", "secondaryJobCode",
        "skills", "gradeCode", "availableDate", "homeAreaCode", "homeAddress", "sourceCode", "memo");
    public static CodeFailure invalid(String field) {
        return CodeFailure.invalid(field, "입력 값을 확인해 주세요.");
    }
    public static String text(JsonNode body, String field, int limit, boolean required) {
        var node = body.get(field);
        if (node == null || node.isNull()) {
            if (required) throw invalid(field);
            return null;
        }
        if (!node.isTextual()) throw invalid(field);
        String value = node.textValue().trim();
        if (value.codePointCount(0, value.length()) > limit || required && value.isEmpty()) throw invalid(field);
        return value.isEmpty() ? null : value;
    }
    public static Input input(JsonNode body, boolean update) {
        var allowed = new HashSet<>(FIELDS);
        if (update) allowed.add("modifiedAt");
        ClientRules.object(body, allowed);
        if (update) ClientRules.token(body);
        String name = text(body, "name", 50, true);
        // 민감 체크가 없는 사용자의 수정: 희망 단가는 응답에서 빠지므로 보내온 값(빈 값 포함)은 보지 않고
        // 저장된 값을 그대로 둔다(명세 ⑨-2). 옛 값은 서비스에서 채운다.
        boolean keepRate = update && !com.company.common.support.auth.RequestAccess.sensitiveVisible();
        java.math.BigDecimal rate = keepRate ? null : rate(body.get("desiredMonthlyRate"));
        String type = text(body, "employeeTypeCode", 10, true);
        if (!Set.of("FL", "OS").contains(type)) throw invalid("employeeTypeCode");
        String company = text(body, "companyId", 20, "OS".equals(type));
        String phone = text(body, "phone", 20, false), email = text(body, "email", 100, false);
        if (phone != null && !phone.matches("[0-9-]+")) throw invalid("phone");
        if (email != null && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw invalid("email");
        var privacy = body.get("privacyAgreement");
        if (privacy != null && !privacy.isBoolean()) throw invalid("privacyAgreement");
        boolean agreed = privacy != null && privacy.booleanValue();
        if ((phone != null || email != null) && !agreed) throw invalid("privacyAgreement");
        String job = text(body, "primaryJobCode", 10, true);
        String secondary = text(body, "secondaryJobCode", 10, false);
        var skillsNode = body.get("skills");
        if (skillsNode == null || !skillsNode.isArray() || skillsNode.isEmpty()) throw invalid("skills");
        var skills = new LinkedHashSet<String>();
        for (var skill : skillsNode) {
            if (!skill.isTextual() || skill.textValue().isBlank()
                || skill.textValue().trim().codePointCount(0, skill.textValue().trim().length()) > 50) throw invalid("skills");
            skills.add(skill.textValue().trim());
        }
        if (skills.size() > 999) throw invalid("skills");
        LocalDate available;
        try { available = LocalDate.parse(text(body, "availableDate", 10, true)); }
        catch (java.time.DateTimeException failure) { throw invalid("availableDate"); }
        return new Input(name, rate, type, "OS".equals(type) ? company : null, phone, email, agreed,
            job, job.equals(secondary) ? null : secondary, java.util.List.copyOf(skills),
            text(body, "gradeCode", 10, true), available, text(body, "homeAreaCode", 10, false),
            text(body, "homeAddress", 50, false), text(body, "sourceCode", 10, true), text(body, "memo", 500, false));
    }
    private static java.math.BigDecimal rate(JsonNode amount) {
        if (amount == null || !amount.isNumber()) throw invalid("desiredMonthlyRate");
        try {
            var rate = amount.decimalValue();
            if (rate.signum() < 0 || rate.stripTrailingZeros().scale() > 0
                || rate.compareTo(new java.math.BigDecimal("999999999999999")) > 0) throw invalid("desiredMonthlyRate");
            return rate;
        } catch (NumberFormatException failure) { throw invalid("desiredMonthlyRate"); }
    }
    public static ContactInput contact(JsonNode body) {
        ClientRules.object(body, Set.of("modifiedAt", "contactStatusCode", "memo"));
        var token = ClientRules.token(body);
        String status = text(body, "contactStatusCode", 10, true);
        if (!Set.of("BEFORE", "DONE", "ITVW", "HOLD", "CTRT").contains(status)) throw invalid("contactStatusCode");
        return new ContactInput(token, status, text(body, "memo", 200, true));
    }
    public static String optional(String value, String field) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > 10) throw invalid(field);
        return value.trim();
    }
}
