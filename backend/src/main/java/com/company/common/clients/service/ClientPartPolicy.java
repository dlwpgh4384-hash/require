package com.company.common.clients.service;

import java.util.Set;

/** 순수 업무 정책이며 HTTP 인증·메뉴 검사와 연결하지 않는다. */
public final class ClientPartPolicy {
    private ClientPartPolicy() {}

    public static boolean canCreateClient(Set<String> roles, String clientTypeCode) {
        boolean supported = "CUST".equals(clientTypeCode) || "VNDR".equals(clientTypeCode)
            || "BOTH".equals(clientTypeCode);
        return supported && (roles.contains("ADMIN") || roles.contains("FIN")
            || (roles.contains("SALES_LDR") && "CUST".equals(clientTypeCode)));
    }

    public static boolean canCreatePart(Set<String> roles) {
        return roles.contains("ADMIN") || roles.contains("FIN");
    }

    /** 지급 계좌(끝 4자리·[보기] 전체)는 역할의 민감 체크로만 본다(명세 ⑨-3, 역할 이름 예외 없음). */
    public static boolean canReadPaymentAccount(boolean sensitive) {
        return sensitive;
    }
}
