package com.company.common.support.test;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.security.core.context.SecurityContextHolder;

/** 스프링 없이 도는 단위 시험도 로그인 전 개발 사용자(admin)로 로그인한 상태로 본다(로그인 보안 시험은 빼고). */
public class AdminLoginExtension implements BeforeEachCallback, AfterEachCallback {
    @Override public void beforeEach(ExtensionContext context) {
        if (!AuthenticatedMockMvcCustomizerFactory.ownSecurity(context.getRequiredTestClass()))
            SecurityContextHolder.setContext(AuthenticatedMockMvcCustomizerFactory.developmentAdmin());
    }
    @Override public void afterEach(ExtensionContext context) {
        if (!AuthenticatedMockMvcCustomizerFactory.ownSecurity(context.getRequiredTestClass()))
            SecurityContextHolder.clearContext();
    }
}
