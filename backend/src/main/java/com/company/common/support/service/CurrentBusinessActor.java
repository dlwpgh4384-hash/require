package com.company.common.support.service;

import java.util.Set;
import org.springframework.stereotype.Component;

/** Request actors come only from the server security context; non-HTTP work is SYSTEM. */
@Component
public class CurrentBusinessActor {
    public String userId() {
        var actor = authentication();
        if (actor != null) return actor.getName();
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() != null)
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("다시 로그인해 주세요.");
        return "SYSTEM";
    }
    /** 매퍼 XML bind용: 등록자·수정자·기록 사용자 칸에 넣을 로그인 아이디(요청 밖은 SYSTEM). */
    public static String id() { return new CurrentBusinessActor().userId(); }
    /** 민감 칸을 보여도 되는지(역할의 민감 체크, 명세 ⑨-1). 역할 이름으로 고르지 않는다. */
    public boolean sensitive() { return com.company.common.support.auth.RequestAccess.sensitiveVisible(); }
    /** 데이터 범위(명세 ⑧-1). 요청 밖(배치)은 전체. */
    public com.company.common.users.service.UserScopePolicy.Scope scope() { return com.company.common.support.auth.RequestAccess.currentScope(); }
    public Set<String> roles() {
        var actor = authentication();
        if (actor == null) return Set.of();
        return actor.getAuthorities().stream().map(org.springframework.security.core.GrantedAuthority::getAuthority)
            .filter(role -> role.startsWith("ROLE_")).map(role -> role.substring(5))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private org.springframework.security.core.Authentication authentication() {
        var actor = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return actor != null && actor.isAuthenticated() && !(actor instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
            ? actor : null;
    }
}
