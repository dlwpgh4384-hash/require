package com.company.common.rolesecurity;

import com.company.common.assignments.service.AssignmentPricePolicy;
import com.company.common.raterenegotiations.dto.RateRenegotiationContracts.Row;
import com.company.common.raterenegotiations.service.RateRenegotiationColumnPolicy;
import com.company.common.support.auth.RequestAccess;
import com.company.common.users.service.UserScopePolicy;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static org.assertj.core.api.Assertions.*;

/** 요청마다 읽은 범위·민감 체크·수정 권한으로 정하는 규칙(명세 ⑧-3·⑧-4·⑨-5·⑨-1 UI-32). DB 없이. */
class ScopeAndSensitivePolicyTest {
    @AfterEach void clear() { RequestContextHolder.resetRequestAttributes(); }

    static void as(boolean all, Set<String> parts, Set<String> projects, boolean sensitive, boolean partScope, Set<String> updatable) {
        var request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        new RequestAccess("u", Set.of(), new UserScopePolicy.Scope(all, parts, projects), sensitive, Set.of(), Set.of(),
            "127.0.0.1", partScope, updatable).store(request);
    }

    @Test
    @DisplayName("대시보드 모양: 전체 → all, 담당 파트 역할 → part, 담당 프로젝트만 → pm, 요청 밖 → all")
    void dashboardMode() {
        assertThat(RequestAccess.dashboardMode()).isEqualTo("all");
        as(true, Set.of(), Set.of(), true, true, Set.of());
        assertThat(RequestAccess.dashboardMode()).isEqualTo("all");
        as(false, Set.of("p1"), Set.of(), false, true, Set.of());
        assertThat(RequestAccess.dashboardMode()).isEqualTo("part");
        assertThat(RequestAccess.developerPartOnly()).isTrue();
        as(false, Set.of(), Set.of("j1"), false, false, Set.of());
        assertThat(RequestAccess.dashboardMode()).isEqualTo("pm");
        assertThat(RequestAccess.developerPartOnly()).isFalse();
        // 파트가 이번 달 없어도 담당 파트 역할이면 파트장 모양(자기 파트 0건).
        as(false, Set.of(), Set.of(), false, true, Set.of());
        assertThat(RequestAccess.dashboardMode()).isEqualTo("part");
    }

    @Test
    @DisplayName("단가 직접 입력 = 민감 체크 + 투입 배정(M006) 수정 권한 — 하나라도 없으면 개발자 풀 월 단가")
    void directPriceNeedsSensitiveAndAssignmentUpdate() {
        as(false, Set.of("p1"), Set.of(), true, true, Set.of("M006"));
        assertThat(AssignmentPricePolicy.direct()).isTrue();
        assertThat(AssignmentPricePolicy.registration(AssignmentPricePolicy.direct(), "MO", "NEW", new BigDecimal("9"), new BigDecimal("7")))
            .isEqualByComparingTo("9");
        as(true, Set.of(), Set.of(), true, false, Set.of());
        assertThat(AssignmentPricePolicy.direct()).isFalse();
        assertThat(AssignmentPricePolicy.registration(AssignmentPricePolicy.direct(), "MO", "NEW", new BigDecimal("9"), new BigDecimal("7")))
            .isEqualByComparingTo("7");
        as(false, Set.of("p1"), Set.of(), false, true, Set.of("M006"));
        assertThat(AssignmentPricePolicy.direct()).isFalse();
        assertThatThrownBy(() -> AssignmentPricePolicy.registration(false, "DP", "NEW", new BigDecimal("9"), BigDecimal.ZERO))
            .hasMessageContaining("월 단가가 없어 배정할 수 없습니다.");
    }

    @Test
    @DisplayName("단가 재협상(UI-32): 민감 체크가 없으면 현재 단가·월 원가·목표 단가가 응답·엑셀 열에서 빠지고 이익률·필요 인상률은 남는다")
    void rateRenegotiationSensitiveColumns() throws Exception {
        var row = new Row("a", "d", "이름", "정규직", "프로젝트", LocalDate.of(2026, 12, 31), 30, BigDecimal.ONE,
            new BigDecimal("7000000"), new BigDecimal("6500000"), new BigDecimal("7.1"), new BigDecimal("8130000"),
            new BigDecimal("16.1"), new BigDecimal("4200000"), "p", "m");
        var json = JsonMapper.builder().findAndAddModules().build();
        as(false, Set.of("p1"), Set.of(), false, true, Set.of());
        var hidden = json.readTree(json.writeValueAsString(row));
        assertThat(hidden.has("currentUnitPrice")).isFalse();
        assertThat(hidden.has("monthlyCost")).isFalse();
        assertThat(hidden.has("targetUnitPrice")).isFalse();
        assertThat(hidden.has("profitRate")).isTrue();
        assertThat(hidden.has("requiredIncreaseRate")).isTrue();
        assertThat(RateRenegotiationColumnPolicy.validate(List.of("developerName", "currentUnitPrice", "monthlyCost", "profitRate", "targetUnitPrice")))
            .containsExactly("developerName", "profitRate");
        as(true, Set.of(), Set.of(), true, false, Set.of());
        var shown = json.readTree(json.writeValueAsString(row));
        assertThat(shown.has("currentUnitPrice")).isTrue();
        assertThat(shown.has("targetUnitPrice")).isTrue();
        assertThat(shown.has("monthlyCost")).isTrue();
    }
}
