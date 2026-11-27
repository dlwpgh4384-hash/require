package com.company.common.support.controller;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import static org.assertj.core.api.Assertions.assertThat;

/** 순서 없는 화면별 처리기는 공통 처리기(가장 뒤, Exception 전부 받음)와 같은 순위가 되어
 * 앱 묶음(jar)에서 4xx가 500으로 바뀔 수 있다(로그인 429 사례). 모든 처리기가 공통 처리기보다 앞인지 본다. */
class ExceptionHandlerOrderContractTest {
    @Test
    @DisplayName("공통 처리기 말고 모든 오류 처리기는 공통 처리기보다 먼저 받는다")
    void everyAdviceRunsBeforeTheCatchAll() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ControllerAdvice.class));
        List<String> unordered = new ArrayList<>();
        int found = 0;
        for (var candidate : scanner.findCandidateComponents("com.company")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            if (type == ApiExceptionHandler.class) continue;
            found++;
            if (OrderUtils.getOrder(type, Ordered.LOWEST_PRECEDENCE) >= Ordered.LOWEST_PRECEDENCE) unordered.add(type.getName());
        }
        assertThat(found).isGreaterThan(5);
        assertThat(unordered).isEmpty();
        assertThat(OrderUtils.getOrder(ApiExceptionHandler.class, 0)).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }
}
