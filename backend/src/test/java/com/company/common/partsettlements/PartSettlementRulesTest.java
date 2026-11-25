package com.company.common.partsettlements;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.PartSettlementDefinitions;
import com.company.common.partsettlements.mapper.PartSettlementRows.*;
import com.company.common.partsettlements.service.PartSettlementRules;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.service.CodeFailure;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static com.company.common.partsettlements.PartSettlementTestData.*;
import static com.company.common.partsettlements.service.PartSettlementRules.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartSettlementRulesTest {
    PartSettlementRules rules;
    final YearMonth month=YearMonth.of(2026,1);
    @BeforeEach void setup() throws Exception {
        var mapper=mock(CalculationRuleMapper.class);definitions(mapper);
        rules=new PartSettlementRules(new PartSettlementDefinitions(new CalculationRuleService(mapper,mock(UserPreferencePeriodSourceMapper.class))).definitions());
    }
    @Test void perAssignmentWonTruncationAndSalaryOnce() {
        var a=input("D","A");a.mm=n("0.77");a.price=n("6666666");
        var b=input("D","A");b.assignmentId="other";b.mm=n("0.47");b.price=n("1100000");
        var result=rules.calculate(part("A"),month,List.of(a,b),List.of()).get(0);
        assertThat(result.unitPrice()).isEqualByComparingTo("5650332");assertThat(result.salary()).isEqualByComparingTo("4000000");
        assertThat(result.projectNames()).containsExactly("프로젝트");assertThat(result.monthlyMm()).isEqualByComparingTo("1.24");
    }
    @Test void uploadedStartMonthStillProratesDeveloperPoolSalary() {
        var i=input("D","A");i.uploaded=true;i.payrollType="CT";i.salary=n("1234567");i.firstStart=LocalDate.of(2026,1,20);
        var result=rules.calculate(part("A"),month,List.of(i),List.of()).get(0);
        assertThat(result.employeeTypeCode()).isEqualTo("CT");assertThat(result.salary()).isEqualByComparingTo("1548387");
        assertThat(result.retirementAmount()).isZero();assertThat(result.notes()).containsExactly("공통관리비","시작 달 12/31일");
    }
    @Test void uploadedStartMonthWithoutPoolSalaryProratesUploadedPay() {
        var i=input("D","A");i.uploaded=true;i.payrollType="FT";i.salary=n("3100000");i.monthlySalary=null;i.firstStart=LocalDate.of(2026,1,16);
        var result=rules.calculate(part("A"),month,List.of(i),List.of()).get(0);
        assertThat(result.salary()).isEqualByComparingTo("1600000");assertThat(result.missingMonthlySalary()).isFalse();
    }
    @Test void startOnFirstDayIsNotProrated() {
        var i=input("D","A");i.firstStart=LocalDate.of(2026,1,1);
        var result=rules.calculate(part("A"),month,List.of(i),List.of()).get(0);
        assertThat(result.salary()).isEqualByComparingTo("4000000");assertThat(result.notes()).containsExactly("공통관리비+퇴직금");
    }
    @Test void notUploadedOutsideStartMonthWarns() {
        var result=rules.calculate(part("A"),month,List.of(input("D","A")),List.of()).get(0);
        assertThat(result.notes()).containsExactly("공통관리비+퇴직금",UPLOAD_PENDING);
    }
    @Test void startMonthWithoutAnySalaryMarksMissing() {
        var i=input("D","A");i.monthlySalary=null;i.firstStart=LocalDate.of(2026,1,20);
        var result=rules.calculate(part("A"),month,List.of(i),List.of()).get(0);
        assertThat(result.salary()).isZero();assertThat(result.missingMonthlySalary()).isTrue();
        assertThat(result.notes()).containsExactly("공통관리비+퇴직금","시작 달 12/31일",MISSING_SALARY);
    }
    @Test void vendorUsesSupplyAndNoManagementExpense() {
        var i=input("D","A");i.uploaded=true;i.payrollType="OS";i.vendorSupply=n("8000000");i.salary=n("1");i.firstStart=LocalDate.of(2026,1,16);
        var row=rules.calculate(part("A"),month,List.of(i),List.of()).get(0);
        assertThat(row.salary()).isEqualByComparingTo("8000000");assertThat(row.salesExpense()).isZero();assertThat(row.retirementAmount()).isZero();
        assertThat(row.notes()).isEmpty();
    }
    @Test void turnkeyWithoutAllocatedCostBeforeCloseWarns() {
        var i=input("D","A");i.contractType="TK";i.contractAmount=n("12000000");i.budget=n("10000000");
        var result=rules.calculate(part("A"),month,List.of(i),List.of()).get(0);
        assertThat(result.unitPrice()).isZero();assertThat(result.notes()).contains(TURNKEY_COST_PENDING);
    }
    @Test void unuploadedStartMonthProratesOnce() {
        var a=input("D","A");a.firstStart=LocalDate.of(2026,1,16);
        var b=input("D","A");b.firstStart=a.firstStart;b.projectId="other";b.projectName="다른 프로젝트";
        assertThat(rules.calculate(part("A"),month,List.of(a,b),List.of()).get(0).salary()).isEqualByComparingTo("2064516");
    }
    @Test void withdrawalDoesNotReduceFullMonthSalary() {
        var i=input("D","A");i.mm=n("0.25");
        assertThat(rules.calculate(part("A"),month,List.of(i),List.of()).get(0).salary()).isEqualByComparingTo("4000000");
    }
    @Test void internalExcludedFreeIncludedWithZeroRevenue() {
        var internal=input("I","A");internal.contractType="IN";
        var free=input("F","A");free.inputType="FREE";
        var rows=rules.calculate(part("A"),month,List.of(internal,free),List.of());
        assertThat(rows).hasSize(1);assertThat(rows.get(0).monthlyMm()).isEqualByComparingTo("1");assertThat(rows.get(0).unitPrice()).isZero();
    }
    @Test void zeroMmNeverProducesCostOnlyRow() {
        var i=input("D","A");i.mm=n("0");assertThat(rules.calculate(part("A"),month,List.of(i),List.of())).isEmpty();
    }
    @Test void negativeProfitShareTruncatesTowardZero() {
        var i=input("D","A");i.price=n("3500000");
        assertThat(rules.calculate(part("A"),month,List.of(i),List.of()).get(0).profitShare()).isEqualByComparingTo("-933333");
    }
    @Test void turnkeyAllocationIsNotMultipliedByAssignmentCount() {
        var a=input("D","A");a.contractType="TK";a.distribution=n("1234567");
        var b=input("D","A");b.contractType="TK";b.distribution=n("1234567");
        assertThat(rules.calculate(part("A"),month,List.of(a,b),List.of()).get(0).unitPrice()).isEqualByComparingTo("1234567");
    }
    @Test void turnkeyFallbackUsesOwnAllocatedCostAndContractBudgetRatio() {
        var i=input("D","A");i.contractType="TK";i.allocatedCost=n("3000000");i.contractAmount=n("12000000");i.budget=n("10000000");
        assertThat(rules.calculate(part("A"),month,List.of(i),List.of()).get(0).unitPrice()).isEqualByComparingTo("3600000");
    }
    @Test void zeroBudgetWarnsWithoutBlocking() {
        var i=input("D","A");i.contractType="TK";i.budget=n("0");
        var result=rules.calculate(part("A"),month,List.of(i),List.of()).get(0);
        assertThat(result.unitPrice()).isZero();assertThat(result.notes()).contains("턴키 예산 없음: 단가 0");snapshotCapacity(List.of(result));
    }
    @Test void projectNamesSortByAggregatedMmThenNameAndDeduplicate() {
        var a=input("D","A");a.projectName="나";a.mm=n("0.4");
        var b=input("D","A");b.projectName="나";b.mm=n("0.6");
        var c=input("D","A");c.projectId="Q";c.projectName="가";
        assertThat(rules.calculate(part("A"),month,List.of(a,b,c),List.of()).get(0).projectNames()).containsExactly("가","나");
    }
    @Test void ambiguousDelimiterIsRejectedBeforeSnapshot() {
        var i=input("D","A");i.projectName="가, 나";
        assertThatThrownBy(()->snapshotCapacity(rules.calculate(part("A"),month,List.of(i),List.of()))).isInstanceOf(CodeFailure.class);
    }
    @Test void monthEndMembershipRestoresBeforeEarliestSubsequentChangeWithTieId() {
        var i=input("D","B");var a=history("TB_HR_DEV","D","영업 파트","A","2026-02-01T01:00:00Z",1);
        var b=history("TB_HR_DEV","D","영업 파트","HQ","2026-02-01T01:00:00Z",2);
        assertThat(membership(i,month,List.of(part("A"),part("B"),part("HQ")),List.of(b,a)).partId()).isEqualTo("A");
    }
    @Test void retiredPersonUsesRetirementBoundary() {
        var i=input("D","B");i.retireDate=LocalDate.of(2026,1,15);
        var h=history("TB_HR_DEV","D","영업 파트","A","2026-01-16T00:00:00Z",1);
        assertThat(membership(i,month,List.of(part("A"),part("B")),List.of(h)).partId()).isEqualTo("A");
    }
    @Test void historicalNameResolvesImmutablePartIdentity() {
        var i=input("D","B");var a=part("A");a.partName="현재 이름";
        var membership=history("TB_HR_DEV","D","영업 파트","이전 이름","2026-02-01T00:00:00Z",1);
        var rename=history("TB_BS_PART","A","파트명","이전 이름","2026-02-02T00:00:00Z",2);
        assertThat(membership(i,month,List.of(a,part("B")),List.of(membership,rename)).partId()).isEqualTo("A");
    }
    @Test void unresolvedNameUsesEvidencedCurrentPartWithWarning() {
        var i=input("D","B");var h=history("TB_HR_DEV","D","영업 파트","알 수 없음","2026-02-01T00:00:00Z",1);
        var result=membership(i,month,List.of(part("A"),part("B")),List.of(h));
        assertThat(result.partId()).isEqualTo("B");assertThat(result.warning()).contains("복원 불명확");
    }
    @Test void historicalUseStateDoesNotTreatLaterResumeAsEarlierActive() {
        var p=part("A");p.useYn="Y";
        var h=history("TB_BS_PART","A","사용 여부","N","2026-02-03T00:00:00Z",1);
        assertThat(closeTarget(p,month,List.of(h))).isFalse();
    }
    @Test void stoppedAfterMonthRemainsTargetButHeadquartersAndMissingCriteriaNeverDo() {
        var p=part("A");p.useYn="N";
        var h=history("TB_BS_PART","A","사용 여부","Y","2026-02-03T00:00:00Z",1);
        assertThat(closeTarget(p,month,List.of(h))).isTrue();p.hqYn="Y";assertThat(closeTarget(p,month,List.of(h))).isFalse();
        p.hqYn="N";p.distributionRate=null;assertThat(closeTarget(p,month,List.of(h))).isFalse();
    }
    @Test void partUsedForPartOfTheMonthIsATargetButOneStoppedBeforeTheMonthIsNot() {
        // 그 달에 하루라도 사용 중이면 정산 대상(2026-11-24 사용자 결정).
        var p=part("A");p.useYn="N";
        var start=month.atDay(1).atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toInstant();
        var mid=history("TB_BS_PART","A","사용 여부","Y",start.plus(java.time.Duration.ofDays(14)).toString(),1);
        assertThat(closeTarget(p,month,List.of(mid))).isTrue();
        var before=history("TB_BS_PART","A","사용 여부","Y",start.minus(java.time.Duration.ofDays(10)).toString(),2);
        assertThat(closeTarget(p,month,List.of(before))).isFalse();
    }
    static History history(String table,String target,String item,String before,String at,int id) {
        var h=new History();h.tableName=table;h.target=target;h.item=item;h.before=before;h.at=Instant.parse(at);h.id=n(Integer.toString(id));return h;
    }
}
