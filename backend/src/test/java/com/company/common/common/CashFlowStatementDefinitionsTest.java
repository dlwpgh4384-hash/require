package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.mapper.CalculationRuleRow;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Code;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CashFlowStatementDefinitionsTest {
    private final CalculationRuleMapper mapper = mock(CalculationRuleMapper.class);
    private final UserPreferencePeriodSourceMapper periods = mock(UserPreferencePeriodSourceMapper.class);
    private final ProfitStructureMapper source = mock(ProfitStructureMapper.class);
    private final CashFlowStatementDefinitions definitions = new CashFlowStatementDefinitions(
        new CalculationRuleService(mapper, periods), source);
    private static final List<String> IDS = List.of(
        "식-00", "식-05", "식-06", "식-44", "식-45", "식-46", "식-47",
        "식-48", "식-49", "식-50", "식-51", "식-52");
    private static final String PAYMENT = "외주 지급액 = 공급가액 + 부가세(10%). "
        + "프리랜서 공급가액 = 그 달 급여(급여 업로드 지급액) + 수당(프리랜서는 0, 식-08). "
        + "프리랜서 지급액 = 공급가액 − 원천세(공급가액 × 3.3%). 모두 원 단위 버림";

    @BeforeEach void setup() {
        when(mapper.selectDefinition(anyString())).thenAnswer(call -> row(
            call.getArgument(0), "DB 식 " + call.getArgument(0), "DB 상세"));
    }

    private static CalculationRuleRow row(String id, String formula, String details) {
        var row = new CalculationRuleRow();
        row.calcId = id;
        row.formula = formula;
        row.details = details;
        return row;
    }

    private void payments(String formula) {
        when(mapper.selectDefinition("식-00")).thenReturn(row("식-00",
            "(수식 없음, 모든 식에 적용하는 원칙)", "금액은 원 단위 정수 저장"));
        when(mapper.selectDefinition("식-49")).thenReturn(row("식-49", formula, "DB 상세"));
        when(source.codes()).thenReturn(List.of(
            new Code("EMP_TYPE", "FT", "정규직", null),
            new Code("SGA_ITEM", "RENT", "임대료", null),
            new Code("FIN_CLS", "NOPR_IN", "영업외수익", null),
            new Code("DRCT_CLS", "DRCT", "직접", null),
            new Code("PAY_TYPE", "CNFM", "확정 신고", null),
            new Code("IO", "IN", "받음", null),
            new Code("XFER_CLS", "SPCL", "특별비(미지급 인건비)", null)));
    }

    @Test void readsAllTwelveRequiredDefinitionsInOriginalIdentityOrder() {
        var result = definitions.definitions();
        assertThat(result).extracting(Definition::calcId).containsExactlyElementsOf(IDS);
        for (var definition : result) {
            assertThat(definition.formula()).isEqualTo("DB 식 " + definition.calcId());
            assertThat(definition.details()).isEqualTo("DB 상세");
            verify(mapper).selectDefinition(definition.calcId());
        }
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(source, periods);
    }

    @Test void partSettlementAndHistoryDefinitionsAreNotReinterpretedAsCashFormulas() {
        var settlement = row("식-44", "파트 이익금 = Σ 인력별 이익금 − 법인카드 사용액", "DB 파트정산");
        var history = row("식-45", "그 달의 적용 값 = 기준 이력 중 적용 시작월 ≤ 그 달 ≤ 적용 종료월인 줄의 값", "DB 이력");
        when(mapper.selectDefinition("식-44")).thenReturn(settlement);
        when(mapper.selectDefinition("식-45")).thenReturn(history);
        assertThat(definitions.definitions()).contains(settlement.value(), history.value());
    }

    @Test void definitionChangesAreVisibleOnTheNextReadWithoutExecutingText() {
        var before = definitions.definitions();
        var changed = row("식-46", "DB 변경 설명 × ÷ −", "임의 SQL을 실행하지 않는 자연어");
        when(mapper.selectDefinition("식-46")).thenReturn(changed);
        assertThat(definitions.definitions()).contains(changed.value());
        assertThat(before).doesNotContain(changed.value());
    }

    @ParameterizedTest @ValueSource(strings = {
        "식-00", "식-05", "식-06", "식-44", "식-45", "식-46", "식-47",
        "식-48", "식-49", "식-50", "식-51", "식-52"})
    void missingDefinitionUsesExistingConfigurationErrorWithoutFallback(String id) {
        when(mapper.selectDefinition(id)).thenReturn(null);
        assertThatThrownBy(definitions::definitions).isInstanceOfSatisfying(CodeFailure.class, error -> {
            assertThat(error.getStatus()).isEqualTo(400);
            assertThat(error.getField()).isEqualTo("calcId");
        });
        verifyNoInteractions(source, periods);
    }

    @Test void sevenCodeGroupsPreserveDatabaseValuesNamesAndOrderingWithoutEnumsOrWrites() {
        var groups = List.of("EMP_TYPE", "SGA_ITEM", "FIN_CLS", "DRCT_CLS", "PAY_TYPE", "IO", "XFER_CLS");
        var rows = groups.stream().map(group -> new Code(group, "DB_VALUE", "DB 표시 " + group,
            BigDecimal.TEN)).toList();
        var unrelated = new Code("CTRT_TYPE", "MO", "월단위", null);
        var all = new java.util.ArrayList<>(rows);
        all.add(unrelated);
        when(source.codes()).thenReturn(all);
        assertThat(definitions.codes()).containsExactlyElementsOf(rows);
        verify(source).codes();
        verifyNoMoreInteractions(source);
        verifyNoInteractions(mapper, periods);
    }

    @Test void renamedCodesAndEmptySourcesAreReadAgainRatherThanSeeded() {
        var changed = new Code("EMP_TYPE", "FT", "DB 변경된 이름", null);
        when(source.codes()).thenReturn(List.of(changed)).thenReturn(List.of());
        assertThat(definitions.codes()).containsExactly(changed);
        assertThat(definitions.codes()).isEmpty();
        verify(source, times(2)).codes();
    }

    @Test void formulaParametersChangeActualPaymentResultsOnTheNextRead() {
        payments(PAYMENT);
        var original = definitions.paymentRules();
        assertThat(original.freelancerPayment(new BigDecimal("5000000"), BigDecimal.ZERO))
            .isEqualByComparingTo("4835000");
        assertThat(original.vendorPayment(new BigDecimal("8000000"))).isEqualByComparingTo("8800000");
        payments(PAYMENT.replace("3.3%", "4%").replace("10%", "12%"));
        var changed = definitions.paymentRules();
        assertThat(changed.freelancerPayment(new BigDecimal("5000000"), BigDecimal.ZERO))
            .isEqualByComparingTo("4800000");
        assertThat(changed.vendorPayment(new BigDecimal("8000000"))).isEqualByComparingTo("8960000");
        assertThat(original.freelancerWithholdingRate()).isEqualByComparingTo("0.033");
    }

    @ParameterizedTest @ValueSource(strings = {
        "EMP_TYPE", "SGA_ITEM", "FIN_CLS", "DRCT_CLS", "PAY_TYPE", "IO", "XFER_CLS"})
    void reportPaymentRulesRejectEachMissingDatabaseCodeGroup(String missing) {
        payments(PAYMENT);
        var complete = source.codes();
        clearInvocations(source);
        when(source.codes()).thenReturn(complete.stream()
            .filter(code -> !missing.equals(code.groupCode())).toList());
        assertThatThrownBy(definitions::paymentRules).isInstanceOfSatisfying(CodeFailure.class, error -> {
            assertThat(error.getStatus()).isEqualTo(400);
            assertThat(error.getField()).isEqualTo("code");
        });
        verify(source).codes();
        verifyNoMoreInteractions(source);
    }

    @Test void paymentRulesReadCodesAgainOnEverySnapshotWithoutSeedingMissingGroups() {
        payments(PAYMENT);
        var complete = source.codes();
        clearInvocations(source);
        when(source.codes()).thenReturn(complete).thenReturn(List.of());
        assertThat(definitions.paymentRules().vendorVatRate()).isEqualByComparingTo("0.10");
        assertThatThrownBy(definitions::paymentRules).isInstanceOfSatisfying(CodeFailure.class,
            error -> assertThat(error.getField()).isEqualTo("code"));
        verify(source, times(2)).codes();
        verifyNoMoreInteractions(source);
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "\n\t"})
    void blankDefinitionIsNotAnAlternativeFinancialFormula(String formula) {
        when(mapper.selectDefinition("식-44")).thenReturn(row("식-44", formula, "DB 상세"));
        assertThatThrownBy(definitions::definitions).isInstanceOfSatisfying(CodeFailure.class, error -> {
            assertThat(error.getField()).isEqualTo("calcId");
            assertThat(error.getMessage()).contains("식-44");
        });
        verifyNoInteractions(source, periods);
    }

    @Test void missingWonStoragePrincipleNamesItsActualDefinition() {
        payments(PAYMENT);
        when(mapper.selectDefinition("식-00")).thenReturn(row("식-00", "공통 원칙", "금액 반올림 저장"));
        assertThatThrownBy(definitions::paymentRules).isInstanceOfSatisfying(CodeFailure.class, error -> {
            assertThat(error.getField()).isEqualTo("calcId");
            assertThat(error.getMessage()).contains("식-00");
        });
    }

    @Test void whitespaceEditsAndUnrelatedDetailProseDoNotChangeSupportedPaymentSemantics() {
        payments(PAYMENT.replace(" ", " \n\t"));
        var rules = definitions.paymentRules();
        assertThat(rules.freelancerPayment(new BigDecimal("101"), new BigDecimal("10")))
            .isEqualByComparingTo("108");
        assertThat(rules.vendorPayment(new BigDecimal("101"))).isEqualByComparingTo("111");
    }

    @ParameterizedTest @ValueSource(strings = {"모두 원 단위 반올림", "모두 원 단위 올림"})
    void unsupportedRoundingFailsInsteadOfSilentlyApplyingOldRules(String rounding) {
        payments(PAYMENT.replace("모두 원 단위 버림", rounding));
        assertConfigurationFailure();
    }

    @ParameterizedTest @ValueSource(strings = {"101%", "-3.3%", "세율%"})
    void invalidPercentageFailsInsteadOfInventingAWithholdingRate(String percentage) {
        payments(PAYMENT.replace("3.3%", percentage));
        assertConfigurationFailure();
    }

    @Test void unsupportedPaymentOperatorIsNotEvaluatedOrIgnored() {
        payments(PAYMENT.replace("공급가액 − 원천세", "공급가액 + 원천세"));
        assertConfigurationFailure();
    }

    @Test void additionalArithmeticCannotBeSilentlyDiscardedAfterASupportedExpression() {
        payments(PAYMENT.replace("부가세(10%).", "부가세(10%) + 100."));
        assertConfigurationFailure();
    }

    @Test void explanatoryParenthesesAreNotComparedByteForByte() {
        payments(PAYMENT.replace("급여 업로드 지급액", "저장된 월 급여")
            .replace("프리랜서는 0, 식-08", "현재 지급 수당 기준"));
        assertThat(definitions.paymentRules().freelancerWithholdingRate()).isEqualByComparingTo("0.033");
    }

    private void assertConfigurationFailure() {
        assertThatThrownBy(definitions::paymentRules).isInstanceOfSatisfying(CodeFailure.class, error -> {
            assertThat(error.getStatus()).isEqualTo(400);
            assertThat(error.getField()).isEqualTo("calcId");
            assertThat(error.getMessage()).contains("식-49");
        });
    }

    @ParameterizedTest @ValueSource(strings = {"definitions", "codes", "paymentRules"})
    void standaloneReadsDeclareReadOnlyRepeatableRead(String method) throws Exception {
        var transaction = CashFlowStatementDefinitions.class.getMethod(method).getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
