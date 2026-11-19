package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.dto.CalculationRuleContracts.Standard;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.BillingCalculationRules.MonthlyInput;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BillingCalculationRulesTest {
    private CalculationRuleService metadata;
    private BillingCalculationRules rules;
    private final YearMonth month = YearMonth.of(2026, 11);
    private final LocalDate due = LocalDate.of(2026, 11, 1);

    @BeforeEach
    void setup() {
        metadata = mock(CalculationRuleService.class);
        rules = new BillingCalculationRules(metadata);
        when(metadata.definition(anyString())).thenAnswer(call ->
            new Definition(call.getArgument(0), "독립 정의", "DB 조회 경계 시험"));
        standard("BILL_MISS_MIN", "10000");
        standard("BILL_MISS_RT", "0.5");
        standard("DUN_D1", "7");
        standard("DUN_D2", "30");
        standard("DUN_D3", "60");
    }

    @Test
    void requestedDefinitionsAreReadByExactCalcIdWithoutExecutingTheirText() {
        var ids = List.of("식-00", "식-01", "식-02", "식-04", "식-05", "식-06", "식-07",
            "식-09", "식-10", "식-47", "식-57", "식-58", "식-88", "식-89");
        assertThat(rules.definitions()).extracting(Definition::calcId).containsExactlyElementsOf(ids);
        ids.forEach(id -> verify(metadata).definition(id));
        verifyNoMoreInteractions(metadata);
    }

    @Test
    void missingDefinitionIsNotReplacedByASeedOrFallback() {
        when(metadata.definition("식-05")).thenThrow(new IllegalStateException("식-05 없음"));
        assertThatThrownBy(() -> rules.amounts(new BigDecimal("100"), BigDecimal.TEN))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("식-05");
    }

    @Test
    void taxIsFlooredInWholeWonWithoutRoundingUserSupply() {
        var result = rules.amounts(new BigDecimal("101"), new BigDecimal("7.25"));
        assertThat(result.supplyAmount()).isEqualByComparingTo("101");
        assertThat(result.vatAmount()).isEqualByComparingTo("7");
        assertThat(result.totalAmount()).isEqualByComparingTo("108");
        verify(metadata).definition("식-00");
        verify(metadata).definition("식-05");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.5", "1000000000000000"})
    void invalidSupplyIsRejectedRatherThanRoundedOrStored(String amount) {
        assertThatThrownBy(() -> rules.amounts(new BigDecimal(amount), BigDecimal.TEN))
            .isInstanceOfSatisfying(CodeFailure.class, error -> {
                assertThat(error.getField()).isEqualTo("supplyAmount");
                assertThat(error.getStatus()).isEqualTo(400);
            });
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "100.01", "1.001"})
    void invalidRateIsRejected(String rate) {
        assertThatThrownBy(() -> rules.amounts(BigDecimal.ONE, new BigDecimal(rate)))
            .isInstanceOfSatisfying(CodeFailure.class, error -> assertThat(error.getField()).isEqualTo("vatRate"));
    }

    @Test
    void zeroAndHundredPercentAreValidRates() {
        assertThat(rules.amounts(BigDecimal.TEN, BigDecimal.ZERO).vatAmount()).isZero();
        assertThat(rules.amounts(BigDecimal.TEN, new BigDecimal("100")).totalAmount()).isEqualByComparingTo("20");
    }

    @Test
    void computedTotalMustAlsoFitPhysicalPrecision() {
        assertThatThrownBy(() -> rules.amounts(new BigDecimal("999999999999999"), BigDecimal.TEN))
            .isInstanceOfSatisfying(CodeFailure.class, error -> assertThat(error.getField()).isEqualTo("totalAmount"));
    }

    @Test
    void dispatchUsesPersistedPartialMonthlyMmAndExcludesFreeAndTurnkey() {
        var result = rules.estimate(List.of(
            new MonthlyInput("DP", "NEW", new BigDecimal("0.6"), new BigDecimal("1000000")),
            new MonthlyInput("MO", "ADD", new BigDecimal("0.33"), new BigDecimal("101")),
            new MonthlyInput("MO", "FREE", BigDecimal.ONE, new BigDecimal("999999")),
            new MonthlyInput("TK", "NEW", BigDecimal.ONE, new BigDecimal("999999"))));
        assertThat(result).isEqualByComparingTo("600033");
        verify(metadata).definition("식-02");
    }

    @Test
    void absentMonthlyPriceDoesNotBecomeInventedZeroRevenue() {
        assertThatThrownBy(() -> rules.estimate(List.of(new MonthlyInput("MO", "NEW", BigDecimal.ONE, null))))
            .isInstanceOfSatisfying(CodeFailure.class, error -> assertThat(error.getField()).isEqualTo("billingPrice"));
    }

    @Test
    void normalReceiptSumChangesTheBalanceAndCancellationRestoresIt() {
        assertThat(rules.balance(new BigDecimal("1100"), new BigDecimal("300"))).isEqualByComparingTo("800");
        assertThat(rules.balance(new BigDecimal("1100"), new BigDecimal("1100"))).isZero();
        assertThat(rules.balance(new BigDecimal("1100"), BigDecimal.ZERO)).isEqualByComparingTo("1100");
    }

    @Test
    void overpaymentIsRejected() {
        assertThatThrownBy(() -> rules.balance(BigDecimal.ONE, BigDecimal.TEN)).isInstanceOf(CodeFailure.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.01", "1000000000000000"})
    void invalidReceiptAmountsAreRejected(String amount) {
        assertThatThrownBy(() -> BillingCalculationRules.positiveMoney(new BigDecimal(amount), "items.amount"))
            .isInstanceOfSatisfying(CodeFailure.class, error -> assertThat(error.getField()).isEqualTo("items.amount"));
    }

    @Test
    void zeroBalanceIsPaidEvenAfterTheDueDate() {
        assertThat(rules.status(false, BigDecimal.ZERO, due, due.plusDays(60))).isEqualTo("PAID");
    }

    @Test
    void dueDateItselfIsUnpaidNotLate() {
        assertThat(rules.status(false, BigDecimal.ONE, due, due)).isEqualTo("UNPAID");
    }

    @Test
    void nextKoreanDayIsLateBeforeTheFirstNotification() {
        var calendar = new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-01T15:00:00Z"), ZoneOffset.UTC));
        assertThat(rules.status(false, BigDecimal.ONE, due, calendar.today())).isEqualTo("LATE");
        assertThat(rules.dunningStep(false, BigDecimal.ONE, due, calendar.today())).isNull();
    }

    @Test
    void cancellationTakesPrecedenceOverPaidAndOverdueStates() {
        assertThat(rules.status(true, BigDecimal.ZERO, due, due)).isEqualTo("CNCL");
        assertThat(rules.status(true, BigDecimal.ONE, due, due.plusDays(60))).isEqualTo("CNCL");
    }

    @Test
    void zeroBillIsMissingEvenWhenTheDifferenceFallsBelowTheThreshold() {
        assertThat(rules.check(new BigDecimal("100"), BigDecimal.ZERO, month).judgement()).isEqualTo("청구 없음");
    }

    @Test
    void minimumThresholdEqualityIsMatched() {
        var result = rules.check(new BigDecimal("1000000"), new BigDecimal("990000"), month);
        assertThat(result.threshold()).isEqualByComparingTo("10000");
        assertThat(result.judgement()).isEqualTo("맞음");
    }

    @Test
    void percentageDenominatorIsReferenceNotBilledAmountAndEqualityIsMatched() {
        var result = rules.check(new BigDecimal("10000000"), new BigDecimal("9950000"), month);
        assertThat(result.threshold()).isEqualByComparingTo("50000");
        assertThat(result.judgement()).isEqualTo("맞음");
    }

    @Test
    void strictlyOverThresholdIsUnderBilled() {
        assertThat(rules.check(new BigDecimal("10000000"), new BigDecimal("9949999"), month).judgement())
            .isEqualTo("적게 청구");
    }

    @Test
    void strictlyOverThresholdInOppositeDirectionIsOverBilled() {
        assertThat(rules.check(new BigDecimal("10000000"), new BigDecimal("10050001"), month).judgement())
            .isEqualTo("많이 청구");
    }

    @Test
    void changedEffectiveThresholdIsReadOnNextCheck() {
        standard("BILL_MISS_MIN", "20000");
        assertThat(rules.check(new BigDecimal("1000000"), new BigDecimal("980000"), month).judgement())
            .isEqualTo("맞음");
        verify(metadata).effectiveStandard("BILL_MISS_MIN", month);
        verify(metadata).effectiveStandard("BILL_MISS_RT", month);
        verify(metadata).definition("식-88");
    }

    @Test
    void notificationUsesCurrentStageWithoutReplayingEarlierStages() {
        assertThat(rules.dunningStep(false, BigDecimal.ONE, due, due.plusDays(7))).isEqualTo(1);
        assertThat(rules.dunningStep(false, BigDecimal.ONE, due.minusDays(30), due)).isEqualTo(2);
        assertThat(rules.dunningStep(false, BigDecimal.ONE, due.minusDays(60), due)).isEqualTo(3);
    }

    @Test
    void paidAndCancelledBillsHaveNoNotificationStage() {
        assertThat(rules.dunningStep(false, BigDecimal.ZERO, due, due.plusDays(60))).isNull();
        assertThat(rules.dunningStep(true, BigDecimal.ONE, due, due.plusDays(60))).isNull();
    }

    @Test
    void effectiveNotificationDaysAreNotHardcoded() {
        standard("DUN_D1", "9");
        assertThat(rules.dunningStep(false, BigDecimal.ONE, due, due.plusDays(7))).isNull();
        assertThat(rules.dunningStep(false, BigDecimal.ONE, due, due.plusDays(9))).isEqualTo(1);
    }

    @Test
    void fractionalNotificationDayIsRejected() {
        standard("DUN_D1", "7.5");
        assertThatThrownBy(() -> rules.dunningStep(false, BigDecimal.ONE, due, due))
            .isInstanceOf(CodeFailure.class);
    }

    @Test
    void unorderedNotificationDaysAreRejected() {
        standard("DUN_D2", "6");
        assertThatThrownBy(() -> rules.dunningStep(false, BigDecimal.ONE, due, due))
            .isInstanceOf(CodeFailure.class);
    }

    private void standard(String code, String value) {
        when(metadata.effectiveStandard(code, month)).thenReturn(new Standard(code, month, new BigDecimal(value)));
    }
}
