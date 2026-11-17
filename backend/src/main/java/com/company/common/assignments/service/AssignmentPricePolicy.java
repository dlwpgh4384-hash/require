package com.company.common.assignments.service;

import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.util.Collection;

public final class AssignmentPricePolicy {
    private AssignmentPricePolicy() {}
    public static boolean direct(Collection<String> roles) {
        return roles.contains("FIN") || roles.contains("SALES_LDR");
    }
    public static boolean billable(String contractType, String inputType) {
        return !"FREE".equals(inputType) && ("MO".equals(contractType) || "DP".equals(contractType));
    }
    public static BigDecimal registration(Collection<String> roles, String contractType, String inputType,
            BigDecimal requested, BigDecimal standard) {
        if (!billable(contractType,inputType)) return BigDecimal.ZERO;
        BigDecimal price=direct(roles)?requested:standard;
        if (price==null || price.signum()<=0) throw CodeFailure.invalid("billingPrice",
            direct(roles)?"청구 단가를 입력해 주세요.":"월 단가가 없어 배정할 수 없습니다. 파트장·재무에게 요청해 주세요.");
        return price;
    }
}
