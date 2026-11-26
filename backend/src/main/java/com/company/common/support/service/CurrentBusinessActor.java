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
