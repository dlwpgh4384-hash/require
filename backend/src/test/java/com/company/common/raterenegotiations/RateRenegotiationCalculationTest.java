package com.company.common.raterenegotiations;

import com.company.common.raterenegotiations.dto.RateRenegotiationContracts.Filter;
import com.company.common.raterenegotiations.mapper.RateRenegotiationRows.AssignmentInfo;
import com.company.common.raterenegotiations.service.RateRenegotiationRules;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class RateRenegotiationCalculationTest {
    static BigDecimal n(String value) { return new BigDecimal(value); }
    static final LocalDate TODAY = LocalDate.of(2026, 2, 1);
    static AssignmentInfo info(String id, int days) {
        return new AssignmentInfo(id, "D", "김개발", "CT", "계약직", "P", "프로젝트",
            TODAY.plusDays(days), n("1"), n("1000000"), "ACT", "NEW", "MO", "ING", "M-1");
    }
    static Filter filter() { return new Filter(null, null, null, null, null); }

    @Test void partialMonthlyMmDoesNotProrateAnnualContractMmAndTargetRoundsUpToTenThousand() {
        var row = RateRenegotiationRules.row(info("A", 30), n("0.5"), n("460001"),
            n("20"), n("5"), TODAY);
        assertThat(row.targetUnitPrice()).isEqualByComparingTo("1160000");
        assertThat(row.requiredIncreaseRate()).isEqualByComparingTo("16.0");
        assertThat(row.annualProfitIncrease()).isEqualByComparingTo("600000");
        assertThat(RateRenegotiationRules.targetAnnual(row, n("1"))).isEqualByComparingTo("1920000");
    }
    @ParameterizedTest
    @CsvSource({"-1,0", "0,0", "5,5", "50,50", "51,50"})
    void clampsInclusiveAssumption(String input, String expected) {
        var f = RateRenegotiationRules.normalize(new Filter(null, null, null, null, n(input)));
        assertThat(f.increaseRate()).isEqualByComparingTo(expected);
        assertThat(f.renewalMonths()).isEqualTo(6);
        assertThat(f.profitRateThreshold()).isEqualByComparingTo("15");
    }
    @ParameterizedTest
    @CsvSource({"3,92,true", "3,93,false", "6,183,true", "6,184,false", "12,366,true", "12,367,false", "6,0,true", "6,-1,false"})
    void renewalWindowsAreInclusiveDayCounts(int months, int days, boolean expected) {
        var f = RateRenegotiationRules.normalize(new Filter(months, null, null, null, null));
        assertThat(RateRenegotiationRules.eligible(info("A", days), n("1"), f, TODAY)).isEqualTo(expected);
    }
    @Test void exactThresholdIsExcludedButAnUnroundedValueJustBelowItIsIncluded() {
        assertThat(RateRenegotiationRules.belowThreshold(n("1000000"), n("850000"), n("15"))).isFalse();
        assertThat(RateRenegotiationRules.belowThreshold(n("1000000"), n("850001"), n("15"))).isTrue();
    }
    @Test void annualMoneyIsTruncatedTowardZeroBeforeSummation() {
        var row = RateRenegotiationRules.row(info("A", 1), n("1"), n("900000"),
            n("20"), n("0.000009"), TODAY);
        assertThat(row.annualProfitIncrease()).isEqualByComparingTo("1");
    }
}
