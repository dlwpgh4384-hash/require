package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.dto.CalculationRuleContracts.Standard;
import com.company.common.calculation.service.CalculationRuleService;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectAmountRulesTest {
    private CalculationRuleService metadata;
    private ProjectAmountRules rules;
    private final YearMonth month = YearMonth.of(2026, 11);

    @BeforeEach
    void setup() {
        metadata = mock(CalculationRuleService.class);
        rules = new ProjectAmountRules(metadata);
        when(metadata.definition("식-00")).thenReturn(new Definition("식-00", "공통 원칙", "원 단위 버림"));
        when(metadata.definition("식-19")).thenReturn(new Definition("식-19",
            "예산(목표 원가) = 프로젝트별 입력값(기본값 현재 계약금액 × (1 − 목표 마진율), 목표 마진율 20%면 80%, 수정 가능). 예산 소진율 = 원가 누계 ÷ 예산 × 100", "상세"));
        when(metadata.effectiveStandard("TGT_MRGN_RT", month))
            .thenReturn(new Standard("TGT_MRGN_RT", month, new BigDecimal("20")));
        when(metadata.definition("식-11")).thenReturn(new Definition("식-11",
            "팀비·경비 몫 = (그 프로젝트 팀비(월) + 프로젝트경비(월)) × 그 사람의 그 프로젝트 MM. 프로젝트 직접 경비 = 투입 자사 인력 팀비·경비 몫의 합 + 그 달 추가 직접 경비 합계",
            "이전 배분 문장: 프로젝트 전체 자사 MM 합으로 나눔"));
    }

    @Test
    void readsTheAutomaticBudgetFromTheCurrentDefinitionAndEffectiveMargin() {
        assertThat(rules.automaticBudget(new BigDecimal("1000000"), month)).isEqualByComparingTo("800000");
        when(metadata.effectiveStandard("TGT_MRGN_RT", month))
            .thenReturn(new Standard("TGT_MRGN_RT", month, new BigDecimal("25")));
        assertThat(rules.automaticBudget(new BigDecimal("1000000"), month)).isEqualByComparingTo("750000");
        verify(metadata, times(2)).definition("식-00");
        verify(metadata, times(2)).definition("식-19");
    }

    @Test
    void nextReadAppliesChangedNumericExpressionWithoutUpdatingMetadata() {
        rules.automaticBudget(new BigDecimal("1000000"), month);
        when(metadata.definition("식-19")).thenReturn(new Definition("식-19",
            "예산 기본값 = 현재 계약금액 × (0.9 − 목표 마진율)", "변경 정의"));
        assertThat(rules.automaticBudget(new BigDecimal("1000000"), month)).isEqualByComparingTo("700000");
        verify(metadata, times(2)).definition("식-19");
    }

    @Test
    void unsupportedBudgetSyntaxFailsWithTheRuleId() {
        when(metadata.definition("식-19")).thenReturn(new Definition("식-19", "Runtime.getRuntime().exec('x')", ""));
        assertThatThrownBy(() -> rules.automaticBudget(BigDecimal.ONE, month))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("식-19");
    }

    @Test
    void invalidEffectiveMarginFailsInsteadOfUsingAConstant() {
        when(metadata.effectiveStandard("TGT_MRGN_RT", month))
            .thenReturn(new Standard("TGT_MRGN_RT", month, new BigDecimal("101")));
        assertThatThrownBy(() -> rules.automaticBudget(BigDecimal.ONE, month))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("TGT_MRGN_RT");
    }

    @Test
    void oneOwnManMonthReceivesBothFullAllowances() {
        var cost = rules.personnelDirectCost("FT", new BigDecimal("50000"), new BigDecimal("50000"), BigDecimal.ONE);
        assertThat(cost.teamAmount()).isEqualByComparingTo("50000");
        assertThat(cost.projectAmount()).isEqualByComparingTo("50000");
        assertThat(cost.total()).isEqualByComparingTo("100000");
    }

    @Test
    void ownAllowancesFollowPersonalManMonthsAndNotTheTeamManMonthTotal() {
        var cost = rules.personnelDirectCost("FL", new BigDecimal("50000"), new BigDecimal("50000"), new BigDecimal("0.25"));
        assertThat(cost.teamAmount()).isEqualByComparingTo("12500");
        assertThat(cost.projectAmount()).isEqualByComparingTo("12500");
    }

    @Test
    void outsourcedPersonnelReceiveNeitherAllowance() {
        var cost = rules.personnelDirectCost("OS", new BigDecimal("50000"), new BigDecimal("50000"), BigDecimal.ONE);
        assertThat(cost.total()).isZero();
        verify(metadata).definition("식-11");
    }

    @Test
    void additionalExpensesBelongToTheProjectEvenWithOnlyOutsourcedPersonnel() {
        var individual = rules.personnelDirectCost("OS", new BigDecimal("50000"), new BigDecimal("50000"), BigDecimal.ONE);
        assertThat(rules.projectDirectCost(individual.total(), new BigDecimal("12345"))).isEqualByComparingTo("12345");
        assertThat(individual.total()).isZero();
    }

    @Test
    void unsupportedDirectCostDefinitionDoesNotSilentlyUseOldSemantics() {
        when(metadata.definition("식-11")).thenReturn(new Definition("식-11", "다른 계산", ""));
        assertThatThrownBy(() -> rules.personnelDirectCost("FT", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("식-11");
    }

    @Test
    void discoversEveryDeclaredDefinitionIncludingCommonPrinciplesOnEveryCall() {
        when(metadata.definition(anyString())).thenAnswer(call -> new Definition(call.getArgument(0), "DB 정의", "상세"));
        rules.definitions();
        rules.definitions();
        for (String id : new String[]{"00", "04", "11", "16", "18", "19", "20", "24", "25",
                "53", "54", "55", "56", "67", "72", "73", "76"}) {
            verify(metadata, times(2)).definition("식-" + id);
        }
    }

    @Test
    void negativeManMonthsAreRejected() {
        assertThatThrownBy(() -> rules.personnelDirectCost("FT", BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("-1")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("projectMm");
    }

    @Test
    void unknownEmployeeCodeIsRejected() {
        assertThatThrownBy(() -> rules.personnelDirectCost("unknown", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("EMP_TYPE");
    }

    @Test
    void unsupportedBudgetSuffixDoesNotFallBackToTheRecognizedPrefix() {
        when(metadata.definition("식-19")).thenReturn(new Definition("식-19",
            "예산 기본값 = 현재 계약금액 × (1 − 목표 마진율) + 1000", ""));
        assertThatThrownBy(() -> rules.automaticBudget(BigDecimal.ONE, month))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("식-19");
    }
}
