package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import java.time.YearMonth;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisInterpreter {
    public record Query(String question, String function, Period period, boolean salary, boolean cause) {}
    private final BusinessCalendar calendar;
    private static final Pattern MONTH = Pattern.compile("(?<![0-9])([0-9]{4})[년./-]\\s*(0?[1-9]|1[0-2])(?:월)?(?![0-9]|\\s*분기)");
    public AiAnalysisInterpreter(BusinessCalendar calendar) { this.calendar = calendar; }
    public Query interpret(Input input) {
        if (input == null) throw CodeFailure.invalid("question", "질문을 입력해 주세요.");
        String question = input.question() == null ? "" : input.question().strip();
        if (question.isBlank() || question.codePointCount(0, question.length()) > 1000)
            throw CodeFailure.invalid("question", "질문은 1~1000자로 입력해 주세요.");
        if (input.requestId() == null || !input.requestId().matches("[A-Za-z0-9_-]{1,128}"))
            throw CodeFailure.invalid("requestId", "요청 번호를 확인해 주세요.");
        if (input.screenId() == null || !input.screenId().matches("UI-[0-9]{2}"))
            throw CodeFailure.invalid("screenId", "화면 번호를 확인해 주세요.");
        if (input.period() != null) validate(input.period());
        YearMonth now = calendar.currentMonth();
        Period period = "UI-26".equals(input.screenId()) || input.period() == null
            ? new Period(now.toString(), now.toString()) : input.period();
        var matches = MONTH.matcher(question);
        YearMonth first = null, last = null;
        while (matches.find()) {
            YearMonth found = YearMonth.of(Integer.parseInt(matches.group(1)), Integer.parseInt(matches.group(2)));
            if (first == null) first = found;
            last = found;
        }
        var quarter = Pattern.compile("([0-9]{4})년?\\s*([1-4])분기").matcher(question);
        if (first != null) period = new Period(first.toString(), last.toString());
        else if (quarter.find()) {
            first = YearMonth.of(Integer.parseInt(quarter.group(1)), (Integer.parseInt(quarter.group(2)) - 1) * 3 + 1);
            period = new Period(first.toString(), first.plusMonths(2).toString());
        }
        else if (question.contains("다음 분기")) {
            first = YearMonth.of(now.getYear(), ((now.getMonthValue() - 1) / 3) * 3 + 1).plusMonths(3);
            period = new Period(first.toString(), first.plusMonths(2).toString());
        } else if (question.contains("이번 분기")) {
            first = YearMonth.of(now.getYear(), ((now.getMonthValue() - 1) / 3) * 3 + 1);
            period = new Period(first.toString(), first.plusMonths(2).toString());
        } else if (question.contains("이번 달") || question.contains("이번달")) {
            period = new Period(now.toString(), now.toString());
        } else if (question.contains("지난 달") || question.contains("지난달")) {
            period = new Period(now.minusMonths(1).toString(), now.minusMonths(1).toString());
        } else if (question.contains("다음 달") || question.contains("다음달")) {
            period = new Period(now.plusMonths(1).toString(), now.plusMonths(1).toString());
        } else {
            var monthOnly = Pattern.compile("(?<![0-9])([1-9]|1[0-2])월").matcher(question);
            if (monthOnly.find()) {
                first = YearMonth.of(now.getYear(), Integer.parseInt(monthOnly.group(1)));
                period = new Period(first.toString(), first.toString());
            }
        }
        validate(period);
        if (question.contains("누계")) period = new Period(period.endMonth().substring(0, 4) + "-01", period.endMonth());
        boolean future = question.contains("예측") || question.contains("전망") || question.contains("다음")
            || YearMonth.parse(period.endMonth()).isAfter(now);
        boolean salary = question.contains("급여") || question.contains("월급") || question.contains("지급단가");
        String function = null;
        if (future && question.contains("영업이익")) function = null;
        else if (question.contains("최종") && (question.contains("마진") || question.contains("이익률"))) function = "P-02";
        else if (future && (question.contains("대기") || question.contains("인력") || question.contains("수요"))) function = "P-04";
        else if (future && question.contains("매출")) function = "P-01";
        else if (question.contains("미수") && (question.contains("위험") || question.contains("예측"))) function = "P-05";
        else if (question.contains("자금")) function = future ? "P-03" : "F-07";
        else if (question.contains("달성") || question.contains("사업계획")) function = "F-05";
        else if (question.contains("파트") && (question.contains("이익") || question.contains("정산"))) function = "F-02";
        else if (question.contains("프로젝트") && (question.contains("손익") || question.contains("원가"))) function = "F-03";
        else if (salary || question.contains("적자 인력") || question.contains("개인 손익") || question.matches(".*이익률.*미만.*")) function = "F-04";
        else if (question.contains("청구") || question.contains("미수")) function = "F-06";
        else if (question.contains("철수")) function = "F-10";
        else if (question.contains("위험") || question.contains("주의")) function = "F-11";
        else if (question.contains("영업 기회") || question.contains("파이프라인")) function = "F-12";
        else if (question.contains("인력") || question.contains("가동률")) function = "F-09";
        else if (question.contains("매출") || question.contains("영업이익") || question.contains("순이익") || question.contains("수익 구조")) function = "F-01";
        return new Query(question, function, period, salary, question.contains("원인") || question.contains("왜"));
    }
    public static void validate(Period period) {
        try {
            if (period.startMonth() == null || period.endMonth() == null
                || !period.startMonth().matches("[0-9]{4}-[0-9]{2}") || !period.endMonth().matches("[0-9]{4}-[0-9]{2}"))
                throw new IllegalArgumentException();
            var start = YearMonth.parse(period.startMonth());
            var end = YearMonth.parse(period.endMonth());
            if (start.getYear() < 1 || end.getYear() > 9999 || start.isAfter(end)) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw CodeFailure.invalid("period", "올바른 시작월과 종료월을 입력해 주세요."); }
    }
}
