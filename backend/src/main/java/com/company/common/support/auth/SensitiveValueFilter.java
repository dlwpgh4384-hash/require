package com.company.common.support.auth;

/**
 * 민감 칸(급여·지급 단가·개인별 원가·월 단가 등) 응답 거르기(명세 ⑨-2).
 * 칸에 {@code @JsonInclude(value = CUSTOM, valueFilter = SensitiveValueFilter.class)}를 붙이면
 * 민감 체크가 없는 사용자의 응답에서 그 칸이 아예 빠진다. 판단은 요청마다 RequestAccess 한 곳.
 * (Jackson은 null 거르기를 처음 한 번만 물어 담아 두므로 null은 늘 그대로 둔다 — 값이 없으니 드러나는 것도 없다.)
 */
public final class SensitiveValueFilter {
    @Override
    public boolean equals(Object value) {
        return value != null && !RequestAccess.sensitiveVisible();
    }

    @Override
    public int hashCode() { return SensitiveValueFilter.class.hashCode(); }
}
