package com.company.common.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
public class NotificationSecurityConfiguration {
    @Bean
    @Order(20)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain notificationSecurityFilterChain(HttpSecurity http, ObjectMapper json) throws Exception {
        http.securityMatcher("/api/common/notifications", "/api/common/notifications/**")
            .headers(headers -> headers.referrerPolicy(policy -> policy.policy(
                ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .requestCache(cache -> cache.disable())
            .exceptionHandling(errors -> errors
                .accessDeniedHandler((request, response, failure) -> error(response, json, 403, "요청 토큰을 확인해 주세요."))
                .authenticationEntryPoint((request, response, failure) -> error(response, json, 401, "다시 로그인해 주세요.")))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                    Authentication actor = SecurityContextHolder.getContext().getAuthentication();
                    if (actor == null || !actor.isAuthenticated() || actor instanceof AnonymousAuthenticationToken) {
                        error(response, json, 401, "다시 로그인해 주세요.");
                        return;
                    }
                    chain.doFilter(request, response);
                }
            }, CsrfFilter.class);
        return http.build();
    }

    private static void error(HttpServletResponse response, ObjectMapper json, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), Map.of("success", false, "meta", Map.of(),
            "message", message, "errors", List.of()));
    }
}
