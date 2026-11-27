package com.company.common.support.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/** 로그인 단계가 끝나면 요청 토큰(XSRF-TOKEN 쿠키)을 새로 만든다. 화면은 요청마다 쿠키를 다시 읽으므로 따로 고칠 것이 없다. */
public final class LoginCsrfToken {
    // 보안 설정과 같은 쿠키 이름·경로(XSRF-TOKEN, 앱 경로)를 쓴다.
    private static final CookieCsrfTokenRepository TOKENS = CookieCsrfTokenRepository.withHttpOnlyFalse();
    private LoginCsrfToken() {}
    public static void rotate(HttpServletRequest request, HttpServletResponse response) {
        if (request == null || response == null || response.isCommitted()) return;
        TOKENS.saveToken(TOKENS.generateToken(request), request, response);
    }
}
