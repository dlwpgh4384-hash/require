package com.company.common.profitstructures;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.profitstructures.mapper.ProfitStructureRows.*;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Assignment;
import com.company.common.profitstructures.service.*;
import com.company.common.calculation.dto.CalculationRuleContracts.Standard;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static com.company.common.profitstructures.ProfitStructureTestSources.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfitStructureMonthlyProfitTest {
    ProfitStructureMapper mapper; CalculationRuleService calculation; ProfitStructureMonthlyProfitService service;
    @BeforeEach void setup() {
        mapper=mock(ProfitStructureMapper.class);calculation=mock(CalculationRuleService.class);service=new ProfitStructureMonthlyProfitService(mapper,calculation);
        when(mapper.people(any())).thenReturn(List.of(person()));when(mapper.payroll(any())).thenReturn(List.of(pay()));when(mapper.codes()).thenReturn(codes());
        when(calculation.effectiveStandard(eq("WTHLD_RT"),any())).thenReturn(new Standard("WTHLD_RT", java.time.YearMonth.of(2026,1), n("3.3")));
    }
    @Test void turnkeyTurnoverUsesBillingWhileReferenceIndependentlyUsesDistribution() {
        var project=new ProjectMonth("202602","P","TK","턴키","C1","고객사","ACQUIRED","프로젝트 영업 파트",n("10000"),n("8000"),n("900"),n("50"),n("777"));
        when(mapper.projects(any())).thenReturn(List.of(project));
        when(mapper.assignments(any())).thenReturn(List.of(new Assignment("202602","A","P","A1","프로젝트","백엔드","TK","PAID",n("1"),n("9999"),n("10"),n("20"),n("10000"),n("8000"),n("777"))));
        var result=service.year(2026,2,"202601").get("202602");
        assertThat(result.values().revenue()).isEqualByComparingTo("900"); assertThat(result.referenceRevenue()).isEqualByComparingTo("777");
        assertThat(result.values().allocatedCost()).isEqualByComparingTo("200");assertThat(result.values().directExpense()).isEqualByComparingTo("80");
        assertThat(result.projects().get(0).project().partId()).isEqualTo("ACQUIRED");
    }
    @Test void turnkeyWithoutDistributionUsesBudgetRatioAndNullWhenBudgetUnknown() {
        when(mapper.projects(any())).thenReturn(List.of(new ProjectMonth("202602","P","TK","턴키","C1","고객사","ACQUIRED","영업",n("10000"),n("8000"),n("900"),null,null)));
        when(mapper.assignments(any())).thenReturn(List.of(new Assignment("202602","A","P","A1","프로젝트","백엔드","TK","PAID",n("1"),n("0"),n("0"),n("0"),n("10000"),n("8000"),null)));
        assertThat(service.year(2026,2,"202601").get("202602").referenceRevenue()).isEqualByComparingTo("250");
        when(mapper.projects(any())).thenReturn(List.of(new ProjectMonth("202602","P","TK","턴키","C1","고객사","ACQUIRED","영업",n("10000"),null,n("900"),null,null)));
        assertThat(service.year(2026,2,"202601").get("202602").referenceRevenue()).isNull();
    }
    @Test void internalProjectLaborAndExpensesAreSgaNotExternalSalesCost() {
        when(mapper.projects(any())).thenReturn(List.of(new ProjectMonth("202602","P","IN","내부",null,"",null,"",n("0"),n("0"),null,n("50"),null)));
        when(mapper.assignments(any())).thenReturn(List.of(new Assignment("202602","A","P","A1","내부","백엔드","IN","PAID",n("1"),n("9999"),n("10"),n("20"),n("0"),n("0"),null)));
        var values=service.year(2026,2,"202601").get("202602").values();
        assertThat(values.revenue()).isZero();assertThat(values.costOfSales()).isZero();assertThat(values.sga()).isEqualByComparingTo("280");
    }
    @Test void payrollMonthClassificationOverridesCurrentDeveloperAffiliation() {
        when(mapper.projects(any())).thenReturn(List.of(project("202602","P","MO","C1")));
        when(mapper.assignments(any())).thenReturn(List.of(assignment()));
        when(mapper.payrollClasses(any())).thenReturn(List.of(new PayrollClass("202602","A","INDRCT")));
        var values=service.year(2026,2,"202601").get("202602").values();
        assertThat(values.allocatedCost()).isZero();assertThat(values.sga()).isEqualByComparingTo("200");
    }
    @Test void allowanceRatesComeOnlyFromTheAllowanceCodeGroup() {
        var p=person();
        var leader=new com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Person(p.developerId(),p.developerName(),p.affiliationId(),p.affiliationName(),
            "TEAM",p.positionName(),p.employeeTypeCode(),p.employeeTypeName(),p.joinDate(),p.retireDate(),p.jobCode(),p.jobName(),p.gradeCode(),p.gradeName(),
            p.salesPartId(),p.directClassCode(),p.managementNumber(),p.salesPartName());
        when(mapper.people(any())).thenReturn(List.of(leader));
        when(mapper.projects(any())).thenReturn(List.of(project("202602","P","MO","C1")));
        when(mapper.assignments(any())).thenReturn(List.of(assignment()));
        var expected=service.year(2026,2,"202601").get("202602").values().allocatedCost();
        var other=codes().stream().map(c->new com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Code("OTHER",c.code(),c.name(),n("999999"))).toList();
        when(mapper.codes()).thenReturn(other);
        assertThat(service.year(2026,2,"202601").get("202602").values().allocatedCost()).isLessThan(expected);
        var mixed=new java.util.ArrayList<>(other);mixed.addAll(codes());
        when(mapper.codes()).thenReturn(mixed);
        assertThat(service.year(2026,2,"202601").get("202602").values().allocatedCost()).isEqualByComparingTo(expected);
    }
    @Test void unknownImportedTaxDoesNotBecomeZeroInOpenMonthAllocation() {
        when(mapper.snapshots(any())).thenReturn(List.of(migrated("202501")));
        when(mapper.annualTax("2025")).thenReturn(n("120"));
        var values=service.year(2025,2,"202601").get("202502").values();
        assertThat(values.tax()).isNull();assertThat(values.netProfit()).isNull();
    }
}
