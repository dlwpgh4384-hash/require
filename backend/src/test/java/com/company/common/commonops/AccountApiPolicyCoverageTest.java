package com.company.common.commonops;

import com.company.common.commonops.service.AccountApiPolicy;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import static org.assertj.core.api.Assertions.*;

/** 모든 화면 API가 「API → (메뉴, 동작)」 목록이나 「로그인만」 목록에 있는지(명세 ⑦-1, 빠진 API 0개). */
class AccountApiPolicyCoverageTest {
    private static final List<String> PUBLIC = List.of("POST /api/common/login", "POST /api/common/password-find",
        "POST /api/common/mfa/resend", "POST /api/common/mfa/verify");

    @Test void everyControllerRouteIsInTheInventory() throws Exception {
        var policy = new AccountApiPolicy();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        var missing = new ArrayList<String>();
        int checked = 0;
        for (var candidate : scanner.findCandidateComponents("com.company.common")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            var base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String[] prefixes = base == null || base.path().length == 0 ? new String[]{""} : base.path();
            for (Method method : type.getDeclaredMethods()) {
                var mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) continue;
                String[] paths = mapping.path().length == 0 ? new String[]{""} : mapping.path();
                for (String prefix : prefixes) for (String path : paths) for (RequestMethod verb : mapping.method()) {
                    String full = prefix + path;
                    if (!full.startsWith("/api/common/") || PUBLIC.contains(verb + " " + full)) continue;
                    checked++;
                    if (!policy.covers(verb.name(), full)) missing.add(verb + " " + full + " (" + type.getSimpleName() + ")");
                }
            }
        }
        assertThat(checked).isGreaterThan(100);
        assertThat(missing).isEmpty();
    }

    /** 「로그인만」(SELF)은 내 것(내 정보·내 기억·내 비밀번호·내 알림·세션)과 여러 화면이 함께 쓰는 고르기 목록·왼쪽 메뉴만. */
    private static final List<String> SELF_ALLOWED = List.of(
        "GET /api/common/my-info", "GET /api/common/session/remaining", "GET /api/common/notifications",
        "GET /api/common/user-preferences/{prefTypeCd}/{prefKey}", "GET /api/common/menus",
        "GET /api/common/user-options", "GET /api/common/code-groups/{groupId}/codes", "GET /api/common/clients",
        "GET /api/common/parts", "GET /api/common/parts/options", "GET /api/common/developers",
        "GET /api/common/developers/areas", "GET /api/common/projects", "GET /api/common/bill-options",
        "GET /api/common/ai-recommendation-options",
        "PUT /api/common/user-preferences/{prefTypeCd}/{prefKey}", "PUT /api/common/my-password",
        "PUT /api/common/notifications/{notificationId}/read", "PUT /api/common/notifications/read-all",
        "POST /api/common/logout", "POST /api/common/session/extend");
    /** 등록·변경(GET 아닌 요청) 중 로그인만으로 되는 것은 내 것뿐이다. */
    private static final List<String> SELF_WRITES = List.of(
        "PUT /api/common/user-preferences/{prefTypeCd}/{prefKey}", "PUT /api/common/my-password",
        "PUT /api/common/notifications/{notificationId}/read", "PUT /api/common/notifications/read-all",
        "POST /api/common/logout", "POST /api/common/session/extend");

    @Test void loginOnlyRoutesAreOnlyPersonalOrSharedSelectors() {
        var self = new AccountApiPolicy().selfRoutes();
        // 메뉴 등록(POST menus)처럼 관리 화면 쓰기가 로그인만으로 분류되면 여기서 잡힌다(MNU-19).
        assertThat(self).as("로그인만 목록에 새로 넣으려면 명세(⑦-1)를 확인하고 여기에도 적는다").allMatch(SELF_ALLOWED::contains);
        assertThat(self.stream().filter(route -> !route.startsWith("GET ")).toList())
            .as("로그인만 쓰기는 내 것뿐").allMatch(SELF_WRITES::contains);
        assertThat(self).doesNotContain("POST /api/common/menus", "GET /api/common/menu-options");
    }
}
