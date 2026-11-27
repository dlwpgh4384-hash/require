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
}
