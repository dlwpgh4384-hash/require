package com.company.common.common;

import com.company.common.developers.service.DeveloperBusinessPolicy;
import com.company.common.developers.service.DeveloperBusinessPolicy.Actor;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.users.service.UserScopePolicy;
import com.company.common.users.service.UserScopePolicy.Grant;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class DeveloperBusinessPolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    private Actor all(String role) {
        return new Actor(Set.of(role), UserScopePolicy.union(List.of(new Grant("ALL", Set.of(), Set.of()))));
    }

    private Actor leader() {
        return new Actor(Set.of("SALES_LDR"),
            UserScopePolicy.union(List.of(new Grant("PART", Set.of("owned-part"), Set.of()))));
    }

    @Test void currentAnonymousActorHasAllDeveloperCapabilities() {
        var current = new CurrentBusinessActor();
        var actor = new Actor(current.roles(), all("ADMIN").scope());
        assertThat(current.userId()).isEqualTo("admin");
        assertThat(DeveloperBusinessPolicy.canReadDeveloper(actor, "first-part")).isTrue();
        assertThat(DeveloperBusinessPolicy.canReadDeveloper(actor, "other-part")).isTrue();
        assertThat(DeveloperBusinessPolicy.canReadMonthlyRate(actor, "other-part")).isTrue();
        assertThat(DeveloperBusinessPolicy.canReadMonthlyPay(actor, "other-part")).isTrue();
        assertThat(DeveloperBusinessPolicy.canUploadPayroll(actor.roles())).isTrue();
    }

    @Test void leaderOnlyReadsOwnPartDevelopers() {
        assertThat(DeveloperBusinessPolicy.canReadDeveloper(leader(), "owned-part")).isTrue();
        assertThat(DeveloperBusinessPolicy.canReadDeveloper(leader(), "other-part")).isFalse();
        assertThat(DeveloperBusinessPolicy.canReadDeveloper(leader(), null)).isFalse();
    }

    @Test void leaderOnlyReadsOwnPartRateAndNeverPersonalPay() {
        assertThat(DeveloperBusinessPolicy.canReadMonthlyRate(leader(), "owned-part")).isTrue();
        assertThat(DeveloperBusinessPolicy.canReadMonthlyRate(leader(), "other-part")).isFalse();
        assertThat(DeveloperBusinessPolicy.canReadMonthlyPay(leader(), "owned-part")).isFalse();
        assertThat(DeveloperBusinessPolicy.canReadMonthlyPay(leader(), "other-part")).isFalse();
    }

    @Test void projectManagerCannotReadRateOrPayEvenInAnExplicitAllScopeFixture() {
        assertThat(DeveloperBusinessPolicy.canReadMonthlyRate(all("PM"), "owned-part")).isFalse();
        assertThat(DeveloperBusinessPolicy.canReadMonthlyPay(all("PM"), "owned-part")).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "EXEC", "FIN"})
    void sensitiveBusinessRolesSeeRateAndPay(String role) {
        assertThat(DeveloperBusinessPolicy.canReadMonthlyRate(all(role), "owned-part")).isTrue();
        assertThat(DeveloperBusinessPolicy.canReadMonthlyPay(all(role), "owned-part")).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "FIN"})
    void onlyStatusOperatorsCanRegister(String role) {
        assertThat(DeveloperBusinessPolicy.canManageStatuses(Set.of(role))).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"EXEC", "SALES_LDR", "PM"})
    void otherRolesCannotManageStatuses(String role) {
        assertThat(DeveloperBusinessPolicy.canManageStatuses(Set.of(role))).isFalse();
    }

    @Test void financeMayCancelFutureRetirement() {
        assertThat(DeveloperBusinessPolicy.canCancelRetirement(Set.of("FIN"), TODAY.plusDays(1), TODAY, false))
            .isTrue();
    }

    @Test void financeMayCancelOnRetirementDay() {
        assertThat(DeveloperBusinessPolicy.canCancelRetirement(Set.of("FIN"), TODAY, TODAY, false)).isTrue();
    }

    @Test void financeCannotCancelPastRetirement() {
        assertThat(DeveloperBusinessPolicy.canCancelRetirement(Set.of("FIN"), TODAY.minusDays(1), TODAY, false))
            .isFalse();
    }

    @Test void systemAdministratorMayCancelOpenPastRetirement() {
        assertThat(DeveloperBusinessPolicy.canCancelRetirement(Set.of("ADMIN"), TODAY.minusDays(1), TODAY, false))
            .isTrue();
    }

    @Test void closedMonthBlocksEvenSystemAdministrator() {
        assertThat(DeveloperBusinessPolicy.canCancelRetirement(Set.of("ADMIN"), TODAY.minusDays(1), TODAY, true))
            .isFalse();
    }

    @Test void payrollUsesFinancePolicySeparateFromStatusPolicy() {
        assertThat(DeveloperBusinessPolicy.canUploadPayroll(Set.of("FIN"))).isTrue();
        assertThat(DeveloperBusinessPolicy.canUploadPayroll(Set.of("SALES_LDR"))).isFalse();
        assertThat(DeveloperBusinessPolicy.canUploadPayroll(Set.of("PM"))).isFalse();
    }

    @Test void retirementIsStrictlyAfterItsKoreanCalendarDay() {
        var before = new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-14T14:59:59Z"), ZoneOffset.UTC));
        var after = new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), ZoneOffset.UTC));
        var retirement = LocalDate.of(2026, 11, 14);
        assertThat(DeveloperBusinessPolicy.retired(retirement, before.today())).isFalse();
        assertThat(DeveloperBusinessPolicy.retired(retirement, after.today())).isTrue();
        assertThat(DeveloperBusinessPolicy.canRegisterStatus(Set.of("ADMIN"), retirement, after.today())).isFalse();
        assertThat(DeveloperBusinessPolicy.canRegisterStatus(Set.of("FIN"), retirement, before.today())).isTrue();
        assertThat(DeveloperBusinessPolicy.retired(null, after.today())).isFalse();
    }

    @Test void savedPriceMonthUsesExistingKoreanBusinessCalendar() {
        var calendar = new BusinessCalendar(Clock.fixed(Instant.parse("2026-12-31T15:00:00Z"), ZoneOffset.UTC));
        assertThat(calendar.currentMonth()).isEqualTo(YearMonth.of(2027, 1));
        assertThat(calendar.toDatabaseMonth(calendar.currentMonth())).isEqualTo("202701");
    }

    @Test void indirectClassificationRequiresExplicitManagementDeclaration() {
        assertThat(DeveloperBusinessPolicy.validDirectClass("INDRCT", false)).isFalse();
        assertThat(DeveloperBusinessPolicy.validDirectClass("INDRCT", true)).isTrue();
        assertThat(DeveloperBusinessPolicy.validDirectClass("DRCT", false)).isTrue();
        assertThat(DeveloperBusinessPolicy.validDirectClass("unknown", true)).isFalse();
    }

    @Test void everyDirectWorkforceConsumerExcludesIndirectStaffAndClassifiesTheirCostsSeparately() {
        assertThat(DeveloperBusinessPolicy.includedInDirectWorkforce("INDRCT")).isFalse();
        assertThat(DeveloperBusinessPolicy.includedInDirectWorkforce("DRCT")).isTrue();
        assertThat(DeveloperBusinessPolicy.includedInDirectWorkforce(null)).isFalse();
        assertThat(DeveloperBusinessPolicy.classifiedAsSellingAdministrativeCost("INDRCT")).isTrue();
        assertThat(DeveloperBusinessPolicy.classifiedAsSellingAdministrativeCost("DRCT")).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"MO", "DP"})
    void missingRateBlocksPmMonthlyAndDispatchAssignmentsEvenWithSubmittedRate(String contract) {
        assertThat(DeveloperBusinessPolicy.canAssignWithRate(Set.of("PM"), contract, null, BigDecimal.TEN)).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"SALES_LDR", "FIN"})
    void leaderAndFinanceMustExplicitlyEnterMissingMonthlyRate(String role) {
        assertThat(DeveloperBusinessPolicy.canAssignWithRate(Set.of(role), "MO", null, null)).isFalse();
        assertThat(DeveloperBusinessPolicy.canAssignWithRate(Set.of(role), "MO", null, BigDecimal.ZERO)).isTrue();
        assertThat(DeveloperBusinessPolicy.canAssignWithRate(Set.of(role), "DP", null, BigDecimal.TEN)).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"TK", "IN"})
    void turnkeyAndInternalDoNotRequireMonthlyRate(String contract) {
        assertThat(DeveloperBusinessPolicy.canAssignWithRate(Set.of("PM"), contract, null, null)).isTrue();
    }

    @Test void existingRateAllowsPmAssignment() {
        assertThat(DeveloperBusinessPolicy.canAssignWithRate(Set.of("PM"), "MO", BigDecimal.TEN, null)).isTrue();
    }

    @Test void missingSalaryOnlyDefaultsPartStartingCostToZeroWithWarning() {
        var result = DeveloperBusinessPolicy.startingSalary(null);
        assertThat(result.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.note()).isEqualTo("⚠ 월 급여 없음");
    }

    @Test void explicitSalaryIncludingZeroIsNotReportedMissing() {
        assertThat(DeveloperBusinessPolicy.startingSalary(BigDecimal.ZERO).note()).isNull();
        assertThat(DeveloperBusinessPolicy.startingSalary(BigDecimal.TEN).amount()).isEqualTo(BigDecimal.TEN);
    }
}
