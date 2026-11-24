package com.company.common.commonops;

import com.company.common.commonops.mapper.BusinessPlanComparisonMapper;
import com.company.common.commonops.mapper.BusinessPlanComparisonRows.Target;
import com.company.common.commonops.mapper.BusinessPlanComparisonRows.TargetWrite;
import com.company.common.commonops.service.BusinessPlanUploadForm;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.service.*;
import com.company.common.uploads.dto.UploadContracts.Inspection;
import com.company.common.uploads.service.CommonUploadService;
import com.company.common.uploads.service.UploadForm;
import com.company.common.uploads.service.UploadWorkbookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** SFR-118-04 목표 업로드(B.10). */
class BusinessPlanUploadFormTest {
    BusinessPlanComparisonMapper mapper;
    UserPreferencePeriodSourceMapper periods;
    BusinessPlanUploadForm form;
    final UploadWorkbookService workbook = new UploadWorkbookService(new ExcelExportService());

    @BeforeEach void setup() {
        mapper = mock(BusinessPlanComparisonMapper.class);
        periods = mock(UserPreferencePeriodSourceMapper.class);
        when(periods.selectCashBaseYm()).thenReturn(new BigDecimal("202601"));
        when(periods.selectClosedMonths()).thenReturn(List.of("202601", "202602"));
        when(mapper.targets(anyInt())).thenReturn(List.of());
        when(mapper.upsertTarget(any())).thenReturn(1);
        form = new BusinessPlanUploadForm(mapper, periods, new CurrentBusinessActor());
    }
    static List<List<String>> year(int year) {
        var rows = new ArrayList<List<String>>();
        for (int m = 1; m <= 12; m++)
            rows.add(new ArrayList<>(List.of(String.format("%d-%02d", year, m), "100000000", "10000000")));
        return rows;
    }
    Inspection inspect(List<List<String>> rows) {
        return form.inspect(workbook.readTabular("plan.xlsx", workbook.tabularFile(form.headers(), rows, null),
            form.headers()), null);
    }
    List<TargetWrite> written() {
        var captor = ArgumentCaptor.forClass(TargetWrite.class);
        verify(mapper, atLeast(0)).upsertTarget(captor.capture());
        return captor.getAllValues();
    }

    @Test void commonFormShapeAndTwelveMonthTemplate() {
        assertThat(form.formTypeCode()).isEqualTo("B10");
        assertThat(form.screenId()).isEqualTo("UI-18");
        assertThat(form.headers()).containsExactly("연월", "매출 목표", "영업이익 목표");
        assertThat(form.monthly()).isFalse();
        var rows = form.templateRows(2026);
        assertThat(rows).hasSize(12);
        assertThat(rows.get(0)).containsExactly("2026-01", "", "");
        assertThat(rows.get(11)).containsExactly("2026-12", "", "");
        assertThat(form.templateRows(null)).isEmpty();
        assertThatThrownBy(() -> form.templateRows(2025)).isInstanceOf(CodeFailure.class);
    }
    @Test void monthsMissingFromTheFileAreZeroWithAnEmptyMonthWarning() {
        // 줄 자체가 없는 달도 빈칸처럼 0으로 올리고 「목표가 빈 달」(SFR-118-04, 예전 목표가 남지 않게, 2026-11-24).
        var rows = year(2026).subList(0, 10);
        var result = inspect(new java.util.ArrayList<>(rows));
        assertThat(result.errorCount()).isZero();
        assertThat(result.warningCount()).isEqualTo(2);
        assertThat(result.issues()).extracting(i -> i.inputValue() + " " + i.reason())
            .containsExactly("2026-11 " + BusinessPlanUploadForm.EMPTY_TARGET, "2026-12 " + BusinessPlanUploadForm.EMPTY_TARGET);
        form.apply(result, null, 8L);
        var written = written();
        assertThat(written).hasSize(12);
        assertThat(written.get(11).revenueTarget()).isZero();
    }
    @Test void blankTargetsAreZeroWithAnEmptyMonthWarningNotAnError() {
        var rows = year(2026);
        rows.get(2).set(1, "");
        rows.get(3).set(2, "");
        var result = inspect(rows);
        assertThat(result.errorCount()).isZero();
        assertThat(result.warningCount()).isEqualTo(2);
        assertThat(result.issues()).extracting(i -> i.inputValue() + " " + i.reason())
            .containsExactly("2026-03 " + BusinessPlanUploadForm.EMPTY_TARGET, "2026-04 " + BusinessPlanUploadForm.EMPTY_TARGET);
        form.apply(result, null, 7L);
        var written = written();
        assertThat(written).hasSize(12);
        assertThat(written.get(2).revenueTarget()).isZero();
        assertThat(written.get(3).operatingProfitTarget()).isZero();
        assertThat(written.get(0).uploadId()).isEqualTo(7L);
    }
    @Test void invalidRowsBlockTheWholeFile() {
        var rows = year(2026);
        rows.get(0).set(1, "-5");
        rows.get(1).set(2, "1.5");
        rows.get(4).set(0, "2026-04");
        rows.add(new ArrayList<>(List.of("2027-01", "1", "1")));
        var result = inspect(rows);
        assertThat(result.errorCount()).isEqualTo(5);
        assertThatThrownBy(() -> form.apply(result, null, 1L)).isInstanceOf(CodeFailure.class);
        verify(mapper, never()).upsertTarget(any());
    }
    @Test void yearsBeforeTheStartYearAreRejected() {
        var result = inspect(year(2025));
        assertThat(result.errorCount()).isEqualTo(12);
        assertThat(result.issues().get(0).reason()).isEqualTo("2026년부터 올릴 수 있습니다.");
    }
    @Test void firstUploadOfAYearNeedsNoClosedMonthChoice() {
        var result = inspect(year(2026));
        assertThat(result.issues()).isEmpty();
        form.apply(result, null, 1L, null);
        assertThat(written()).hasSize(12);
    }
    @Test void sameClosedMonthTargetsUploadWithoutTheConfirmDialog() {
        when(mapper.targets(2026)).thenReturn(List.of(
            new Target("202601", new BigDecimal("100000000"), new BigDecimal("10000000")),
            new Target("202602", new BigDecimal("100000000"), new BigDecimal("10000000")),
            new Target("202603", new BigDecimal("1"), new BigDecimal("1"))));
        var result = inspect(year(2026));
        assertThat(result.issues()).isEmpty();
        form.apply(result, null, 1L);
        assertThat(written()).hasSize(12);
    }
    @Nested class ChangedClosedMonths {
        Inspection result;
        @BeforeEach void changed() {
            when(mapper.targets(2026)).thenReturn(List.of(
                new Target("202601", new BigDecimal("90000000"), new BigDecimal("10000000")),
                new Target("202602", new BigDecimal("100000000"), new BigDecimal("10000000"))));
            result = inspect(year(2026));
        }
        @Test void areWarnedAndNeedAChoiceBeforeAnyWrite() {
            assertThat(result.errorCount()).isZero();
            assertThat(result.issues()).extracting(i -> i.inputValue() + " " + i.reason())
                .containsExactly("2026-01 " + BusinessPlanUploadForm.CLOSED_CHANGED);
            assertThatThrownBy(() -> form.apply(result, null, 1L, null)).isInstanceOf(CodeFailure.class);
            verify(mapper, never()).upsertTarget(any());
        }
        @Test void keepLeavesEveryClosedMonthAndWritesOnlyOpenMonths() {
            form.apply(result, null, 1L, UploadForm.KEEP_CLOSED);
            assertThat(written()).extracting(TargetWrite::yearMonth)
                .containsExactly("202603", "202604", "202605", "202606", "202607", "202608", "202609",
                    "202610", "202611", "202612");
        }
        @Test void replaceWritesAllTwelveMonths() {
            form.apply(result, null, 1L, UploadForm.REPLACE_CLOSED);
            assertThat(written()).hasSize(12);
            assertThat(written().get(0).revenueTarget()).isEqualByComparingTo("100000000");
        }
    }
    @Test void applyRequestTakesTheOptionalChoiceAndOtherFormsKeepTheirBehavior() throws Exception {
        var json = new ObjectMapper();
        assertThat(CommonUploadService.parseApply(json.readTree("{\"modifiedAt\":null}")).closedMonthPolicy()).isNull();
        assertThat(CommonUploadService.parseApply(json.readTree("{\"modifiedAt\":null,\"closedMonthPolicy\":\"keep\"}"))
            .closedMonthPolicy()).isEqualTo("keep");
        assertThatThrownBy(() -> CommonUploadService.parseApply(
            json.readTree("{\"modifiedAt\":null,\"closedMonthPolicy\":\"ALL\"}"))).isInstanceOf(CodeFailure.class);
        UploadForm other = mock(UploadForm.class, CALLS_REAL_METHODS);
        assertThatThrownBy(() -> other.apply(null, null, 1L, "keep")).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> other.templateRows(2026)).isInstanceOf(CodeFailure.class);
        assertThat(other.templateRows(null)).isEmpty();
    }
}
