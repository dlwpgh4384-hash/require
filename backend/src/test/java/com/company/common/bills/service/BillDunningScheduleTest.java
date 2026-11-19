package com.company.common.bills.service;

import com.company.common.bills.mapper.BillMapper;
import com.company.common.bills.mapper.BillRows.*;
import com.company.common.common.BillingCalculationRules;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.support.service.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BillDunningScheduleTest {
    @Test void failureForOneBillDoesNotStopFollowingBills() {
        var mapper=mock(BillMapper.class);var service=mock(BillDunningService.class);
        when(mapper.outstandingIds()).thenReturn(List.of("first","second"));
        when(service.sendAutomatic("first")).thenThrow(CodeFailure.invalid("delivery","失敗"));
        new BillDunningSchedule(mapper,service).notifyOutstanding();
        verify(service).sendAutomatic("second");
    }
    @Test void automaticAndResendSuccessAreIndependentAndAutomaticIsOncePerStage() {
        var mapper=mock(BillMapper.class);var projects=mock(ProjectMapper.class);var rules=mock(BillingCalculationRules.class);
        var calendar=mock(BusinessCalendar.class);var mailbox=mock(BillLocalMailbox.class);
        var service=new BillDunningService(mapper,projects,rules,calendar,new CurrentBusinessActor(),mailbox);
        var today=LocalDate.of(2026,11,15);
        when(calendar.today()).thenReturn(today);when(calendar.currentMonth()).thenReturn(YearMonth.of(2026,11));when(calendar.toDatabaseMonth(any())).thenReturn("202611");
        var invoice=new Invoice("bill","project",1,"202611",today,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.ONE,new BigDecimal("11"),BigDecimal.ZERO,new BigDecimal("11"),today.minusDays(15),null,false,null,null,null,"project","customer","MO","ING",today.minusYears(1),today.plusYears(1),true,false,0,null);
        when(mapper.lockInvoice("bill")).thenReturn(invoice);when(mapper.normalReceipts("bill")).thenReturn(BigDecimal.ZERO);
        when(rules.balance(any(),any())).thenReturn(new BigDecimal("11"));when(rules.dunningStep(anyBoolean(),any(),any(),any())).thenReturn(2);
        when(mapper.recipients(any())).thenReturn(List.of(new Recipient("admin","관리자","admin@example.test")));
        when(mapper.notification(any())).thenReturn(1);when(mapper.history(any())).thenReturn(1);when(mailbox.deliver(anyList(),anyString())).thenReturn(Path.of("accepted.eml"));
        assertThat(service.sendAutomatic("bill").history().resend()).isFalse();
        when(mapper.automaticallySent("bill",2)).thenReturn(true);
        assertThat(service.sendAutomatic("bill")).isNull();
        assertThat(service.postCommonBillDunningResend("bill").history().resend()).isTrue();
        verify(mapper,times(2)).history(any());verify(mailbox,times(2)).deliver(anyList(),anyString());
    }
}
