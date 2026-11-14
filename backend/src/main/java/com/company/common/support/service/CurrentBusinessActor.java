package com.company.common.support.service;

import java.util.Set;
import org.springframework.stereotype.Component;

/** UI-22 연결 전의 명시적인 개발 실행 문맥. 요청 헤더에서 권한을 받지 않는다. */
@Component
public class CurrentBusinessActor {
    public String userId() { return "admin"; }
    public Set<String> roles() { return Set.of("ADMIN", "FIN", "SALES_LDR"); }
}
