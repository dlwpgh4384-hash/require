package com.company.common.cashflowstatements;

import com.company.common.calculation.dto.CalculationRuleContracts.*;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.cashflowstatements.controller.CommonCashFlowStatementController;
import com.company.common.cashflowstatements.dto.CashFlowStatementContracts.Filter;
import com.company.common.cashflowstatements.mapper.CashFlowStatementMapper;
import com.company.common.cashflowstatements.service.*;
import com.company.common.common.CashFlowStatementDefinitions;
import com.company.common.financeitems.dto.FinanceItemContracts.CashContribution;
import com.company.common.financeitems.mapper.FinanceItemMapper;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonCashFlowStatementController.class)
@ContextConfiguration(classes = {CommonCashFlowStatementController.class, CommonCashFlowStatementService.class,
    CashFlowStatementExcelService.class, CashFlowStatementDefinitions.class, ExcelExportService.class,
    BusinessCalendar.class, CurrentBusinessActor.class, CodeExceptionHandler.class, ApiExceptionHandler.class})
class CashFlowStatementControllerTest {
    static final String PATH = "/api/common/cash-flow-statements";
    @Autowired MockMvc mvc;
    @Autowired CommonCashFlowStatementService reports;
    @SpyBean ExcelExportService writer;
    @MockBean CashFlowStatementMapper source;
    @MockBean FinanceItemMapper finance;
    @MockBean ProfitStructureMapper people;
    @MockBean UserPreferencePeriodSourceMapper periods;
    @MockBean CalculationRuleService calculations;
    @MockBean PlatformTransactionManager transactions;
    static BigDecimal n(String value) { return new BigDecimal(value); }
    static CashContribution contribution(String month, String type, String value) { return new CashContribution(month, type, n(value)); }
    static List<Code> cashCodes() {
        return List.of(
            new Code("EMP_TYPE", "FT", "정규직", null),
            new Code("SGA_ITEM", "RENT", "임대료", null),
            new Code("FIN_CLS", "NOPR_IN", "영업외수익", null),
            new Code("DRCT_CLS", "DRCT", "직접", null),
            new Code("PAY_TYPE", "CNFM", "확정 신고", null),
            new Code("IO", "IN", "받음", null),
            new Code("XFER_CLS", "SPCL", "특별비(미지급 인건비)", null));
    }
    static Definition definition(String id) {
        String formula = switch (id) {
            case "식-46" -> "이월금 = 전월이월금(A) + 수입(B) − 지출(C). 다음 달 전월이월금 = 이번 달 이월금";
            case "식-47" -> "수입(B) = 입금 합계(부가세 포함) + 잡수익 + 대여금";
            case "식-48" -> "지출(C) = 인건비(실지급액) + 계약직·프리랜서 + 용역비(부가세 포함) + 미지급 인건비(계좌이체 지출 구분 특별비) + 제경비(세금·4대보험·영업비·계좌이체 지출 구분 계좌이체(퇴직금 지급·퇴직연금 납입 포함) 등) + 법인세 납부";
            case "식-49" -> "외주 지급액 = 공급가액 + 부가세(10%). 프리랜서 공급가액 = 그 달 급여(급여 업로드 지급액) + 수당(프리랜서는 0, 식-08). 프리랜서 지급액 = 공급가액 − 원천세(공급가액 × 3.3%). 모두 원 단위 버림";
            case "식-50" -> "확정 신고 예상액 = 연간 예상 법인세 − 중간예납";
            case "식-51" -> "경고 = 예정 달 이월금 < 최소 운영자금(운영 기준값 MIN_CASH, 처음 10억 원)";
            case "식-52" -> "항목 합계 = 기간 안 월 값의 합. 기간 전월이월금 = 첫 달 전월이월금. 기간 이월금 = 마지막 달 이월금";
            default -> "기존 원천 정의";
        };
        return new Definition(id, formula, "금액은 원 단위 정수 저장");
    }
    @BeforeEach void setup() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(source.currentMonth()).thenReturn("202611");
        when(source.immediateStandard("CASH_BASE_YM")).thenReturn(n("202601"));
        when(source.immediateStandard("CASH_BASE_AMT")).thenReturn(n("1000000000"));
        when(calculations.definition(anyString())).thenAnswer(call -> definition(call.getArgument(0)));
        when(calculations.effectiveStandard(eq("MIN_CASH"), any())).thenReturn(new Standard("MIN_CASH", YearMonth.of(2026, 1), n("1000000000")));
        when(source.insertAccess(anyString(), anyString())).thenReturn(1);
        when(people.codes()).thenReturn(cashCodes());
    }
    @Test void anonymousReportReturnsTwelveMonthsAndFifteenRawColumns() throws Exception {
        when(source.receipts()).thenReturn(List.of(contribution("202601", "RECEIPT", "100")));
        mvc.perform(get(PATH).param("year", "2026")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.months.length()").value(12))
            .andExpect(jsonPath("$.data.numericTable.columns.length()").value(15))
            .andExpect(jsonPath("$.data.numericTable.rows.length()").value(15))
            .andExpect(jsonPath("$.data.months[0].cashSales").value(100))
            .andExpect(jsonPath("$.data.numericTable.rows[2].values[0]").value(100));
        verify(source, never()).insertAccess(anyString(), anyString());
        verify(finance, only()).cashContributions();
        verify(people).codes();
    }
    @ParameterizedTest @ValueSource(strings = {"", "/excel"})
    void anonymousOperationsRejectMissingCodeDefinitionsBeforeReadingBusinessSources(String suffix) throws Exception {
        when(people.codes()).thenReturn(cashCodes().stream()
            .filter(code -> !"IO".equals(code.groupCode())).toList());
        mvc.perform(get(PATH + suffix).param("year", "2026").param("amountUnit", "원"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("code"));
        verifyNoInteractions(source, finance);
        verify(people).codes();
    }
    @ParameterizedTest @ValueSource(strings = {"", "/excel"})
    void anonymousOperationsRejectBlankFormulaWithoutFallbackOrDownloadAudit(String suffix) throws Exception {
        when(calculations.definition("식-44")).thenReturn(new Definition("식-44", " ", "DB 상세"));
        mvc.perform(get(PATH + suffix).param("year", "2026").param("amountUnit", "원"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("calcId"));
        verifyNoInteractions(source, finance, people);
    }
    @Test void missingYearReturnsFieldErrorBeforeReadingSources() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("year"));
        verifyNoInteractions(source, finance);
    }
    @ParameterizedTest @ValueSource(strings = {"word", "2026.5", "0", "10000"})
    void invalidYearReturnsFieldErrorWithoutSourceMutation(String value) throws Exception {
        mvc.perform(get(PATH).param("year", value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("year"));
        verifyNoInteractions(source, finance);
    }
    @Test void closedMonthUsesOnlyActualAndOverduePlanDoesNotMoveForward() throws Exception {
        when(periods.selectClosedMonths()).thenReturn(List.of("202601"));
        when(source.receipts()).thenReturn(List.of(contribution("202601", "RECEIPT", "40"), contribution("202601", "REMAINDER", "60")));
        var result = reports.getCommonCashFlowStatements(new Filter(2026)).data();
        assertThat(result.months().get(0).cashSales()).isEqualByComparingTo("40");
        assertThat(result.months().get(1).cashSales()).isZero();
    }
    @Test void partialReceiptAndOnlyRemainderBelongToDifferentMonths() {
        when(source.receipts()).thenReturn(List.of(contribution("202601", "RECEIPT", "40"), contribution("202603", "REMAINDER", "60")));
        var result = reports.getCommonCashFlowStatements(new Filter(2026)).data();
        assertThat(result.months().get(0).cashSales()).isEqualByComparingTo("40");
        assertThat(result.months().get(2).cashSales()).isEqualByComparingTo("60");
        assertThat(result.numericTable().rows().get(2).values().get(14)).isEqualByComparingTo("100");
    }
    @Test void warningOnlyForStrictlyLowerOpenMonth() {
        when(periods.selectClosedMonths()).thenReturn(List.of("202601"));
        when(finance.cashContributions()).thenReturn(List.of(contribution("202601", "EXPENSE", "1"), contribution("202602", "LOAN", "1"), contribution("202603", "EXPENSE", "1")));
        var result = reports.getCommonCashFlowStatements(new Filter(2026)).data();
        assertThat(result.warnings()).extracting(w -> w.yearMonth()).doesNotContain("202601", "202602").contains("202603");
    }
    @Test void configuredZeroMinimumIsNotReplacedByInitialBillion() {
        when(calculations.effectiveStandard(eq("MIN_CASH"), any())).thenReturn(new Standard("MIN_CASH", YearMonth.of(2026, 1), n("0")));
        var result = reports.getCommonCashFlowStatements(new Filter(2026)).data();
        assertThat(result.minimumCash()).isZero();
        assertThat(result.warnings()).isEmpty();
        verify(calculations).effectiveStandard("MIN_CASH", YearMonth.of(2026, 11));
    }
    @Test void unknownBaseKeepsFlowsAndNullBalancesWithoutWarnings() {
        when(source.immediateStandard("CASH_BASE_AMT")).thenReturn(null);
        when(source.receipts()).thenReturn(List.of(contribution("202601", "RECEIPT", "50")));
        var result = reports.getCommonCashFlowStatements(new Filter(2026)).data();
        assertThat(result.months().get(0).cashSales()).isEqualByComparingTo("50");
        assertThat(result.months()).allSatisfy(m -> { assertThat(m.openingBalance()).isNull(); assertThat(m.closingBalance()).isNull(); });
        assertThat(result.warnings()).isEmpty();
    }
    @Test void beforeBaseYearStillReturnsFlows() {
        when(source.receipts()).thenReturn(List.of(contribution("202501", "RECEIPT", "50")));
        var result = reports.getCommonCashFlowStatements(new Filter(2025)).data();
        assertThat(result.months().get(0).cashSales()).isEqualByComparingTo("50");
        assertThat(result.months().get(0).closingBalance()).isNull();
        assertThat(result.warnings()).isEmpty();
    }
    @Test void absentBaseMonthStillReturnsTwelveMonthRead() throws Exception {
        when(source.immediateStandard("CASH_BASE_YM")).thenReturn(null);
        mvc.perform(get(PATH).param("year", "2026")).andExpect(status().isOk()).andExpect(jsonPath("$.meta.currentYearMonth").value("202611"));
    }
    @Test void emptySourceRetainsNullTableAndGroupHeadings() {
        when(source.immediateStandard("CASH_BASE_AMT")).thenReturn(null);
        var result = reports.getCommonCashFlowStatements(new Filter(2026)).data();
        assertThat(result.numericTable().rows()).allSatisfy(row -> assertThat(row.values()).hasSize(15).containsOnlyNulls());
    }
    @Test void balancesChainAcrossYearsAndPeriodEndsAreNotSummed() {
        when(source.immediateStandard("CASH_BASE_YM")).thenReturn(n("202512"));
        when(source.immediateStandard("CASH_BASE_AMT")).thenReturn(n("100"));
        when(source.receipts()).thenReturn(List.of(contribution("202512", "RECEIPT", "20"), contribution("202601", "RECEIPT", "10"), contribution("202607", "RECEIPT", "30")));
        var result = reports.getCommonCashFlowStatements(new Filter(2026)).data();
        assertThat(result.numericTable().rows().get(0).values().subList(12, 15)).containsExactly(n("120"), n("130"), n("120"));
        assertThat(result.numericTable().rows().get(14).values().subList(12, 15)).containsExactly(n("130"), n("160"), n("160"));
    }
    @Test void midyearBasePreservesNullSummaryEndpointsAndKnownFlowSums() throws Exception {
        when(source.immediateStandard("CASH_BASE_YM")).thenReturn(n("202607"));
        when(source.receipts()).thenReturn(List.of(contribution("202603", "RECEIPT", "40"), contribution("202607", "RECEIPT", "60")));
        mvc.perform(get(PATH).param("year", "2026")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.months[2].cashSales").value(40))
            .andExpect(jsonPath("$.data.months[2].openingBalance").isEmpty())
            .andExpect(jsonPath("$.data.months[2].closingBalance").isEmpty())
            .andExpect(jsonPath("$.data.numericTable.rows[0].values[12]").isEmpty())
            .andExpect(jsonPath("$.data.numericTable.rows[0].values[13]").value(1000000000))
            .andExpect(jsonPath("$.data.numericTable.rows[0].values[14]").isEmpty())
            .andExpect(jsonPath("$.data.numericTable.rows[14].values[12]").isEmpty())
            .andExpect(jsonPath("$.data.numericTable.rows[14].values[14]").value(1000000060))
            .andExpect(jsonPath("$.data.numericTable.rows[2].values[14]").value(100))
            .andExpect(jsonPath("$.data.warnings.length()").value(0));
        verify(source, never()).insertAccess(anyString(), anyString());
    }
    @Test void closedMonthWithoutUploadNeverForecastsPayroll() {
        when(source.payroll("202612")).thenReturn(List.of(salary("202610", "FT")));
        when(source.activeDevelopers()).thenReturn(List.of("developer"));
        when(people.people(any())).thenReturn(List.of(new Person("developer", "이름", null, null, null, null, "FL", "프리랜서", null, null, null, null, null, null, null, null, null, null)));
        when(periods.selectClosedMonths()).thenReturn(List.of("202611"));
        var months = reports.getCommonCashFlowStatements(new Filter(2026)).data().months();
        assertThat(months.get(10).contractFreelancePayroll()).isZero();
        assertThat(months.get(10).overhead()).isEqualByComparingTo("1000");
        assertThat(months.get(11).contractFreelancePayroll()).isEqualByComparingTo("4835000");
    }
    static Pay salary(String month, String type) {
        return new Pay(month, "developer", type, n("5000000"), n("0"), n("100"), n("999999"), n("200"), n("300"), n("400"), n("4500000"), n("8000000"));
    }
    @Test void storedClassificationSurvivesFutureFreelancerAndDeductionsStayInNextMonth() {
        when(source.payroll("202612")).thenReturn(List.of(salary("202610", "FT")));
        when(source.activeDevelopers()).thenReturn(List.of("developer"));
        when(people.people(any())).thenReturn(List.of(new Person("developer", "이름", null, null, null, null, "FL", "프리랜서", null, null, null, null, null, null, null, null, null, null)));
        var result = reports.getCommonCashFlowStatements(new Filter(2026)).data();
        assertThat(result.months().get(9).regularPayroll()).isEqualByComparingTo("4500000");
        assertThat(result.months().get(10).contractFreelancePayroll()).isEqualByComparingTo("4835000");
        assertThat(result.months().get(10).overhead()).isEqualByComparingTo("1000");
        assertThat(result.months().get(11).overhead()).isEqualByComparingTo("165000");
        assertThat(result.months().get(8).regularPayroll()).isZero();
    }
    @Test void previousDecemberDeductionsAreIncludedInJanuary() {
        when(source.payroll("202612")).thenReturn(List.of(salary("202512", "FT")));
        assertThat(reports.getCommonCashFlowStatements(new Filter(2026)).data().months().get(0).overhead()).isEqualByComparingTo("1000");
    }
    @Test void storedFreelanceAndVendorConsumeLiveRatesAndStoredContractUsesRealPay() {
        when(source.payroll("202612")).thenReturn(List.of(salary("202601", "FL"), salary("202602", "OS"), salary("202603", "CT")));
        var result = reports.getCommonCashFlowStatements(new Filter(2026)).data();
        assertThat(result.months().get(0).contractFreelancePayroll()).isEqualByComparingTo("4835000");
        assertThat(result.months().get(1).outsourcing()).isEqualByComparingTo("8800000");
        assertThat(result.months().get(2).contractFreelancePayroll()).isEqualByComparingTo("4500000");
        var definition = definition("식-49");
        when(calculations.definition("식-49")).thenReturn(new Definition("식-49", definition.formula().replace("3.3%", "4%").replace("10%", "12%"), definition.details()));
        result = reports.getCommonCashFlowStatements(new Filter(2026)).data();
        assertThat(result.months().get(0).contractFreelancePayroll()).isEqualByComparingTo("4800000");
        assertThat(result.months().get(1).outsourcing()).isEqualByComparingTo("8960000");
    }
    @Test void financeCategoriesAreSingleCountedAndTaxOnlyMarchOrAugust() {
        when(finance.cashContributions()).thenReturn(List.of(contribution("202603", "OTHER_INCOME", "100"), contribution("202603", "LOAN", "-20"), contribution("202603", "EXPENSE", "50"), contribution("202603", "UNPAID_LABOR", "70"), contribution("202603", "CORPORATE_TAX", "90"), contribution("202604", "CORPORATE_TAX", "999")));
        when(source.miscellaneousIncome()).thenReturn(List.of(contribution("202603", "OTHER_INCOME", "110")));
        var result = reports.getCommonCashFlowStatements(new Filter(2026)).data();
        assertThat(result.months().get(2).incomeTotal()).isEqualByComparingTo("90");
        assertThat(result.months().get(2).expenseTotal()).isEqualByComparingTo("210");
        assertThat(result.months().get(3).corporateTaxPayment()).isZero();
    }
    @Test void whitespaceOnlyFormulaChangesAreAccepted() {
        var original = definition("식-46");
        when(calculations.definition("식-46")).thenReturn(new Definition("식-46", original.formula().replace(" ", "\n\t"), original.details()));
        assertThat(reports.getCommonCashFlowStatements(new Filter(2026)).data().months()).hasSize(12);
    }
    @Test void unsupportedBalanceMultiplierIsConfigurationError() throws Exception {
        when(calculations.definition("식-46")).thenReturn(new Definition("식-46", "이월금=전월이월금+수입−지출*2. 다음 달 전월이월금=이번 달 이월금", ""));
        mvc.perform(get(PATH).param("year", "2026")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("calcId"));
        verify(source, never()).insertAccess(anyString(), anyString());
    }
    @Test void inactiveDeveloperIsNotForecast() {
        when(source.payroll("202612")).thenReturn(List.of(salary("202610", "FT")));
        when(people.people(any())).thenReturn(List.of(new Person("developer", "이름", null, null, null, null, "FL", "프리랜서", null, null, null, null, null, null, null, null, null, null)));
        when(source.activeDevelopers()).thenReturn(List.of());
        assertThat(reports.getCommonCashFlowStatements(new Filter(2026)).data().months().get(10).contractFreelancePayroll()).isZero();
    }
    @Test void retiredDeveloperIsNotForecast() {
        when(source.payroll("202612")).thenReturn(List.of(salary("202610", "FT")));
        when(people.people(any())).thenReturn(List.of(new Person("developer", "이름", null, null, null, null, "FL", "프리랜서", null, java.time.LocalDate.of(2026, 10, 31), null, null, null, null, null, null, null, null)));
        when(source.activeDevelopers()).thenReturn(List.of("developer"));
        assertThat(reports.getCommonCashFlowStatements(new Filter(2026)).data().months().get(10).contractFreelancePayroll()).isZero();
    }
    @Test void forecastRetainsLatestUploadedAllowanceInsteadOfCurrentPositionAllowance() {
        var basis = new Pay(
            "202610", "developer", "FT", n("5000000"), n("150000"),
            n("100"), n("0"), n("200"), n("0"), n("400"),
            n("5149400"), n("0")
        );
        when(source.payroll("202612")).thenReturn(List.of(basis));
        when(source.activeDevelopers()).thenReturn(List.of("developer"));
        when(people.people(any())).thenReturn(List.of(new Person(
            "developer", "이름", null, null, "TEAM", null, "FT", "정규직",
            null, null, null, null, null, null, null, null, null, null
        )));
        var codes = new ArrayList<>(cashCodes());
        codes.add(new Code("ALWNC", "ALLOW_TEAM", "직위 수당", n("300000")));
        when(people.codes()).thenReturn(codes);

        var months = reports.getCommonCashFlowStatements(new Filter(2026)).data().months();
        BigDecimal expected = basis.salary().add(basis.allowance())
            .subtract(basis.personalInsurance()).subtract(basis.incomeTax());
        assertThat(months.get(9).regularPayroll()).isEqualByComparingTo(basis.netPay());
        assertThat(months.get(10).regularPayroll()).isEqualByComparingTo(expected);
        assertThat(months.get(11).regularPayroll()).isEqualByComparingTo(expected);
    }
    @Test void contractForecastRetainsApplicableDeductionBases() {
        when(source.payroll("202612")).thenReturn(List.of(salary("202610", "CT")));
        when(people.people(any())).thenReturn(List.of(new Person("developer", "이름", null, null, null, null, "CT", "계약직", null, null, null, null, null, null, null, null, null, null)));
        when(source.activeDevelopers()).thenReturn(List.of("developer"));
        when(calculations.effectiveStandard(eq("WTHLD_RT"), any())).thenReturn(new Standard("WTHLD_RT", YearMonth.of(2026, 1), n("3.3")));
        var months = reports.getCommonCashFlowStatements(new Filter(2026)).data().months();
        assertThat(months.get(10).contractFreelancePayroll()).isEqualByComparingTo("4834400");
        assertThat(months.get(10).overhead()).isEqualByComparingTo("1000");
        assertThat(months.get(11).overhead()).isEqualByComparingTo("165700");
    }
    @Test void unsupportedFormulaFailsWithConfigurationField() throws Exception {
        when(calculations.definition("식-51")).thenReturn(new Definition("식-51", "잔액 >= 기준", "변경"));
        mvc.perform(get(PATH).param("year", "2026")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("calcId"));
    }
    @Test void anonymousWorkbookIsActualXlsxWithSameRawRowsColumnsAndValues() throws Exception {
        when(source.receipts()).thenReturn(List.of(contribution("202601", "RECEIPT", "123")));
        var report = reports.getCommonCashFlowStatements(new Filter(2026)).data();
        byte[] bytes = mvc.perform(get(PATH + "/excel").param("year", "2026").param("amountUnit", "원"))
            .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().exists("Content-Disposition")).andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("자금수지표");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).contains("2026년", "단위: 원");
            for (int r = 0; r < 15; r++) {
                assertThat(sheet.getRow(r + 3).getCell(0).getStringCellValue()).isEqualTo(report.numericTable().rows().get(r).label());
                for (int c = 0; c < 15; c++) {
                    var expected = report.numericTable().rows().get(r).values().get(c);
                    var cell = sheet.getRow(r + 3).getCell(c + 1);
                    if (expected == null) assertThat(cell.getStringCellValue()).isEqualTo("–");
                    else assertThat(cell.getNumericCellValue()).isEqualTo(expected.doubleValue());
                }
            }
        }
        verify(source).insertAccess(anyString(), contains("자금수지표 2026 원"));
    }
    @Test void missingExportUnitIsFieldErrorAndNoAudit() throws Exception {
        mvc.perform(get(PATH + "/excel").param("year", "2026")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("amountUnit"));
        verifyNoInteractions(source);
    }
    @ParameterizedTest @ValueSource(strings = {"", "만원", "=HYPERLINK(1)"})
    void unsupportedExportUnitIsFieldErrorAndNoAudit(String unit) throws Exception {
        mvc.perform(get(PATH + "/excel").param("year", "2026").param("amountUnit", unit)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("amountUnit"));
        verifyNoInteractions(source);
    }
    @Test void workbookWithUnknownBaseHasDashBalancesButRealInflows() throws Exception {
        when(source.immediateStandard("CASH_BASE_AMT")).thenReturn(null);
        when(source.receipts()).thenReturn(List.of(contribution("202601", "RECEIPT", "123")));
        byte[] bytes = mvc.perform(get(PATH + "/excel").param("year", "2026").param("amountUnit", "원")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(book.getSheetAt(0).getRow(3).getCell(1).getStringCellValue()).isEqualTo("–");
            assertThat(book.getSheetAt(0).getRow(5).getCell(1).getNumericCellValue()).isEqualTo(123);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"원", "천 원", "백만 원"})
    void workbookMatchesEveryProjectedCellAndRoundsRawTotalsOnce(String unit) throws Exception {
        BigDecimal divisor = switch (unit) {
            case "천 원" -> n("1000");
            case "백만 원" -> n("1000000");
            default -> BigDecimal.ONE;
        };
        BigDecimal tie = "원".equals(unit) ? n("15") : divisor.multiply(n("1.5"));
        when(source.immediateStandard("CASH_BASE_AMT")).thenReturn(null);
        when(finance.cashContributions()).thenReturn(List.of(
            new CashContribution("202601", "LOAN", tie),
            new CashContribution("202602", "LOAN", tie.negate()),
            new CashContribution("202603", "LOAN", tie)));
        var table = reports.getCommonCashFlowStatements(new Filter(2026)).data().numericTable();
        byte[] bytes = mvc.perform(get(PATH + "/excel").param("year", "2026").param("amountUnit", unit))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).contains("단위: " + unit);
            for (int c = 0; c < 15; c++) {
                var column = table.columns().get(c);
                assertThat(sheet.getRow(2).getCell(c + 1).getStringCellValue()).isEqualTo(
                    column.label() + (column.actual() == null ? "" : column.actual() ? " 실적" : " 예정"));
            }
            for (int r = 0; r < 15; r++) {
                for (int c = 0; c < 15; c++) {
                    var raw = table.rows().get(r).values().get(c);
                    var cell = sheet.getRow(r + 3).getCell(c + 1);
                    if (raw == null) assertThat(cell.getStringCellValue()).isEqualTo("–");
                    else assertThat(cell.getNumericCellValue()).isEqualTo(raw.divide(divisor, 0,
                        raw.signum() < 0 ? java.math.RoundingMode.HALF_DOWN : java.math.RoundingMode.HALF_UP).doubleValue());
                }
            }
            var loan = sheet.getRow(7);
            assertThat(loan.getCell(1).getNumericCellValue()).isEqualTo("원".equals(unit) ? 15 : 2);
            assertThat(loan.getCell(2).getNumericCellValue()).isEqualTo("원".equals(unit) ? -15 : -1);
            assertThat(loan.getCell(15).getNumericCellValue()).isEqualTo("원".equals(unit) ? 15 : 2);
        }
        verify(source).insertAccess(anyString(), contains("자금수지표 2026 " + unit));
    }
    @ParameterizedTest @ValueSource(strings = {"word", "2026.5", "0", "10000"})
    void invalidExportYearReturnsYearErrorBeforeReadingSources(String year) throws Exception {
        mvc.perform(get(PATH + "/excel").param("year", year).param("amountUnit", "원"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("year"));
        verifyNoInteractions(source, finance);
    }
    @Test void missingExportYearReturnsYearErrorWithoutAudit() throws Exception {
        mvc.perform(get(PATH + "/excel").param("amountUnit", "원"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("year"));
        verifyNoInteractions(source, finance);
    }
    @Test void workbookFailureDoesNotAuditOrReturnSuccess() throws Exception {
        doThrow(new IllegalStateException("serialization")).when(writer).write(anyString(), anyList(), anyList(), anyString(), any());
        mvc.perform(get(PATH + "/excel").param("year", "2026").param("amountUnit", "원")).andExpect(status().is5xxServerError());
        verify(source, never()).insertAccess(anyString(), anyString());
    }
    @Test void auditFailureDoesNotReturnSuccessfulWorkbook() throws Exception {
        when(source.insertAccess(anyString(), anyString())).thenReturn(0);
        mvc.perform(get(PATH + "/excel").param("year", "2026").param("amountUnit", "원")).andExpect(status().is5xxServerError());
    }
}
