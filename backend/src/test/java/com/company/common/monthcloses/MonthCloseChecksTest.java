package com.company.common.monthcloses;

import com.company.common.assignments.mapper.AssignmentMapper;
import com.company.common.assignments.mapper.AssignmentRows.Row;
import com.company.common.assignments.service.AssignmentMonthRules;
import com.company.common.billchecks.dto.BillCheckContracts.*;
import com.company.common.billchecks.service.BillCheckService;
import com.company.common.common.MonthCloseCodes;
import com.company.common.commonops.dto.PartSettlementCloseContracts;
import com.company.common.commonops.service.PartSettlementCloseService;
import com.company.common.monthcloses.dto.MonthCloseContracts.*;
import com.company.common.monthcloses.mapper.MonthCloseMapper;
import com.company.common.monthcloses.service.MonthCloseAssignmentService;
import com.company.common.monthcloses.service.MonthCloseCheckService;
import com.company.common.support.service.BusinessCalendar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonthCloseChecksTest {
    @Test void aggregateMmPerPersonInsteadOfCountingAssignmentRows() {
        var mapper = mock(AssignmentMapper.class);
        var rules = mock(AssignmentMonthRules.class);
        when(mapper.rows()).thenReturn(List.of(assignment("one", "person", "0.6"), assignment("two", "person", "0.6"), assignment("three", "other", "0.9")));
        when(mapper.history(any())).thenReturn(List.of());
        when(rules.effective(any(), any(), any(), any())).thenAnswer(call -> call.getArgument(3));
        when(rules.mm(any(), any(), any(), any())).thenAnswer(call -> call.getArgument(0));
        when(rules.sales(any(), any(), anyBoolean())).thenReturn(BigDecimal.ZERO);
        when(rules.over(any())).thenAnswer(call -> ((BigDecimal) call.getArgument(0)).compareTo(BigDecimal.ONE) > 0);
        var service = new MonthCloseAssignmentService(mapper, rules, new BusinessCalendar());
        assertThat(service.overCount(YearMonth.of(2026, 1))).isEqualTo(1);
    }
    Row assignment(String id, String person, String mm) {
        return new Row(id, "project", person, "job", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
            new BigDecimal(mm), BigDecimal.TEN, "NEW", null, null, 0, "N", "ACT", null,
            "프로젝트", "인력", person, "직무", "MO", "ING");
    }
    @Test void exactlySixOrderedChecksUseLiveSourcesAndZeroReferencesStayNormal() {
        var mapper = mock(MonthCloseMapper.class);
        var codes = mock(MonthCloseCodes.class);
        var assignments = mock(MonthCloseAssignmentService.class);
        var settlements = mock(PartSettlementCloseService.class);
        var bills = mock(BillCheckService.class);
        when(codes.name(any(), any())).thenAnswer(call -> call.getArgument(1));
        when(mapper.selectCounts("202601")).thenReturn(new Counts(0, 2, 0));
        when(assignments.overCount(YearMonth.of(2026, 1))).thenReturn(1);
        when(settlements.check("2026-01")).thenReturn(new PartSettlementCloseContracts.Check("2026-01", 2, 1, List.of()));
        when(bills.getCommonBillChecks("202601")).thenReturn(new CheckResult("202601",
            new Summary(1, 1, 0, 0, 0), List.of(new CheckRow("project", "프로젝트", "MO", "청구 없음",
                1, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, "누락", null, null)),
            "DB 식-88", false, List.of("202601"), null));
        var service = new MonthCloseCheckService(mapper, codes, assignments, settlements, bills);
        var result = service.calculate(YearMonth.of(2026, 1));
        assertThat(result.checks()).extracting(Check::name).containsExactly("PAY", "MM_OVER", "BILL", "FI", "SETL", "BILL_MISS");
        assertThat(result.checks()).extracting(Check::status).containsExactly("OK", "WARN", "WARN", "OK", "WARN", "WARN");
        assertThat(result.remaining()).containsExactly("MM_OVER", "BILL", "SETL", "BILL_MISS");
        assertThat(result.checks()).extracting(Check::targetScreenId).containsExactly(null, "UI-12", "UI-13", null, "UI-19", "UI-13");
        assertThat(result.checks().get(0).description()).isEqualTo("0명 업로드 완료");
        assertThat(result.checks().get(3).description()).isEqualTo("0건 입력");
        verify(mapper).selectCounts("202601"); verifyNoMoreInteractions(mapper);
    }
}
