package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.dashboard.service.DashboardActivityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 답 모양 만들기(표·결론 글·단추)를 한곳에 모은다. 숫자는 받은 값을 그대로 옮긴다. */
@Component
public class AiAnalysisTables {
    private final ObjectMapper json;
    private final AiAnalysisScreens screens;
    public AiAnalysisTables(ObjectMapper json, AiAnalysisScreens screens) { this.json = json; this.screens = screens; }

    public <T> T filter(Map<String, ?> values, Class<T> type) { return json.convertValue(values, type); }
    public Action open(String screenId, Period period) { return screens.open(screenId, period); }
    public String screen(String screenId) { return screens.name(screenId); }

    /** 줄(Map·record)을 칸 key로 읽어 표로 만든다. */
    public Table table(String title, List<Column> columns, List<?> source) {
        var rows = new ArrayList<List<Object>>();
        for (Object item : source) {
            Map<?, ?> values = item instanceof Map<?, ?> map ? map : json.convertValue(item, Map.class);
            var row = new ArrayList<Object>();
            for (var column : columns) row.add(cell(values.get(column.key())));
            rows.add(Collections.unmodifiableList(row));
        }
        return new Table(title, List.copyOf(columns), List.copyOf(rows), null, null);
    }
    public static Table table(String title, List<Column> columns, List<List<Object>> rows,
        List<List<String>> tones, List<Object> totalRow) {
        return new Table(title, List.copyOf(columns), rows.stream().map(AiAnalysisTables::cells).toList(),
            tones == null ? null : tones.stream().map(r -> Collections.unmodifiableList(new ArrayList<>(r))).toList(),
            totalRow == null ? null : cells(totalRow));
    }
    private static List<Object> cells(List<?> values) {
        var row = new ArrayList<Object>();
        for (Object value : values) row.add(cell(value));
        return Collections.unmodifiableList(row);
    }
    /** 숫자는 BigDecimal, 날짜·달은 글자, 그 밖은 글자. */
    public static Object cell(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal number) return number;
        if (value instanceof Integer || value instanceof Long || value instanceof Short) return BigDecimal.valueOf(((Number) value).longValue());
        if (value instanceof Number number) return new BigDecimal(number.toString());
        if (value instanceof TemporalAccessor time) return time.toString();
        if (value instanceof Boolean flag) return flag ? "예" : "아니요";
        return value.toString();
    }

    // ---- 결론 글 ----
    public static String bold(String value) { return "**" + value + "**"; }
    public static String money(BigDecimal value) { return DashboardActivityService.kor(value); }
    public static String rate(BigDecimal value) { return DashboardActivityService.pct(value); }
    public static String people(long count) { return count + "명"; }
    public static String count(long count) { return count + "건"; }
    public static String mm(BigDecimal value) { return value == null ? "–" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(); }

    // ---- 기간 ----
    public static YearMonth month(String value) {
        return value.length() == 6 ? YearMonth.of(Integer.parseInt(value.substring(0, 4)), Integer.parseInt(value.substring(4)))
            : YearMonth.parse(value);
    }
    public static String key(YearMonth month) { return String.format("%04d%02d", month.getYear(), month.getMonthValue()); }
    public static Period single(YearMonth month) { return new Period(month.toString(), month.toString()); }
    public static Period span(YearMonth first, YearMonth last) { return new Period(first.toString(), last.toString()); }
    public static String period(Period period) {
        return period.startMonth().equals(period.endMonth()) ? period.startMonth()
            : period.startMonth() + " ~ " + period.endMonth();
    }
    /** 사용한 데이터 글: 「개인 원가·수익표 · 2026-10」. 기간이 없는 조회는 「오늘 기준」. */
    public static String used(String source, Period period) { return source + " · " + (period == null ? "오늘 기준" : period(period)); }

    // ---- 이름 고르기(프로젝트·파트) ----
    private static final Set<String> COMMON = Set.of("프로젝트", "시스템", "구축", "사업", "이번", "지난", "다음", "올해", "작년",
        "이익률", "마진율", "추이", "손익", "원가", "매출", "적자", "위험", "주의", "청구", "미수", "미수금", "최종", "예측", "얼마",
        "지금", "현재", "의심", "누락", "금액", "진행", "인력", "인원", "개발자", "이익금", "영업", "기회", "단계", "상태");
    private static final Pattern PARTICLE = Pattern.compile("(은|는|이|가|의|을|를|에|와|과|도)$");
    /**
     * 질문에 나온 이름 하나를 고른다. 이름 전체가 나오면 그것(긴 이름 우선), 아니면 낱말이 들어 있는 이름.
     * 여러 개가 맞으면 「어느 프로젝트인지 골라 주세요.」와 후보(최대 5개)로 답할 수 없음.
     */
    public static <T> T pick(String question, List<T> options, Function<T, String> name, Function<T, String> id, String what) {
        var matched = options.stream().filter(o -> contains(question, id.apply(o)) || contains(question, name.apply(o))).toList();
        var exact = matched.stream().filter(o -> matched.stream().noneMatch(other -> other != o && name.apply(other) != null
            && name.apply(o) != null && name.apply(other).length() > name.apply(o).length() && name.apply(other).contains(name.apply(o)))).toList();
        var distinctExact = distinct(exact, id);
        if (distinctExact.size() == 1) return distinctExact.get(0);
        if (distinctExact.size() > 1) throw ambiguous(question, distinctExact, name, null, what);
        String found = null;
        var partial = new ArrayList<T>();
        for (String raw : question.split("[\\s,.?!·]+")) {
            String token = PARTICLE.matcher(raw).replaceAll("");
            if (token.length() < 2 || token.matches(".*[0-9].*") || COMMON.contains(token)) continue;
            for (var option : options) {
                String value = name.apply(option);
                if (value != null && value.contains(token) && !partial.contains(option)) { partial.add(option); found = raw; }
            }
        }
        var distinctPartial = distinct(partial, id);
        if (distinctPartial.size() == 1) return distinctPartial.get(0);
        if (distinctPartial.size() > 1) throw ambiguous(question, distinctPartial, name, found, what);
        return null;
    }
    private static boolean contains(String question, String value) { return value != null && !value.isBlank() && question.contains(value); }
    private static <T> List<T> distinct(List<T> values, Function<T, String> id) {
        var seen = new LinkedHashMap<String, T>();
        for (var value : values) seen.putIfAbsent(id.apply(value), value);
        return List.copyOf(seen.values());
    }
    private static <T> AiAnalysisFunction.NoAnswer ambiguous(String question, List<T> options, Function<T, String> name,
        String token, String what) {
        // 단추에는 이름만, 누르면 「이름 + 나머지 질문」을 묻는다(질문에 있던 이름·낱말은 빼고 붙인다).
        var shown = options.stream().limit(5).toList();
        String rest = question;
        if (token != null) rest = rest.replace(token, " ");
        else for (var option : shown) if (name.apply(option) != null && !name.apply(option).isBlank())
            rest = rest.replace(name.apply(option), " ");
        String tail = rest.replaceAll("\\s+", " ").strip();
        var labels = shown.stream().map(name).toList();
        var suggestions = labels.stream().map(label -> tail.isEmpty() ? label : label + " " + tail).toList();
        return new AiAnalysisFunction.NoAnswer("어느 " + what + "인지 골라 주세요.", null, suggestions, labels);
    }
}
