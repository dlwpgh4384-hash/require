package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.aianalysis.dto.AiAnalysisContracts.Period;
import com.company.common.aianalysis.service.AiAnalysisInterpreter.Query;
import com.company.common.bills.dto.BillContracts.Bill;
import com.company.common.bills.service.CommonBillService;
import com.company.common.billchecks.service.BillCheckService;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.commonops.service.BillingLookupService;
import com.company.common.menus.service.DevelopedScreenRegistry;
import com.company.common.operatingprofitstatements.dto.OperatingProfitStatementContracts;
import com.company.common.operatingprofitstatements.service.CommonOperatingProfitStatementService;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.profitstructures.mapper.ProfitStructureRows.Snapshot;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService.Month;
import com.company.common.support.service.BusinessCalendar;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 등록된 조회 함수 = 명세 목록, 그리고 AI 답 숫자 = 원래 화면 서비스 숫자(같은 자료). */
class AiAnalysisCatalogTest {
    final AiAnalysisTables tables = new AiAnalysisTables(new ObjectMapper().findAndRegisterModules(), AiAnalysisInterpreterTest.screens());

    @Test void registeredFunctionsAreExactlyTheSpecificationListWithoutF08() {
        List<AiAnalysisFunction> functions = List.of(
            new AiAnalysisProfitFunction(null, null, null), new AiAnalysisFinancialFunctions(null, null, null, null, null),
            new AiAnalysisPersonalFunction(null, null, null, null, null, null), new AiAnalysisBillFunctions(null, null, null, null, null),
            new AiAnalysisCashFunctions(null, null, null, null), new AiAnalysisWorkforceFunctions(null, null, null, null, null, null, null, null),
            new AiAnalysisProjectBoardFunction(null, null, null), new AiAnalysisPipelineFunctions(null, null, null, null, null));
        var reader = new AiAnalysisReadExecutor(functions, mock(PlatformTransactionManager.class), null, null, null);
        var expected = List.of("F-01", "F-02", "F-03", "F-04", "F-05", "F-06", "F-07", "F-09", "F-10", "F-11", "F-12",
            "P-01", "P-02", "P-03", "P-04", "P-05");
        assertThat(reader.registered()).containsExactlyElementsOf(expected).doesNotContain("F-08");
        assertThat(AiAnalysisCatalog.FUNCTIONS).extracting(AiAnalysisCatalog.Entry::id).containsExactlyElementsOf(expected);
        assertThat(AiAnalysisCatalog.FUNCTIONS).extracting(AiAnalysisCatalog.Entry::name).containsExactly("회사 손익", "파트 이익금",
            "프로젝트 손익", "개인 손익·적자 인력", "사업계획 달성률", "청구·미수", "자금 현황", "인력 현황", "철수 예정", "프로젝트 상태",
            "영업 기회", "매출·영업이익 예측", "턴키 최종 마진율", "자금 잔액 예측", "인력 과부족", "미수 연체 위험");
        assertThat(AiAnalysisCatalog.FUNCTIONS).extracting(AiAnalysisCatalog.Entry::sourceScreenId).containsExactly("UI-08", "UI-19",
            "UI-04", "UI-03", "UI-18", "UI-13", "UI-21", "UI-28", "UI-05", "UI-31", "UI-29", "UI-29", "UI-04", "UI-21", "UI-17", "UI-13");
        var registry = new DevelopedScreenRegistry();
        assertThat(AiAnalysisCatalog.FUNCTIONS).allSatisfy(e -> {
            assertThat(e.inputs()).isNotEmpty(); assertThat(registry.contains(e.sourceScreenId())).isTrue();
        });
    }

    static Month month(int m, String revenue, String operating) {
        String ym = String.format("2026%02d", m);
        var values = new Snapshot(ym, new BigDecimal(revenue), BigDecimal.ZERO, new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("100"),
            new BigDecimal(revenue).subtract(new BigDecimal("100")), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(operating),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(operating), "N", m < 10 ? "Y" : "N", Instant.EPOCH);
        return new Month(values, List.of(), BigDecimal.ZERO, true, false, m >= 10);
    }
    @Test void companyProfitTotalsEqualTheOperatingProfitStatementOnTheSameFixture() {
        var monthly = mock(ProfitStructureMonthlyProfitService.class);
        var months = new LinkedHashMap<String, Month>();
        for (int m = 1; m <= 10; m++) months.put(String.format("2026%02d", m), month(m, String.valueOf(1000 + m * 37), String.valueOf(100 + m * 11)));
        when(monthly.year(2026, 10, "202601")).thenReturn(months);
        var periods = mock(UserPreferencePeriodBasis.class);
        when(periods.metadata()).thenReturn(Map.of("periodBasis", new UserPreferencePeriodBasis.Basis("202610", "202601", List.of("202609"))));
        var screen = new CommonOperatingProfitStatementService(monthly, periods, mock(CalculationRuleService.class))
            .getCommonOperatingProfitStatements(new OperatingProfitStatementContracts.Filter(2026, "202610")).data().summary();
        var answer = new AiAnalysisProfitFunction(monthly, periods, tables)
            .read(new Query("2026년 10월 누계 매출", "F-01", new Period("2026-01", "2026-10"), false, false, true, null));
        var total = answer.tables().get(0).totalRow();
        assertThat((BigDecimal) total.get(1)).isEqualByComparingTo(screen.cumulativeRevenue().amount());
        assertThat((BigDecimal) total.get(2)).isEqualByComparingTo(screen.cumulativeOperatingProfit().amount());
        assertThat((BigDecimal) total.get(3)).isEqualByComparingTo(screen.operatingProfitRate());
        assertThat(answer.provisional()).isTrue();
    }

    static Bill bill(String id, String month, String unpaid, String status) {
        return new Bill(id, "P" + id, "프로젝트" + id, "거래처", 1, month, LocalDate.of(2025, 12, 1), BigDecimal.TEN, BigDecimal.TEN,
            BigDecimal.ONE, new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal(unpaid), LocalDate.of(2026, 1, 25), null, status,
            false, null, null, null, null, 0, false, false, false, false, false, Instant.EPOCH);
    }
    @Test void unpaidAnswerUsesEveryUnpaidBillRegardlessOfYearLikeTheMenuBadge() {
        var bills = mock(CommonBillService.class);
        var unpaid = List.of(bill("1", "202512", "700", "LATE"), bill("2", "202610", "300", "UNPAID"));
        when(bills.getCommonUnpaidBills()).thenReturn(unpaid);
        var calendar = mock(BusinessCalendar.class); when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 11));
        var answer = new AiAnalysisBillFunctions(bills, mock(BillCheckService.class), mock(BillingLookupService.class), calendar, tables)
            .read(new Query("지금 미수금은 얼마?", "F-06", new Period("2026-11", "2026-11"), false, false));
        var expected = unpaid.stream().map(Bill::unpaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat((BigDecimal) answer.tables().get(0).totalRow().get(4)).isEqualByComparingTo(expected);
        assertThat(answer.conclusion()).isEqualTo("지금 미수금은 **2건**, 합계 **0만 원**입니다. 그중 연체는 1건입니다.");
        assertThat(answer.provisional()).isFalse(); assertThat(answer.usedData()).isEqualTo("청구·입금 · 오늘 기준");
        verify(bills, never()).getCommonBills(any());
    }
}
