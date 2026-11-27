package com.company.common.dailybatch;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.company.common.assignments.service.CommonAssignmentService;
import com.company.common.calculation.dto.CalculationRuleContracts.Standard;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.cashflowstatements.dto.CashFlowStatementContracts.*;
import com.company.common.cashflowstatements.service.CommonCashFlowStatementService;
import com.company.common.dailybatch.mapper.DailyBatchMapper;
import com.company.common.dailybatch.service.DailyBatch;
import com.company.common.dailybatch.service.DailyBatchSchedule;
import com.company.common.developers.service.CommonDeveloperService;
import com.company.common.notifications.service.NotificationPublisher;
import com.company.common.notifications.service.NotificationPublisher.WithdrawalTarget;
import com.company.common.notifications.service.NotificationRetention;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.users.dto.UserContracts.Response;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 일 배치 8단계 순서·단계별 로그·한 단계 실패에도 계속·매달 1일만 ④·02:00 한국 시각 하나(명세 ⑪). */
class DailyBatchTest {
    CommonDeveloperService developers = mock(CommonDeveloperService.class);
    CommonAssignmentService assignments = mock(CommonAssignmentService.class);
    CommonCashFlowStatementService cash = mock(CommonCashFlowStatementService.class);
    CalculationRuleService standards = mock(CalculationRuleService.class);
    NotificationPublisher publisher = mock(NotificationPublisher.class);
    NotificationRetention retention = mock(NotificationRetention.class);
    DailyBatchMapper mapper = mock(DailyBatchMapper.class);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    DailyBatch batch;
    ListAppender<ILoggingEvent> logs = new ListAppender<>();
    Logger logger = (Logger) LoggerFactory.getLogger(DailyBatch.class);

    @BeforeEach void setup() {
        logs.start();
        logger.addAppender(logs);
        batch = new DailyBatch(developers, assignments, cash, standards, publisher, retention, mapper,
            new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-26T17:00:00Z"), ZoneOffset.UTC)), transactions);
        when(standards.effectiveStandard(eq("WTHDR_NOTI_D"), any()))
            .thenReturn(new Standard("WTHDR_NOTI_D", YearMonth.of(2026, 1), new BigDecimal("30")));
        var minimum = new BigDecimal("1000000000");
        when(cash.getCommonCashFlowStatements(any())).thenReturn(new Response<>(true, Map.of(),
            new Result(2026, minimum, List.of(), List.of(new Warning("202612", BigDecimal.ONE, minimum)), null)));
        when(mapper.activeUsers()).thenReturn(List.of("fin", "pm"));
        when(mapper.dueWithdrawals(LocalDate.of(2026, 12, 27))).thenReturn(List.of(new WithdrawalTarget("a1", "d1")));
    }

    @AfterEach void detach() { logger.detachAppender(logs); }

    @Test void runsEightStepsInSpecOrderAndLogsEachStep() {
        var report = batch.run(LocalDate.of(2026, 11, 27));
        assertThat(report.steps()).extracting(DailyBatch.Step::number).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(report.steps()).extracting(DailyBatch.Step::name).containsExactly("근무 상태",
            "퇴사일 지난 개발자 사용 중지", "계약 종료 지난 진행 프로젝트 종료", "투입 MM·단가(매달 1일)", "철수 알림",
            "자금 부족 알림", "30일 지난 알림 삭제", "보관 기간 지난 접근·변경 기록 정리");
        assertThat(report.succeeded()).isTrue();
        var order = inOrder(developers, mapper, publisher, retention);
        order.verify(developers).refreshAll();
        order.verify(mapper).stopRetiredDevelopers(LocalDate.of(2026, 11, 27));
        order.verify(mapper).endExpiredProjects(LocalDate.of(2026, 11, 27));
        order.verify(publisher).withdrawalSummary(eq("fin"), eq(LocalDate.of(2026, 12, 27)), eq(30), anyList());
        order.verify(publisher).cashShortage(eq("fin"), eq("202612"), any());
        order.verify(retention).purgeExpired();
        order.verify(mapper).purgeAccessLogs(Instant.parse("2023-11-26T17:00:00Z"));
        verify(assignments, never()).refreshCurrentMonth();
        // 단계마다 따로 트랜잭션
        verify(transactions, times(8)).getTransaction(any());
        var messages = logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        for (int step = 1; step <= 8; step++) {
            int n = step;
            assertThat(messages).anyMatch(m -> m.startsWith("[일 배치] " + n + "단계 ") && m.contains("성공"));
        }
        assertThat(messages.get(0)).isEqualTo("[일 배치] 시작 (기준일 2026-11-27)");
    }

    @Test void monthlyAssignmentRefreshRunsOnlyOnTheFirst() {
        batch.run(LocalDate.of(2026, 12, 1));
        verify(assignments).refreshCurrentMonth();
    }

    @Test void failedStepIsLoggedWithoutDataAndLaterStepsStillRun() {
        doThrow(new IllegalStateException("민감한 값 12345")).when(developers).refreshAll();
        when(mapper.endExpiredProjects(any())).thenThrow(new RuntimeException("secret"));
        var report = batch.run(LocalDate.of(2026, 11, 27));
        assertThat(report.succeeded()).isFalse();
        assertThat(report.steps()).filteredOn(s -> !s.succeeded()).extracting(DailyBatch.Step::number).containsExactly(1, 3);
        verify(retention).purgeExpired();
        verify(mapper).purgeChangeHistory(any());
        verify(transactions, times(2)).rollback(any());
        var text = logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(text).anyMatch(m -> m.contains("1단계 근무 상태 실패 (IllegalStateException)"));
        assertThat(String.join("\n", text)).doesNotContain("12345").doesNotContain("secret");
    }

    @Test void noDueAssignmentCreatesNoWithdrawalNotification() {
        when(mapper.dueWithdrawals(any())).thenReturn(List.of());
        assertThat(batch.run(LocalDate.of(2026, 11, 27)).steps().get(4).detail()).isEqualTo("종료일 2026-12-27 투입 0건");
        verify(publisher, never()).withdrawalSummary(any(), any(), anyInt(), any());
    }

    @Test void oneScheduleAtTwoInSeoulRunsTheWholeBatch() throws Exception {
        var schedule = DailyBatchSchedule.class.getMethod("run").getAnnotation(Scheduled.class);
        assertThat(schedule.cron()).isEqualTo("0 0 2 * * *");
        assertThat(schedule.zone()).isEqualTo("Asia/Seoul");
        var mocked = mock(DailyBatch.class);
        new DailyBatchSchedule(mocked).run();
        verify(mocked).run();
    }
}
