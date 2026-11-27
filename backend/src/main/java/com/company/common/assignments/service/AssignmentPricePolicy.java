package com.company.common.assignments.service;

import com.company.common.support.service.CodeFailure;
import com.company.common.support.auth.RequestAccess;
import java.math.BigDecimal;

public final class AssignmentPricePolicy {
    private AssignmentPricePolicy() {}
    public static final String NO_PRICE = "월 단가가 없어 배정할 수 없습니다. 단가를 볼 수 있는 담당자(재무 등)에게 요청해 주세요.";
    /** 투입 배정(UI-12) 메뉴. */
    public static final String MENU = "M006";
    /**
     * 청구 단가를 직접 넣거나 「단가 변경」할 수 있는지(명세 ⑨-5): 역할 이름이 아니라 민감 체크 + 투입 배정 수정 권한.
     * 등록만 있고 수정이 없으면 개발자 풀 월 단가로 저장한다.
     */
    public static boolean direct() { return direct(RequestAccess.sensitiveVisible()); }
    /** sensitive: 로그인 사용자의 민감 체크(CurrentBusinessActor.sensitive()). */
    public static boolean direct(boolean sensitive) { return sensitive && RequestAccess.menuUpdatable(MENU); }
    public static boolean billable(String contractType, String inputType) {
        return !"FREE".equals(inputType) && ("MO".equals(contractType) || "DP".equals(contractType));
    }
    /** direct=false(민감 체크 없음)이면 넣은 값은 버리고 개발자 풀 월 단가로 저장한다. */
    public static BigDecimal registration(boolean direct, String contractType, String inputType,
            BigDecimal requested, BigDecimal standard) {
        if (!billable(contractType,inputType)) return BigDecimal.ZERO;
        BigDecimal price=direct?requested:standard;
        if (price==null || price.signum()<=0) throw CodeFailure.invalid("billingPrice",
            direct?"청구 단가를 입력해 주세요.":NO_PRICE);
        return price;
    }
}
