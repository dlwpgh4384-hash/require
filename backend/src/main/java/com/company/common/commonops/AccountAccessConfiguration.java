package com.company.common.commonops;

import com.company.common.commonops.mapper.AccountAccessMapper;
import com.company.common.commonops.service.AccountAccessService;
import com.company.common.commonops.service.AccountAccessFailure;
import com.company.common.support.auth.AccountSessions;
import com.company.common.support.service.BusinessCalendar;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
public class AccountAccessConfiguration {
    @Bean
    org.springframework.boot.web.servlet.ServletContextInitializer accountSessionCookie(
        @org.springframework.beans.factory.annotation.Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        return context -> {
            context.getSessionCookieConfig().setHttpOnly(true);
            context.getSessionCookieConfig().setSecure(secure);
        };
    }
    @Bean
    org.springframework.boot.web.servlet.server.CookieSameSiteSupplier accountCookieSameSite() {
        return org.springframework.boot.web.servlet.server.CookieSameSiteSupplier.ofLax().whenHasName("JSESSIONID");
    }
    @Bean
    @Order(10)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain accountAccessSecurity(HttpSecurity http, ObjectMapper json, AccountAccessService service,
        com.company.common.commonops.service.AccountApiPolicy policy, AccountAccessMapper mapper) throws Exception {
        Set<String> publicPosts = Set.of("/api/common/login", "/api/common/password-find", "/api/common/mfa/resend", "/api/common/mfa/verify");
        http.securityMatcher("/api/**", "/screens/UI-22").authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/health", "/api/common/login", "/api/common/password-find", "/api/common/mfa/resend", "/api/common/mfa/verify").permitAll()
            .requestMatchers("/api/**").authenticated().anyRequest().permitAll())
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .headers(headers -> headers
                .referrerPolicy(referrer -> referrer.policy(
                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults()))
            .requestCache(cache -> cache.disable()).logout(logout -> logout.disable())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, failure) -> error(response, json, 401, "다시 로그인해 주세요."))
                .accessDeniedHandler((request, response, failure) -> error(response, json, 403, "요청 토큰을 확인해 주세요.")))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws IOException, ServletException {
                    response.setHeader("Cache-Control", "no-store");
                    String path = request.getServletPath();
                    if (path.startsWith("/api/") && !path.equals("/api/health") && !publicPosts.contains(path)) {
                        try {
                            var actor = service.authenticated(request);
                            if (actor.passwordChangeRequired && !path.equals("/api/common/my-password") && !path.equals("/api/common/logout")) {
                                error(response, json, 403, "비밀번호를 먼저 변경해 주세요.");
                                return;
                            }
                            String form = null;
                            if (path.startsWith("/api/common/uploads/templates/")) form = path.substring(path.lastIndexOf('/') + 1);
                            else if (path.equals("/api/common/uploads")) form = request.getParameter("formTypeCode");
                            else if (path.startsWith("/api/common/uploads/")) {
                                String number = path.substring("/api/common/uploads/".length()).split("/")[0];
                                if (number.matches("[0-9]{1,12}")) form = mapper.uploadForm(Long.parseLong(number), actor.userId);
                            }
                            var permissions = mapper.permissions(actor.userId);
                            var roles = mapper.roles(actor.userId);
                            policy.check(request.getMethod(), path, form, permissions, roles);
                            var access = access(mapper, actor.userId, roles, permissions, request.getRemoteAddr());
                            access.store(request);
                            com.company.common.commonops.service.AccountScopeGuard.check(mapper, path, access.scope());
                            if (access.partScope() && !access.scope().all())
                                com.company.common.commonops.service.AccountScopeGuard.checkDeveloper(mapper, path, access.scope());
                        } catch (AccountAccessFailure failure) {
                            error(response, json, failure.status, failure.getMessage());
                            return;
                        }
                    }
                    chain.doFilter(request, response);
                }
            }, CsrfFilter.class)
            .addFilterAfter(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws IOException, ServletException {
                    if ((request.getServletPath().equals("/api/health") || request.getServletPath().equals("/screens/UI-22"))
                        && request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) token.getToken();
                    chain.doFilter(request, response);
                }
            }, CsrfFilter.class);
        return http.build();
    }
    /** 요청마다 DB에서 다시 읽은 권한·범위·민감 체크(명세 ③-6). 역할이 여럿이면 합집합(⑧-1). */
    static com.company.common.support.auth.RequestAccess access(AccountAccessMapper mapper, String userId,
        List<String> roles, List<AccountAccessMapper.Permission> permissions, String ip) {
        var parts = Set.copyOf(mapper.scopeParts(userId));
        var projects = Set.copyOf(mapper.scopeProjects(userId));
        var codes = mapper.scopeCodes(userId);
        var scope = com.company.common.users.service.UserScopePolicy.union(codes.stream()
            .map(code -> new com.company.common.users.service.UserScopePolicy.Grant(code, parts, projects)).toList());
        var readable = permissions.stream().filter(permission -> "Y".equals(permission.readYn)).toList();
        return new com.company.common.support.auth.RequestAccess(userId, Set.copyOf(roles), scope, mapper.sensitive(userId),
            readable.stream().map(permission -> permission.menuId).collect(java.util.stream.Collectors.toSet()),
            readable.stream().map(permission -> permission.screenId).collect(java.util.stream.Collectors.toSet()), ip,
            codes.contains("PART"), readable.stream().filter(permission -> "Y".equals(permission.updateYn))
                .map(permission -> permission.menuId).collect(java.util.stream.Collectors.toSet()));
    }
    @Bean
    SessionAudit accountSessionAudit(AccountAccessMapper mapper, BusinessCalendar calendar,
        org.springframework.transaction.PlatformTransactionManager manager) { return new SessionAudit(mapper, calendar, manager); }
    public static class SessionAudit {
        private final AccountAccessMapper mapper;
        private final BusinessCalendar calendar;
        private final org.springframework.transaction.support.TransactionTemplate transaction;
        SessionAudit(AccountAccessMapper mapper, BusinessCalendar calendar, org.springframework.transaction.PlatformTransactionManager manager) {
            this.mapper = mapper;
            this.calendar = calendar;
            this.transaction = new org.springframework.transaction.support.TransactionTemplate(manager);
            this.transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
        @EventListener public void ended(AccountSessions.Ended event) {
            transaction.executeWithoutResult(status -> mapper.access(event.userId(), "LOGOUT", event.reason(), event.ip(), calendar.now()));
        }
    }
    static void error(HttpServletResponse response, ObjectMapper json, int status, String message) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), Map.of("success", false, "meta", Map.of(), "message", message, "errors", List.of()));
    }
}
