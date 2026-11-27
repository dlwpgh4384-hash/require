package com.company.common.preferences.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class UserPreferenceRules {
    /** COLS: 목록 열 설정(SFR-010-05). 키=화면 번호, 값=숨긴 열 key 목록(JSON 배열). */
    /** AMT_UNIT: 금액 단위(공통코드 PREF_TYPE). 키=GLOBAL, 값=원·천 원·백만 원(시안 unitSel). */
    private static final Set<String> TYPES = Set.of("FAV", "RECENT", "MENU_FOLD", "PAGE_SIZE", "DENSE", "PERIOD", "COLS", "AMT_UNIT");
    private static final Set<String> MONTH_SCREENS = Set.of("UI-04", "UI-10", "UI-14", "UI-16", "UI-19", "UI-20",
        "UI-07", "UI-08", "UI-09", "UI-18");
    private final ObjectMapper json;

    public UserPreferenceRules(ObjectMapper json) { this.json = json; }

    public void validateKey(String type, String key) {
        if (!TYPES.contains(type)) throw UserPreferenceFailure.invalid("prefTypeCd", "설정 종류를 확인해 주세요.");
        if (key == null || key.isBlank() || key.length() > 30
            || (type.equals("PERIOD") || type.equals("COLS") ? !key.matches("[A-Za-z0-9_-]{1,10}") : !key.equals("GLOBAL"))) {
            throw UserPreferenceFailure.invalid("prefKey", "설정 키를 확인해 주세요.");
        }
    }

    public void validate(String type, String key, String value) {
        validateKey(type, key);
        if (value == null || value.isEmpty() || value.length() > 2000) invalid("설정 값은 1자 이상 2000자 이하로 입력해 주세요.");
        switch (type) {
            case "PAGE_SIZE" -> { if (!Set.of("20", "50", "100").contains(value)) invalid("목록 건수는 20, 50, 100 중에서 입력해 주세요."); }
            case "DENSE" -> { if (!Set.of("넓게", "좁게").contains(value)) invalid("보기 밀도는 넓게 또는 좁게를 선택해 주세요."); }
            case "MENU_FOLD" -> { if (!Set.of("true", "false").contains(value)) invalid("메뉴 접기 값을 확인해 주세요."); }
            case "AMT_UNIT" -> { if (!Set.of("원", "천 원", "백만 원").contains(value)) invalid("금액 단위는 원, 천 원, 백만 원 중에서 선택해 주세요."); }
            case "FAV", "RECENT" -> validateList(value, type.equals("FAV") ? 10 : 5);
            case "PERIOD" -> validatePeriod(key, value);
            case "COLS" -> validateColumns(value);
            default -> throw new IllegalStateException();
        }
    }

    /** FAV·RECENT 저장 값(화면 번호 배열)에서 지금 볼 수 있는 화면만 남긴다. 형식이 깨진 옛 값은 그대로. */
    public String readableScreens(String value) {
        if (com.company.common.support.auth.RequestAccess.current() == null) return value;
        try {
            JsonNode array = json.readTree(value);
            if (array == null || !array.isArray()) return value;
            var kept = json.createArrayNode();
            for (JsonNode item : array)
                if (!item.isTextual() || com.company.common.support.auth.RequestAccess.screenReadable(item.textValue())) kept.add(item);
            return json.writeValueAsString(kept);
        } catch (JsonProcessingException failure) {
            return value;
        }
    }

    /**
     * 화면이 보낸 목록은 볼 수 있는 화면만 담고 있으므로, 저장돼 있던 볼 수 없는 화면을 뒤에 붙여 지우지 않는다.
     * 개수 한도를 넘으면 볼 수 없는 화면부터 뺀다.
     */
    public String keepHidden(String type, String input, String stored) {
        if (!(type.equals("FAV") || type.equals("RECENT")) || stored == null
            || com.company.common.support.auth.RequestAccess.current() == null) return input;
        try {
            JsonNode next = json.readTree(input);
            JsonNode old = json.readTree(stored);
            if (next == null || !next.isArray() || old == null || !old.isArray()) return input;
            int limit = type.equals("FAV") ? 10 : 5;
            var merged = json.createArrayNode();
            var seen = new HashSet<String>();
            next.forEach(item -> { merged.add(item); seen.add(item.asText()); });
            for (JsonNode item : old)
                if (merged.size() < limit && item.isTextual() && !seen.contains(item.textValue())
                    && !com.company.common.support.auth.RequestAccess.screenReadable(item.textValue())) merged.add(item);
            return json.writeValueAsString(merged);
        } catch (JsonProcessingException failure) {
            return input;
        }
    }

    private JsonNode parse(String value) {
        try {
            return json.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(value);
        } catch (JsonProcessingException failure) {
            throw UserPreferenceFailure.invalid("prefVal", "설정 값의 형식을 확인해 주세요.");
        }
    }

    private void validateList(String value, int limit) {
        JsonNode array = parse(value);
        if (array == null || !array.isArray()) invalid("화면 목록의 형식을 확인해 주세요.");
        if (array.size() > limit) invalid(limit == 10 ? "즐겨찾기는 최대 10개까지 추가할 수 있습니다." : "최근 화면은 최대 5개까지 저장할 수 있습니다.");
        Set<String> seen = new HashSet<>();
        for (JsonNode item : array) {
            if (!item.isTextual() || !item.textValue().matches("[A-Za-z0-9_-]{1,10}")) invalid("화면 번호의 형식을 확인해 주세요.");
            if (!seen.add(item.textValue())) invalid("같은 화면을 중복해서 입력하지 말아 주세요.");
        }
    }

    private void validateColumns(String value) {
        JsonNode array = parse(value);
        if (array == null || !array.isArray() || array.size() > 50) invalid("열 설정의 형식을 확인해 주세요.");
        Set<String> seen = new HashSet<>();
        for (JsonNode item : array) {
            if (!item.isTextual() || !item.textValue().matches("[A-Za-z0-9_]{1,30}") || !seen.add(item.textValue()))
                invalid("열 설정의 형식을 확인해 주세요.");
        }
    }

    private void validatePeriod(String key, String value) {
        JsonNode period = parse(value);
        if (period == null || !period.isObject()) invalid("기간의 형식을 확인해 주세요.");
        var names = period.fieldNames();
        while (names.hasNext()) {
            if (!Set.of("year", "month").contains(names.next())) invalid("기간의 형식을 확인해 주세요.");
        }
        JsonNode year = period.get("year");
        if (year == null || !year.isIntegralNumber() || !year.canConvertToInt()) invalid("연도를 정수로 입력해 주세요.");
        JsonNode month = period.get("month");
        if (MONTH_SCREENS.contains(key)) {
            if (month == null || !month.isIntegralNumber() || !month.canConvertToInt()
                || month.intValue() < 1 || month.intValue() > 12) invalid("월은 1부터 12까지 입력해 주세요.");
        } else if (month != null) invalid("연도형 기간에는 월을 입력하지 말아 주세요.");
    }

    private static void invalid(String message) { throw UserPreferenceFailure.invalid("prefVal", message); }
}
