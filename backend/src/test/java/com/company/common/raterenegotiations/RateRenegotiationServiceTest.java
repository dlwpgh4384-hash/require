package com.company.common.raterenegotiations;

import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Cost;
import com.company.common.raterenegotiations.dto.RateRenegotiationContracts.*;
import com.company.common.raterenegotiations.mapper.RateRenegotiationMapper;
import com.company.common.raterenegotiations.mapper.RateRenegotiationRows.AssignmentInfo;
import com.company.common.raterenegotiations.service.*;
import com.company.common.support.service.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.company.common.raterenegotiations.RateRenegotiationCalculationTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateRenegotiationServiceTest {
    private final RateRenegotiationSource source = mock(RateRenegotiationSource.class);
    private final RateRenegotiationMapper mapper = mock(RateRenegotiationMapper.class);
    private final BusinessCalendar calendar = mock(BusinessCalendar.class);
    private final CommonRateRenegotiationService service = new CommonRateRenegotiationService(source, mapper, calendar);
    private static FormulaHelp help() { return new FormulaHelp("갱신", "원가", "이익률", "목표", "인상률", "연이익"); }
    private static Cost cost(String id, String labor) {
        return new Cost("202602", "D", "P", id, "프로젝트", "직무", "MO", n("0.5"), n("500000"),
            n(labor), n("999"), n("888"), "NEW");
    }
    private void sources(List<Cost> costs, List<AssignmentInfo> assignments) {
        when(calendar.today()).thenReturn(TODAY);
        when(source.read()).thenReturn(new RateRenegotiationSource.Context("202602", false, n("20"), costs, List.of(), help()));
        when(mapper.selectAssignmentInfo("202602", false)).thenReturn(assignments);
    }
    @Test void keepsAssignmentsDistinctCountsPeopleOnceAndSortsExactRatiosStably() {
        sources(List.of(cost("A", "450000"), cost("B", "450001"), cost("C", "450001")),
            List.of(info("A", 1), info("B", 2), info("C", 3)));
        var result = service.getCommonRateRenegotiations(filter());
        assertThat(result.items()).extracting(Row::assignmentId).containsExactly("B", "C", "A");
        assertThat(result.summary().targetCount()).isEqualTo(1);
        assertThat(result.summary().averageProfitRate()).isEqualByComparingTo("10.0");
        assertThat(result.summary().assumedAnnualProfitIncrease()).isEqualByComparingTo("1800000");
        assertThat(result.totalAnnualProfitIncrease()).isEqualByComparingTo(result.summary().assumedAnnualProfitIncrease());
        verify(mapper, never()).insertAccess(anyString(), anyString());
    }
    @Test void optionalEmployeeFilterAppliesToBothRowsAndSummaryWithoutRestrictingParts() {
        sources(List.of(cost("A", "450000")), List.of(info("A", 1)));
        var result = service.getCommonRateRenegotiations(new Filter(6, n("15"), "FT", null, n("5")));
        assertThat(result.items()).isEmpty();
        assertThat(result.summary().targetCount()).isZero();
        assertThat(result.summary().averageProfitRate()).isNull();
        assertThat(result.summary().targetProfitRate()).isEqualByComparingTo("20");
    }
    @Test void optionalProjectFilterAppliesToRowsAndSummary() {
        sources(List.of(cost("A", "450000")), List.of(info("A", 1)));
        assertThat(service.getCommonRateRenegotiations(new Filter(6, n("15"), null, "other", n("5"))).items()).isEmpty();
        assertThat(service.getCommonRateRenegotiations(new Filter(6, n("15"), null, "P", n("5"))).summary().targetCount()).isEqualTo(1);
    }
    @Test void serializesBeforeAuditAndNeverAuditsWorkbookFailure() {
        var calculation = mock(CommonRateRenegotiationService.class);
        var writer = mock(ExcelExportService.class);
        var actor = new CurrentBusinessActor();
        var export = new RateRenegotiationExcelService(calculation, mapper, writer, actor, calendar);
        when(calculation.getCommonRateRenegotiations(any())).thenReturn(new Result("2026-02", n("5"), List.of(),
            new Summary(0, null, n("20"), n("0"), n("0")), n("0"), help()));
        when(calendar.now()).thenReturn(java.time.Instant.parse("2026-02-01T00:00:00Z"));
        when(writer.write(anyString(), anyList(), anyList(), anyString(), any())).thenThrow(new IllegalStateException("generation"));
        assertThatThrownBy(() -> export.getCommonRateRenegotiationsExcel(filter(), List.of("developerName")))
            .isInstanceOf(IllegalStateException.class);
        verify(mapper, never()).insertAccess(anyString(), anyString());
    }
    @Test void callerColumnOrderIsPreservedAndDuplicateEmptyAndUnknownKeysAreRejected() {
        assertThat(RateRenegotiationColumnPolicy.validate(List.of("monthlyCost", "developerName")))
            .containsExactly("monthlyCost", "developerName");
        for (var columns : List.of(List.<String>of(), List.of(""), List.of("unknown"), List.of("developerName", "developerName"))) {
            assertThatThrownBy(() -> RateRenegotiationColumnPolicy.validate(columns)).isInstanceOf(CodeFailure.class);
        }
    }
}
