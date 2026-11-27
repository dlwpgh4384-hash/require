package com.company.common.operatingprofitstatements;

import com.company.common.operatingprofitstatements.dto.OperatingProfitStatementContracts.*;
import com.company.common.operatingprofitstatements.service.OperatingProfitStatementProjection;
import com.company.common.operatingprofitstatements.service.OperatingProfitStatementUploadForm;
import com.company.common.profitstructures.mapper.ProfitStructureRows.Snapshot;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService.Month;
import com.company.common.profitstructures.service.ProfitStructureRules;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OperatingProfitStatementProjectionTest {
    static Month month(String key, String revenue, String profit) {
        var value=new Snapshot(key,new BigDecimal(revenue),null,null,null,null,null,null,null,new BigDecimal(profit),null,null,null,null,"Y","Y",null);
        return new Month(value,List.of(),null,true,true,false);
    }
    @Test void cumulativePreviousMonthUsesPreviousCumulativeNotSingleMonth() {
        var source=new TreeMap<String,Month>();
        source.put("202601",month("202601","100","20")); source.put("202602",month("202602","100","20"));
        source.put("202603",month("202603","100","20"));
        var result=OperatingProfitStatementProjection.of(new Filter(2026,"202603"),source,false);
        assertThat(result.summary().cumulativeRevenue().previousMonthChangePercent()).isEqualByComparingTo("50.0");
        assertThat(result.summary().baseMonthOperatingProfit().previousMonthChangePercent()).isEqualByComparingTo("0.0");
    }
    @Test void januaryCumulativeHasNoPreviousButBaseMonthCanCompareDecember() {
        var source=Map.of("202601",month("202601","100","20"),"202512",month("202512","100","10"));
        var result=OperatingProfitStatementProjection.of(new Filter(2026,"202601"),source,false);
        assertThat(result.summary().cumulativeRevenue().previousMonthChangePercent()).isNull();
        assertThat(result.summary().baseMonthOperatingProfit().previousMonthChangePercent()).isEqualByComparingTo("100.0");
    }
    @Test void liveMonthWithoutSourcesIsZeroLikeProfitStructureAndFirstYearHasNoPriorColumns() {
        var empty=new Snapshot("202601",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,
            BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,null,"N","N",null,
            BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
        var source=Map.of("202601",new Month(empty,List.of(),null,false,false,true));
        var result=OperatingProfitStatementProjection.of(new Filter(2026,"202601"),source,false);
        assertThat(result.summary().cumulativeRevenue().amount()).isEqualByComparingTo("0");
        assertThat(result.numericTable().rows().get(0).values().get(0).value()).isEqualByComparingTo("0");
        assertThat(result.numericTable().rows().get(0).values().get(13).value()).isNull();
        assertThat(result.numericTable().columns()).hasSize(15);
        var first=OperatingProfitStatementProjection.of(new Filter(2026,"202601"),source,true);
        assertThat(first.numericTable().columns()).extracting(Column::label).last().isEqualTo("누계");
        assertThat(first.numericTable().rows()).allSatisfy(row -> assertThat(row.values()).hasSize(13));
        assertThat(first.numericTable().rows()).extracting(Line::style)
            .containsExactly("bold","group","indent","indent","indent","bold","plain","indent","indent","indent","plain","bold","plain");
    }
    @Test void zeroIsPresentButZeroDenominatorAndNullAreNotZeroPercent() {
        assertThat(OperatingProfitStatementProjection.change(BigDecimal.ONE,BigDecimal.ZERO)).isNull();
        assertThat(OperatingProfitStatementProjection.change(null,BigDecimal.ONE)).isNull();
        assertThat(OperatingProfitStatementProjection.change(new BigDecimal("-50"),new BigDecimal("-100"))).isEqualByComparingTo("50.0");
        var values=List.of(month("202501","0","0"));
        assertThat(ProfitStructureRules.completeSum(values,1,Snapshot::revenue)).isEqualByComparingTo("0");
        assertThat(ProfitStructureRules.completeSum(values,2,Snapshot::revenue)).isNull();
        assertThat(ProfitStructureRules.completeSum(values,1,Snapshot::grossProfit)).isNull();
    }
    @Test void futureReadAndUploadPolicyArePureAndDistinct() {
        assertThat(OperatingProfitStatementUploadForm.projectedUploadAllows(Set.of("ADMIN"))).isTrue();
        assertThat(OperatingProfitStatementUploadForm.projectedUploadAllows(Set.of("FIN","EXEC"))).isFalse();
    }
}
