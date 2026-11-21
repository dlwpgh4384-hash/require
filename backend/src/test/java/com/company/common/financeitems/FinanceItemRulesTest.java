package com.company.common.financeitems;

import com.company.common.financeitems.dto.FinanceItemContracts.*;
import com.company.common.financeitems.service.FinanceItemRules;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FinanceItemRulesTest {
    static BigDecimal n(String value) { return new BigDecimal(value); }
    @Test void annualExampleDividesIntoIndependentMonthlyAmounts() {
        var tax=FinanceItemRules.allocation(2026,n("120000000"),Set.of(),key->BigDecimal.ZERO); assertThat(tax).hasSize(12); tax.values().forEach(value->assertThat(value).isEqualByComparingTo("10000000"));
    }
    @Test void closedAmountsStayFixedAndNegativeRemainderTruncatesTowardZero() {
        var fixed=new LinkedHashMap<String,BigDecimal>(); for(int month=1;month<=9;month++)fixed.put(String.format("2026%02d",month),n("10000000"));
        var tax=FinanceItemRules.allocation(2026,n("50000000"),fixed.keySet(),fixed::get); assertThat(tax.get("202601")).isEqualByComparingTo("10000000"); assertThat(tax.get("202610")).isEqualByComparingTo("-13333333"); assertThat(tax.get("202612")).isEqualByComparingTo("-13333334"); assertThat(tax.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("50000000");
    }
    @Test void residualUsesLastOpenMonthWhenDecemberIsClosed() {
        var tax=FinanceItemRules.allocation(2026,n("120000005"),Set.of("202612"),key->n("10000000")); assertThat(tax.get("202611")).isEqualByComparingTo("10000005"); assertThat(tax.get("202612")).isEqualByComparingTo("10000000");
    }
    @Test void noOpenMonthDoesNotRedistributeClosedResults() {
        var fixed=new LinkedHashMap<String,BigDecimal>(); for(int month=1;month<=12;month++)fixed.put(String.format("2026%02d",month),n("10000000")); assertThat(FinanceItemRules.allocation(2026,n("1"),fixed.keySet(),fixed::get)).isEqualTo(fixed);
    }
    @Test void copyClampsDayAndResetsIdentityAndSource() {
        var source=new Item("9",Instant.EPOCH,"SGA",null,"임대료",n("10"),BigDecimal.ZERO,LocalDate.of(2026,1,31),"Y","N","22",null);
        var copy=FinanceItemRules.copy(source,YearMonth.of(2026,2),false); assertThat(copy.cashDate()).isEqualTo(LocalDate.of(2026,2,28)); assertThat(copy.itemId()).isNull(); assertThat(copy.uploadId()).isNull(); assertThat(copy.modifiedAt()).isNull(); assertThat(copy.autoFillYn()).isEqualTo("N"); assertThat(FinanceItemRules.copy(source,YearMonth.of(2026,2),true).autoFillYn()).isEqualTo("Y"); assertThat(source.itemId()).isEqualTo("9");
    }
    @Test void monthBoundaryRejectsDashAndInvalidCalendarMonth() {
        assertThatThrownBy(()->FinanceItemRules.month("2026-01")).isInstanceOf(CodeFailure.class); assertThatThrownBy(()->FinanceItemRules.month("202613")).isInstanceOf(CodeFailure.class); assertThat(FinanceItemRules.month("202602")).isEqualTo(YearMonth.of(2026,2));
    }
    @Test void amountZeroIsValidButBlankAndFractionHaveDistinctErrors() {
        FinanceItemRules.amount(BigDecimal.ZERO,"amount"); assertThatThrownBy(()->FinanceItemRules.amount(null,"amount")).isInstanceOfSatisfying(CodeFailure.class,error->assertThat(error.getField()).isEqualTo("amount")); assertThatThrownBy(()->FinanceItemRules.amount(n("0.5"),"amount")).isInstanceOf(CodeFailure.class);
    }
    @Test void requiredTextAllowsExactlyThreeHundredCharacters() { FinanceItemRules.text("가".repeat(300),"reason",300,true); assertThatThrownBy(()->FinanceItemRules.text("가".repeat(301),"reason",300,true)).isInstanceOf(CodeFailure.class); }
}
