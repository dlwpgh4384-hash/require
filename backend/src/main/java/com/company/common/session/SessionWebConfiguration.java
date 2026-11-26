package com.company.common.session;

import com.company.common.session.service.SessionPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class SessionWebConfiguration implements WebMvcConfigurer {
    private final ObjectProvider<SessionPolicy> policies;

    // 화면별 조각 시험처럼 세션 정책이 없는 문맥에서도 기동되게 한다(실제 앱에는 항상 있다).
    public SessionWebConfiguration(ObjectProvider<SessionPolicy> policies) { this.policies = policies; }

    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                Object handler) {
                response.setHeader("Cache-Control", "no-store");
                SessionPolicy policy = policies.getIfAvailable();
                if (policy != null && SessionPolicy.authenticated()) {
                    boolean refresh = !"/api/common/session/remaining".equals(request.getRequestURI());
                    var timing = policy.onRequest(request, refresh);
                    response.setHeader("X-Session-Remaining-Seconds", Long.toString(timing.remainingSeconds()));
                    response.setHeader("X-Session-Minutes", Integer.toString(timing.sessionMinutes()));
                    response.setHeader("X-Session-Server-Responded-At", timing.serverRespondedAt().toString());
                }
                return true;
            }
        }).addPathPatterns("/api/**");
    }
}
