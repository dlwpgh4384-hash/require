package com.company.common.dailybatch.service;

import com.company.common.assignments.service.CommonAssignmentService;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.cashflowstatements.dto.CashFlowStatementContracts.Filter;
import com.company.common.cashflowstatements.service.CommonCashFlowStatementService;
import com.company.common.dailybatch.mapper.DailyBatchMapper;
import com.company.common.developers.service.CommonDeveloperService;
import com.company.common.notifications.service.NotificationPublisher;
import com.company.common.notifications.service.NotificationRetention;
import com.company.common.support.service.BusinessCalendar;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 일 배치(PER-004, 명세 ⑪): 매일 02:00(한국 시각) 하나로 차례대로 돈다.
 * ① 근무 상태 ② 퇴사일 지난 개발자 사용 중지 ③ 계약 종료일 지난 진행 프로젝트 종료 ④ (매달 1일만) 투입 MM·단가
 * ⑤ 철수 n일 전 알림 ⑥ 자금 부족 예상 알림 ⑦ 30일 지난 알림 삭제 ⑧ 보관 기간 지난 접근·변경 기록 정리.
 * 단계마다 따로 저장(트랜잭션)해 한 단계가 실패해도 다음 단계는 돌고, 실패는 서버 로그에 남긴다(자동 재시도 없음).
 * 같은 날 다시 돌려도 알림은 늘지 않는다(⑤·⑥ 하루 1번 검사, 나머지 단계는 이미 바뀐 줄을 다시 바꾸지 않음).
 * 앱 밖에서 한 번 돌리기: {@code java -jar app.jar --run-daily-batch[=YYYY-MM-DD]}(NavigationApplication).
 */
@Service
public class DailyBatch {
    private static final Logger LOG = LoggerFactory.getLogger(DailyBatch.class);
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    /** 단계 결과(로그·명령 출력용). detail에는 건수만 넣고 사람·금액 값은 넣지 않는다. */
    public record Step(int number, String name, boolean succeeded, String detail) {}
    public record Report(LocalDate day, List<Step> steps) {
        public boolean succeeded() { return steps.stream().allMatch(Step::succeeded); }
    }

    private final CommonDeveloperService developers;
    private final CommonAssignmentService assignments;
    private final CommonCashFlowStatementService cash;
    private final CalculationRuleService standards;
    private final NotificationPublisher notifications;
    private final NotificationRetention retention;
    private final DailyBatchMapper mapper;
    private final BusinessCalendar calendar;
    private final TransactionTemplate transaction;

    public DailyBatch(CommonDeveloperService developers, CommonAssignmentService assignments,
        CommonCashFlowStatementService cash, CalculationRuleService standards,
        NotificationPublisher notifications, NotificationRetention retention, DailyBatchMapper mapper,
        BusinessCalendar calendar, PlatformTransactionManager transactions) {
        this.developers = developers;
        this.assignments = assignments;
        this.cash = cash;
        this.standards = standards;
        this.notifications = notifications;
        this.retention = retention;
        this.mapper = mapper;
        this.calendar = calendar;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 오늘(한국 날짜) 기준으로 돈다. */
    public Report run() { return run(calendar.today()); }

    /**
     * day: 날짜로 정하는 단계(②·③·④ 1일 여부·⑤ 철수 종료일)의 기준일. ①·④의 계산, ⑥ 자금수지, ⑦·⑧ 보관 기간은
     * 각 부품이 지금 시각(업무 달력·DB 시각)을 그대로 쓴다.
     */
    public Report run(LocalDate day) {
        LOG.info("[일 배치] 시작 (기준일 {})", day);
        var steps = new ArrayList<Step>();
        steps.add(step(1, "근무 상태", () -> { developers.refreshAll(); return "끝"; }));
        steps.add(step(2, "퇴사일 지난 개발자 사용 중지", () -> mapper.stopRetiredDevelopers(day) + "명"));
        steps.add(step(3, "계약 종료 지난 진행 프로젝트 종료", () -> mapper.endExpiredProjects(day) + "건"));
        steps.add(step(4, "투입 MM·단가(매달 1일)", () -> {
            if (day.getDayOfMonth() != 1) return "1일이 아니라 건너뜀";
            assignments.refreshCurrentMonth();
            return "끝";
        }));
        steps.add(step(5, "철수 알림", () -> withdrawals(day)));
        steps.add(step(6, "자금 부족 알림", () -> cashShortages(day)));
        steps.add(step(7, "30일 지난 알림 삭제", () -> retention.purgeExpired() + "건"));
        steps.add(step(8, "보관 기간 지난 접근·변경 기록 정리", () -> {
            var cutoff = calendar.now().atZone(KOREA).minusYears(3).toInstant();
            return "접근 " + mapper.purgeAccessLogs(cutoff) + "건, 변경 " + mapper.purgeChangeHistory(cutoff) + "건";
        }));
        var report = new Report(day, List.copyOf(steps));
        long failed = steps.stream().filter(s -> !s.succeeded()).count();
        if (failed == 0) LOG.info("[일 배치] 끝 (기준일 {}, 8단계 모두 성공)", day);
        else LOG.warn("[일 배치] 끝 (기준일 {}, 실패 {}단계)", day, failed);
        return report;
    }

    private String withdrawals(LocalDate day) {
        var days = standards.effectiveStandard("WTHDR_NOTI_D", YearMonth.from(day)).value();
        if (days == null) throw new IllegalStateException("WTHDR_NOTI_D");
        int n = days.intValueExact();
        LocalDate endDate = day.plusDays(n);
        var due = mapper.dueWithdrawals(endDate);
        if (due.isEmpty()) return "종료일 " + endDate + " 투입 0건";
        int created = 0;
        for (String user : mapper.activeUsers())
            if (notifications.withdrawalSummary(user, endDate, n, due)) created++;
        return "종료일 " + endDate + " 투입 " + due.size() + "건, 알림 " + created + "건";
    }

    private String cashShortages(LocalDate day) {
        var result = cash.getCommonCashFlowStatements(new Filter(day.getYear())).data();
        if (result.warnings().isEmpty()) return "부족 예상 달 없음";
        var users = mapper.activeUsers();
        int created = 0;
        for (var warning : result.warnings())
            for (String user : users)
                if (notifications.cashShortage(user, warning.yearMonth(), result.minimumCash())) created++;
        return "부족 예상 " + result.warnings().size() + "달, 알림 " + created + "건";
    }

    private Step step(int number, String name, Supplier<String> work) {
        long started = System.nanoTime();
        try {
            String detail = transaction.execute(status -> work.get());
            LOG.info("[일 배치] {}단계 {} 성공 ({}ms): {}", number, name, (System.nanoTime() - started) / 1_000_000, detail);
            return new Step(number, name, true, detail);
        } catch (RuntimeException failure) {
            // 예외 글에는 자료 값이 섞일 수 있어 종류만 남긴다.
            LOG.warn("[일 배치] {}단계 {} 실패 ({}): 다음 단계는 계속합니다.", number, name, failure.getClass().getSimpleName());
            return new Step(number, name, false, "실패 " + failure.getClass().getSimpleName());
        }
    }
}
