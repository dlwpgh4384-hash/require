package com.company.common.support.test;

import java.util.List;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 로그인(UI-22) 작업으로 /api/** 가 로그인을 요구하고, 현재 사용자는 로그인 정보에서만 온다.
 * 로그인 자체를 시험하지 않는 기존 화면 시험은 한 곳에서 로그인된 상태로 맞춘다:
 * 사용자는 로그인 작업 전 개발 실행 문맥과 같은 admin(ADMIN·FIN·SALES_LDR)이라 기존 시험의 기대값이 그대로다.
 * - MockMvc 요청: 보안 문맥 + CSRF 토큰(아래 Customizer)
 * - 요청 밖 서비스 호출(저장 시험 등): 시험 메서드마다 같은 사용자(아래 Listener)
 * 로그인·세션·2차 인증·알림·AI 보안을 직접 시험하는 시험은 건드리지 않는다.
 */
public class AuthenticatedMockMvcCustomizerFactory implements ContextCustomizerFactory {
    private static final List<String> OWN_SECURITY_TESTS = List.of(
        "com.company.common.commonops.Account", "com.company.common.commonops.LoginFailure",
        "com.company.common.session.", "com.company.common.mfa.",
        "com.company.common.notifications.", "com.company.common.aianalysis.controller.CommonAiAnalysisControllerTest",
        // 실제 로그인 API·보안 필터로 역할 5개를 시험한다(자동 admin 로그인을 쓰지 않음).
        "com.company.common.rolesecurity.");

    static boolean ownSecurity(Class<?> testClass) {
        String name = testClass.getName();
        return OWN_SECURITY_TESTS.stream().anyMatch(name::startsWith);
    }

    static SecurityContext developmentAdmin() {
        var security = SecurityContextHolder.createEmptyContext();
        security.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_FIN"), new SimpleGrantedAuthority("ROLE_SALES_LDR"))));
        return security;
    }

    /** 시험이 요청을 새로 만들어 바꿀 때(파일 올리기) 로그인 세션·CSRF 머리글을 옮긴다. */
    public static org.springframework.mock.web.MockHttpServletRequest keepLogin(
        org.springframework.mock.web.MockHttpServletRequest built, org.springframework.mock.web.MockHttpServletRequest original) {
        built.setSession(original.getSession());
        for (String name : java.util.Collections.list(original.getHeaderNames()))
            for (String value : java.util.Collections.list(original.getHeaders(name))) built.addHeader(name, value);
        return built;
    }

    /** 스프링 없이 도는 단위 시험용: try (var login = loginDevelopmentAdmin()) { ... } */
    public static AutoCloseable loginDevelopmentAdmin() {
        SecurityContextHolder.setContext(developmentAdmin());
        return SecurityContextHolder::clearContext;
    }

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> attributes) {
        return ownSecurity(testClass) ? null : new Customizer();
    }

    static final class Customizer implements ContextCustomizer {
        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration merged) {
            if (!(context instanceof GenericApplicationContext registry)) return;
            registry.registerBean("authenticatedMockMvcRequests", MockMvcBuilderCustomizer.class,
                () -> builder -> builder.defaultRequest(get("/").with(request -> {
                    var security = developmentAdmin();
                    request.getSession().setAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, security);
                    var repository = new HttpSessionCsrfTokenRepository();
                    var token = repository.generateToken(request);
                    repository.saveToken(token, request, null);
                    new XorCsrfTokenRequestAttributeHandler().handle(request, new MockHttpServletResponse(), () -> token);
                    var masked = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                    request.addHeader(masked.getHeaderName(), masked.getToken());
                    // 보안 필터를 끈 조각 시험(addFilters = false)도 같은 사용자로 보이게 한다.
                    SecurityContextHolder.setContext(security);
                    return request;
                    // 보안 필터는 요청이 끝나면 문맥을 비우므로, 요청 뒤 직접 부르는 서비스도 같은 사용자로 되돌린다.
                })).alwaysDo(result -> SecurityContextHolder.setContext(developmentAdmin())));
        }

        @Override public boolean equals(Object other) { return other instanceof Customizer; }
        @Override public int hashCode() { return Customizer.class.hashCode(); }
    }

    /** 요청 밖에서 부르는 서비스(저장 시험·동시 실행 시험)도 같은 사용자. 새로 만든 작업 스레드도 물려받는다. */
    public static class Listener implements TestExecutionListener {
        static {
            SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
        }
        @Override public void beforeTestMethod(TestContext context) {
            if (!ownSecurity(context.getTestClass())) SecurityContextHolder.setContext(developmentAdmin());
        }
        @Override public void afterTestMethod(TestContext context) {
            if (!ownSecurity(context.getTestClass())) SecurityContextHolder.clearContext();
        }
    }
}
