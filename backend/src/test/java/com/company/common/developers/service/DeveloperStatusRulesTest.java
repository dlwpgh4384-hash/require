package com.company.common.developers.service;

import com.company.common.developers.dto.DeveloperContracts.StatusInput;
import com.company.common.developers.mapper.DeveloperRows.State;
import com.company.common.support.service.CodeFailure;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DeveloperStatusRulesTest {
    private final LocalDate day=LocalDate.of(2026,11,15);
    private State row(String code,int n,LocalDate start,LocalDate end) {var r=new State();r.code=code;r.sequence=n;r.startDate=start;r.endDate=end;return r;}
    @Test void endBeforeStartIsRejected() {assertThatThrownBy(() -> DeveloperStatusRules.validate(List.of(row("LEAVE",1,day,day.minusDays(1))))).isInstanceOf(CodeFailure.class);}
    @Test void overlappingLeavesAreRejected() {assertThatThrownBy(() -> DeveloperStatusRules.validate(List.of(row("LEAVE",1,day,null),row("LEAVE",2,day.plusDays(1),null)))).isInstanceOf(CodeFailure.class).hasMessageContaining("겹칩니다");}
    @Test void duplicateRetirementsAreRejected() {assertThatThrownBy(() -> DeveloperStatusRules.validate(List.of(row("RETIRE",1,day,null),row("RETIRE",2,day.plusDays(1),null)))).isInstanceOf(CodeFailure.class);}
    @Test void leaveAfterRetirementIsRejected() {assertThatThrownBy(() -> DeveloperStatusRules.validate(List.of(row("RETIRE",1,day,null),row("LEAVE",2,day.plusDays(1),null)))).isInstanceOf(CodeFailure.class);}
    @Test void leaveBeforeRetirementNeedsEndBeforeRetirement() {assertThatThrownBy(() -> DeveloperStatusRules.validate(List.of(row("LEAVE",1,day.minusDays(3),null),row("RETIRE",2,day,null)))).isInstanceOf(CodeFailure.class);}
    @Test void twoReturnsForSameLeaveAreRejected() {
        var leave=row("LEAVE",1,day.minusDays(1),day);var a=row("WORK",2,day.plusDays(1),null);a.closedLeaveSequence=1;
        var b=row("WORK",3,day.plusDays(1),null);b.closedLeaveSequence=1;
        assertThatThrownBy(() -> DeveloperStatusRules.validate(List.of(leave,a,b))).isInstanceOf(CodeFailure.class);
    }
    @Test void newLeaveCannotStartOnReturnDay() {
        var leave=row("LEAVE",1,day.minusDays(2),day.minusDays(1));var work=row("WORK",2,day,null);work.closedLeaveSequence=1;
        assertThatThrownBy(() -> DeveloperStatusRules.validate(List.of(leave,work,row("LEAVE",3,day,null)))).isInstanceOf(CodeFailure.class);
    }
    @Test void followingDayLeaveIsValidAndReturnBoundaryIsInclusive() {
        var rows=new ArrayList<State>();rows.add(row("LEAVE",1,day.minusDays(2),day.minusDays(1)));
        DeveloperStatusRules.append(rows,new StatusInput("재직",day,null,null),"dev","admin");
        DeveloperStatusRules.append(rows,new StatusInput("휴직",day.plusDays(1),null,null),"dev","admin");
        assertThat(rows).hasSize(3);
    }
    @Test void statusIdentifiersMustBeExactIntegersAndBelongToDeveloper() {
        var rows=List.of(row("LEAVE",1,day,null));
        assertThatThrownBy(() -> DeveloperStatusRules.target(rows,"1.1")).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> DeveloperStatusRules.target(rows,"2")).isInstanceOf(CodeFailure.class);
    }
    @Test void financeCannotCancelPastRetirementButAdminCan() {
        assertThat(DeveloperBusinessPolicy.canCancelRetirement(Set.of("FIN"),day.minusDays(1),day,false)).isFalse();
        assertThat(DeveloperBusinessPolicy.canCancelRetirement(Set.of("ADMIN"),day.minusDays(1),day,false)).isTrue();
        assertThat(DeveloperBusinessPolicy.canCancelRetirement(Set.of("ADMIN"),day.minusDays(1),day,true)).isFalse();
    }
    @Test void nonManagementIndirectIsRejectedByPolicy() {assertThat(DeveloperBusinessPolicy.validDirectClass("INDRCT",false)).isFalse();assertThat(DeveloperBusinessPolicy.validDirectClass("INDRCT",true)).isTrue();}
    @Test void returnCancellationTransfersRetirementLinkSoLaterRetirementCancellationRestoresOriginal() {
        var leave=row("LEAVE",1,day.minusDays(5),day.minusDays(1));
        var work=row("WORK",2,day,null);work.closedLeaveSequence=1;work.originalEndDate=null;
        var retirement=row("RETIRE",3,day.plusDays(5),null);
        var rows=new ArrayList<>(List.of(leave,work,retirement));
        DeveloperStatusRules.cancel(rows,work);
        assertThat(leave.endDate).isEqualTo(day.plusDays(4));assertThat(retirement.closedLeaveSequence).isEqualTo(1);
        DeveloperStatusRules.cancel(rows,retirement);assertThat(leave.endDate).isNull();
    }
}
