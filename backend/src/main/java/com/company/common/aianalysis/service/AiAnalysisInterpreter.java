package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import java.time.YearMonth;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 규칙(낱말) 해석기: 질문에서 조회 함수 하나와 기간만 고른다. SQL은 만들지 않는다.
 * 외부 AI를 붙이면 같은 Query를 돌려주고 서버가 다시 검사한다.
 */
@Component
public class AiAnalysisInterpreter {
    /**
     * periodGiven: 질문에 기간 낱말이 있었는지. notice: 함수 없이 답할 수 없을 때 보일 글(없으면 기본 글).
     */
    public record Query(String question, String function, Period period, boolean salary, boolean cause,
        boolean periodGiven, String notice) {
        public Query(String question, String function, Period period, boolean salary, boolean cause) {
            this(question, function, period, salary, cause, false, null);
        }
    }
    public static final String NO_OPERATING_FORECAST = "영업이익 예측은 계산 식이 없어 답하지 않습니다.";
    private final BusinessCalendar calendar;
    private final AiAnalysisScreens screens;
    private static final Pattern MONTH = Pattern.compile("(?<![0-9])([0-9]{4})\\s*[년./-]\\s*(0?[1-9]|1[0-2])(?:월)?(?![0-9]|\\s*분기)");
    private static final Pattern QUARTER = Pattern.compile("(?:([0-9]{4})년?\\s*)?([1-4])\\s*분기");
    private static final Pattern HALF = Pattern.compile("(?:([0-9]{4})년?\\s*)?(상반기|하반기)");
    private static final Pattern AHEAD = Pattern.compile("(?:앞으로|향후|다음)\\s*([0-9]{1,2})\\s*(?:개월|달)");
    private static final Pattern MONTH_ONLY = Pattern.compile("(?<![0-9])(1[0-2]|[1-9])\\s*월");
    private static final Pattern YEAR_ONLY = Pattern.compile("(?<![0-9])([0-9]{4})\\s*년(?!\\s*[0-9])");
    private static final Pattern FORECAST = Pattern.compile(".*(예측|전망|앞으로|향후|될까|될 것|것 같|예상|다음 분기|다음분기|다음 달|다음달).*");
    public AiAnalysisInterpreter(BusinessCalendar calendar, AiAnalysisScreens screens) {
        this.calendar = calendar; this.screens = screens;
    }
    public Query interpret(Input input) {
        if (input == null) throw CodeFailure.invalid("question", "질문을 입력해 주세요.");
        String question = input.question() == null ? "" : input.question().strip();
        if (question.isBlank() || question.codePointCount(0, question.length()) > 1000)
            throw CodeFailure.invalid("question", "질문은 1~1000자로 입력해 주세요.");
        if (input.requestId() == null || !input.requestId().matches("[A-Za-z0-9_-]{1,128}"))
            throw CodeFailure.invalid("requestId", "요청 번호를 확인해 주세요.");
        if (input.screenId() == null || !input.screenId().matches("UI-[0-9]{2}") || !screens.registered(input.screenId()))
            throw CodeFailure.invalid("screenId", "화면 번호를 확인해 주세요.");
        if (input.period() != null) validate(input.period());
        YearMonth now = calendar.currentMonth();
        Period fallback = "UI-26".equals(input.screenId()) || input.period() == null
            ? new Period(now.toString(), now.toString()) : input.period();
        Period stated = period(question, now);
        Period period = stated == null ? fallback : stated;
        validate(period);
        boolean forecast = FORECAST.matcher(question).matches();
        boolean salary = has(question, "급여", "월급", "연봉", "지급단가", "지급 단가");
        boolean cause = has(question, "원인", "왜", "이유");
        String function = null, notice = null;
        if (salary) function = "F-04";
        else if (has(question, "철수")) function = "F-10";
        else if (has(question, "연체") || has(question, "미수") && has(question, "위험", "될", "예측")) function = "P-05";
        else if (has(question, "최종") && has(question, "마진", "이익률")) function = "P-02";
        else if (has(question, "미수", "청구", "누락")) function = "F-06";
        else if (has(question, "자금", "잔액", "현금", "이월금")) function = forecast ? "P-03" : "F-07";
        else if (has(question, "대기", "남는", "남을", "남아", "잉여", "부족", "과부족", "수요") && !has(question, "대기 원가")) {
            boolean shortage = has(question, "부족", "과부족", "수요", "잉여");
            function = forecast || shortage ? "P-04" : "F-09";
        }
        else if (has(question, "가동률", "가득률", "인력 현황", "인원 현황")) function = "F-09";
        else if (has(question, "달성", "사업계획", "계획 대비")) function = "F-05";
        else if (has(question, "파트") && has(question, "이익", "정산")) function = "F-02";
        else if (has(question, "적자 인력", "적자 인원", "적자인 사람", "개인 손익", "개인 원가", "개인별")
            || question.matches(".*이익률\\s*[0-9.]+\\s*%?\\s*미만.*")) function = "F-04";
        else if (has(question, "영업이익률", "순이익률", "매출총이익률")) function = forecast ? null : "F-01";
        else if (has(question, "적자 프로젝트", "이익률 추이", "마진율", "프로젝트 손익", "프로젝트 원가", "프로젝트 이익")
            || has(question, "프로젝트") && has(question, "손익", "원가", "이익", "마진")) function = "F-03";
        else if (has(question, "위험", "주의", "신호등", "현황판")
            || has(question, "프로젝트") && has(question, "상태", "현황")) function = "F-11";
        else if (has(question, "영업 기회", "영업기회", "파이프라인", "수주")) function = "F-12";
        else if (has(question, "매출", "영업이익", "순이익", "매출총이익", "이익률", "대기 원가", "수익 구조", "실적")) {
            if (!forecast) function = "F-01";
            else if (has(question, "매출")) function = "P-01";
            else notice = NO_OPERATING_FORECAST;
        }
        else if (has(question, "적자")) function = "F-04";
        return new Query(question, function, period, salary, cause, stated != null, notice);
    }
    /** 질문에 적힌 기간. 없으면 null. */
    Period period(String question, YearMonth now) {
        Matcher m = MONTH.matcher(question);
        YearMonth first = null, last = null;
        while (m.find()) {
            YearMonth found = YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            if (first == null) first = found;
            last = found;
        }
        if (first != null) return range(question, first.isAfter(last) ? last : first, first.isAfter(last) ? first : last);
        int year = has(question, "작년", "지난해", "전년도") ? now.getYear() - 1 : now.getYear();
        m = QUARTER.matcher(question);
        if (m.find()) {
            int y = m.group(1) == null ? year : Integer.parseInt(m.group(1));
            var start = YearMonth.of(y, (Integer.parseInt(m.group(2)) - 1) * 3 + 1);
            return range(question, start, start.plusMonths(2));
        }
        m = HALF.matcher(question);
        if (m.find()) {
            int y = m.group(1) == null ? year : Integer.parseInt(m.group(1));
            var start = YearMonth.of(y, "상반기".equals(m.group(2)) ? 1 : 7);
            return new Period(start.toString(), start.plusMonths(5).toString());
        }
        YearMonth quarter = YearMonth.of(now.getYear(), ((now.getMonthValue() - 1) / 3) * 3 + 1);
        if (has(question, "다음 분기", "다음분기")) return span(quarter.plusMonths(3), quarter.plusMonths(5));
        if (has(question, "이번 분기", "이번분기")) return span(quarter, quarter.plusMonths(2));
        if (has(question, "지난 분기", "지난분기")) return span(quarter.minusMonths(3), quarter.minusMonths(1));
        m = AHEAD.matcher(question);
        if (m.find()) {
            int months = Math.max(1, Math.min(12, Integer.parseInt(m.group(1))));
            return span(now.plusMonths(1), now.plusMonths(months));
        }
        if (has(question, "이번 달", "이번달", "지금", "현재", "오늘")) return range(question, now, now);
        if (has(question, "지난 달", "지난달", "전월")) return range(question, now.minusMonths(1), now.minusMonths(1));
        if (has(question, "다음 달", "다음달")) return span(now.plusMonths(1), now.plusMonths(1));
        m = MONTH_ONLY.matcher(question);
        if (m.find()) {
            var month = YearMonth.of(year, Integer.parseInt(m.group(1)));
            return range(question, month, month);
        }
        m = YEAR_ONLY.matcher(question);
        if (m.find()) year = Integer.parseInt(m.group(1));
        else if (!has(question, "작년", "지난해", "전년도", "올해", "금년", "누계")) return null;
        var january = YearMonth.of(year, 1);
        return span(january, year < now.getYear() ? YearMonth.of(year, 12) : year == now.getYear() ? now : YearMonth.of(year, 12));
    }
    /** 「누계」면 그 해 1월부터. */
    private static Period range(String question, YearMonth first, YearMonth last) {
        if (has(question, "누계", "누적")) first = YearMonth.of(last.getYear(), 1);
        return span(first, last);
    }
    private static Period span(YearMonth first, YearMonth last) { return new Period(first.toString(), last.toString()); }
    static boolean has(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
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
