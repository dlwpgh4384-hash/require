package com.company.common.profitstructures;

import com.company.common.profitstructures.dto.ProfitStructureContracts.*;
import com.company.common.profitstructures.service.*;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService.*;
import com.company.common.profitstructures.mapper.ProfitStructureRows.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.company.common.profitstructures.ProfitStructureTestSources.*;
import static com.company.common.profitstructures.service.ProfitStructureRules.*;
import static org.assertj.core.api.Assertions.*;

class ProfitStructureRulesTest {
    @Test void futurePolicyProjectsFinanceExecutiveAdministratorAndUnionWithoutAnHttpGuard() {
        for(var role:List.of("FIN","EXEC","ADMIN")) assertThat(projectedRoleAllows(Set.of(role))).isTrue();
        assertThat(projectedRoleAllows(Set.of("PM","FIN"))).isTrue();
        assertThat(projectedRoleAllows(Set.of("PM","SALES_LDR"))).isFalse();
        assertThat(projectedRoleAllows(Set.of())).isFalse();
    }
    Month month(Snapshot row) { return new Month(row,List.of(),row.revenue(),true,"Y".equals(row.migrated()),!"Y".equals(row.fixed())); }
    @Test void quarterUsesEqualPartialWindowsAndPriorYearRollover() {
        var current=ProfitStructurePeriod.of(new Filter(2026,"quarter","202605"));
        assertThat(current.start()).isEqualTo(YearMonth.of(2026,4));assertThat(current.end()).isEqualTo(YearMonth.of(2026,5));
        assertThat(current.previous().start()).isEqualTo(YearMonth.of(2026,1));assertThat(current.previous().end()).isEqualTo(YearMonth.of(2026,2));
        var winter=ProfitStructurePeriod.of(new Filter(2026,"quarter","202602")).previous();
        assertThat(winter.start()).isEqualTo(YearMonth.of(2025,10));assertThat(winter.end()).isEqualTo(YearMonth.of(2025,11));
    }
    @Test void chronologicalBucketsNeverIncludeFutureQuarterOrMonth() {
        var q=ProfitStructurePeriod.of(new Filter(2026,"quarter","202605"));
        assertThat(q.buckets()).extracting(ProfitStructurePeriod::label).containsExactly("1분기","2분기(5월까지)");
        assertThat(q.previousLabel()).isEqualTo("전 분기 같은 기간");
        assertThat(ProfitStructurePeriod.of(new Filter(2026,"quarter","202606")).previousLabel()).isEqualTo("전 분기");
        assertThat(ProfitStructurePeriod.of(new Filter(2026,"month","202602")).buckets()).extracting(ProfitStructurePeriod::label).containsExactly("1월","2월");
        // 시안: 연 누계는 1월, 1~2월 … 달마다 누계 칸이고 직전 비교 없이 전년 같은 기간만 본다.
        var y=ProfitStructurePeriod.of(new Filter(2026,"year-to-date","202603"));
        assertThat(y.buckets()).extracting(ProfitStructurePeriod::start).containsOnly(YearMonth.of(2026,1));
        assertThat(y.buckets()).extracting(ProfitStructurePeriod::end).containsExactly(YearMonth.of(2026,1),YearMonth.of(2026,2),YearMonth.of(2026,3));
        assertThat(y.buckets()).extracting(ProfitStructurePeriod::label).containsExactly("1월","2월","3월");
        assertThat(y.previous()).isNull(); assertThat(y.previousLabel()).isNull();
        assertThat(y.previousYear().end()).isEqualTo(YearMonth.of(2025,3)); assertThat(y.name()).isEqualTo("2026년 1~3월 누계");
    }
    @Test void ratiosAreWeightedAmountsAndUndefinedRatesAreNull() {
        var first=month(fixed("202601"));
        var small=new Snapshot("202602",n("100"),n("40"),n("0"),n("0"),n("40"),n("60"),n("10"),n("0"),n("50"),n("0"),n("0"),n("0"),n("50"),"N","Y",null);
        var result=summary(List.of(first,month(small)));
        assertThat(result.grossProfitRate()).isEqualByComparingTo(n("360").multiply(n("100")).divide(n("1100"),1,java.math.RoundingMode.DOWN));
        assertThat(ratio(n("10"),BigDecimal.ZERO)).isNull(); assertThat(ratio(null,n("10"))).isNull();
        assertThat(summary(List.of()).revenue()).isZero();assertThat(summary(List.of()).grossProfitRate()).isNull();
    }
    @Test void mixedMigrationHidesCompleteCompositionAndPropagatesUnknownDetail() {
        var migrated=month(migrated("202501")); var normal=month(fixed("202502"));
        var period=ProfitStructurePeriod.of(new Filter(2025,"quarter","202502"));
        var projected=project(period,List.of(migrated,normal));
        assertThat(projected.composition()).isNull();assertThat(projected.trend().referenceRevenue()).isNull();
        assertThat(projected.summary().grossProfit()).isNull();assertThat(projected.waterfall().netProfit()).isNull();
        assertThat(projected.summary().revenue()).isEqualByComparingTo("2000");assertThat(projected.provisional()).isFalse();
    }
    @Test void endingFixedMonthSuppressesEarlierProvisionalFlag() {
        var earlier=new Month(fixed("202601"),List.of(),n("1000"),true,false,true);
        var later=month(fixed("202602"));
        assertThat(project(ProfitStructurePeriod.of(new Filter(2026,"quarter","202602")),List.of(earlier,later)).provisional()).isFalse();
    }
    @Test void customerTiesUseIdAndOtherAggregatesRawCostsBeforeRatios() {
        var values=new ArrayList<ProjectValue>();
        for(String id:List.of("C7","C6","C5","C4","C3","C2","C1"))values.add(new ProjectValue(project("202601",id,"MO",id),n("100"),n(id.equals("C7")?"90":"50")));
        var source=new Month(fixed("202601"),values,n("700"),true,false,false);
        var rows=composition(List.of(source)).topClients();
        assertThat(rows).extracting(CompositionRow::name).containsExactly("C1","C2","C3","C4","C5","그 밖 2곳");
        assertThat(rows.get(5).revenue()).isEqualByComparingTo("200");assertThat(rows.get(5).profitRate()).isEqualByComparingTo("30.0");
        assertThat(rows.stream().map(CompositionRow::revenue).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("700");
    }
    @Test void numericTableUsesSelectedPeriodTopFiveForEveryColumnAndOtherIsTheRest() {
        var jan=new ArrayList<ProjectValue>(); var feb=new ArrayList<ProjectValue>();
        for(String id:List.of("C1","C2","C3","C4","C5","C6","C7")) {
            jan.add(new ProjectValue(project("202601",id,"MO",id),n(id.equals("C7")?"900":"10"),n("0")));
            feb.add(new ProjectValue(project("202602",id,"MO",id),n(id.equals("C7")?"1":"100"),n("0")));
        }
        var rows=List.of(project(ProfitStructurePeriod.of(new Filter(2026,"month","202601")),List.of(new Month(fixed("202601"),jan,n("960"),true,false,false))),
            project(ProfitStructurePeriod.of(new Filter(2026,"month","202602")),List.of(new Month(fixed("202602"),feb,n("601"),true,false,false))));
        var table=ProfitStructureTable.of(rows,List.<List<ProjectValue>>of(jan,feb),true);
        assertThat(table.columns()).extracting(NumericColumn::label).containsExactly("1월","2월");
        assertThat(table.rows().get(0).label()).isEqualTo("매출");
        int head=table.rows().stream().map(NumericLine::label).toList().indexOf("매출 구성 · 고객사 상위 5곳");
        var clients=table.rows().subList(head+1,head+7);
        assertThat(clients).extracting(NumericLine::label).containsExactly("C1","C2","C3","C4","C5","그 밖 2곳");
        // 1월 칸: 2월 상위 다섯 곳(C1~C5)은 10씩, 나머지(C6 10 + C7 900)는 그 밖
        assertThat(clients.get(0).values().get(0)).isEqualByComparingTo("10");
        assertThat(clients.get(5).values().get(0)).isEqualByComparingTo("910");
        assertThat(clients.get(5).values().get(1)).isEqualByComparingTo("101");
    }
    @Test void fiveClientsNeverCreateInventedOtherAndCompositionSubtractsOnlyAllocatedCost() {
        var source=new Month(fixed("202601"),List.of(new ProjectValue(project("202601","P","MO","C1"),n("100"),n("50"))),n("100"),true,false,false);
        var composition=composition(List.of(source));
        assertThat(composition.topClients()).hasSize(1);assertThat(composition.topClients().get(0).profitRate()).isEqualByComparingTo("50.0");
        assertThat(composition.contractTypes().get(0).share()).isEqualByComparingTo("100.0");
        assertThat(composition.projectSalesParts().get(0).name()).isEqualTo("프로젝트 영업 파트");
    }
}
