package com.company.common.support.auth;

import com.company.common.users.service.UserScopePolicy;
import java.util.Set;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 요청마다 보안 필터가 DB에서 한 번 읽은 로그인 사용자의 권한·범위·민감 체크(명세 ③-6).
 * 서비스·매퍼는 여기만 본다(역할 이름으로 고르지 않는다). 요청이 아닌 배치·준비 명령과
 * 보안 필터 없이 도는 조각 시험에는 값이 없어 「전체 범위·민감 보임·모든 메뉴」로 본다.
 */
public record RequestAccess(String userId, Set<String> roles, UserScopePolicy.Scope scope, boolean sensitive,
                            Set<String> readableMenus, Set<String> readableScreens, String clientIp, boolean partScope,
                            Set<String> updatableMenus) {
    private static final String ATTRIBUTE = RequestAccess.class.getName();
    private static final UserScopePolicy.Scope ALL = new UserScopePolicy.Scope(true, Set.of(), Set.of());

    public RequestAccess {
        roles = Set.copyOf(roles);
        readableMenus = Set.copyOf(readableMenus);
        readableScreens = Set.copyOf(readableScreens);
        updatableMenus = Set.copyOf(updatableMenus);
    }

    public void store(jakarta.servlet.http.HttpServletRequest request) { request.setAttribute(ATTRIBUTE, this); }

    public static RequestAccess current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes == null ? null
            : (RequestAccess) attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    }

    /** 매퍼 XML의 bind에서 부른다: 범위 밖 자료를 WHERE로 뺀다. */
    public static UserScopePolicy.Scope currentScope() {
        RequestAccess access = current();
        return access == null ? ALL : access.scope;
    }

    public static boolean scopeAll() { return currentScope().all(); }

    /**
     * 개발자 풀(UI-02)만 거르는 범위(명세 ⑧-3, 시안 devPartOnly): 「전체」가 없고 「담당 파트」 역할이 있으면 자기 파트 인력만.
     * PM(담당 프로젝트만)은 거르지 않는다.
     */
    public static boolean developerPartOnly() {
        RequestAccess access = current();
        return access != null && !access.scope.all() && access.partScope;
    }

    /** 대시보드 모양(명세 ⑧-4, 시안 dashMode): all(전체) / part(담당 파트 역할이 하나라도) / pm. */
    public static String dashboardMode() {
        RequestAccess access = current();
        if (access == null || access.scope.all()) return "all";
        return access.partScope ? "part" : "pm";
    }
    public static Set<String> scopeParts() { return currentScope().partIds(); }
    public static Set<String> scopeProjects() { return currentScope().projectIds(); }

    /** 민감 칸(급여·단가·원가·계좌)을 보여도 되는지. 역할의 민감 체크 하나만 본다(명세 ⑨-1). */
    public static boolean sensitiveVisible() {
        RequestAccess access = current();
        return access == null || access.sensitive;
    }

    /** 이 화면(UI-xx)을 조회할 수 있는지. 값이 없으면(보안 필터 밖) 막지 않는다. */
    public static boolean screenReadable(String screenId) {
        RequestAccess access = current();
        return access == null || access.readableScreens.contains(screenId);
    }

    public static boolean menuReadable(String menuId) {
        RequestAccess access = current();
        return access == null || access.readableMenus.contains(menuId);
    }

    /** 이 메뉴(M0xx)의 수정 권한이 있는지. 값이 없으면(보안 필터 밖) 막지 않는다. */
    public static boolean menuUpdatable(String menuId) {
        RequestAccess access = current();
        return access == null || access.updatableMenus.contains(menuId);
    }

    /** 접근 기록 줄의 CLIENT_IP. 요청이 없으면 NULL. */
    public static String ip() {
        RequestAccess access = current();
        if (access != null) return access.clientIp;
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet
            ? servlet.getRequest().getRemoteAddr() : null;
    }
}
