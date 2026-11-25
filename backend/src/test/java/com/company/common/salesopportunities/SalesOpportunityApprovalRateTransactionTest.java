package com.company.common.salesopportunities;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.SalesPipelineApprovalStandards;
import com.company.common.common.SalesPipelineDefinitions;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.salesopportunities.mapper.SalesOpportunityMapper;
import com.company.common.salesopportunities.service.SalesOpportunityCalculations;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 대시보드는 한 읽기 트랜잭션 안에서 견적 승인 대기(WAIT) 기회마다 영업 기회 상세를 읽는다. 견적 승인 기준값이 없을 때
 * 상세의 「(기준 n%)」는 null이어야 하고, 바깥 트랜잭션이 롤백 전용이 되어 끝에 500(UnexpectedRollbackException)이
 * 나면 안 된다.
 */
class SalesOpportunityApprovalRateTransactionTest {
    private AnnotationConfigApplicationContext context;
    private CalculationRuleMapper mapper;

    @BeforeEach void start() {
        context = new AnnotationConfigApplicationContext(Config.class);
        mapper = context.getBean(CalculationRuleMapper.class);
        when(context.getBean(BusinessCalendar.class).currentMonth()).thenReturn(YearMonth.of(2026, 11));
        when(mapper.selectEffectiveStandard(anyString(), anyString())).thenReturn(null);
    }
    @AfterEach void stop() { context.close(); }

    @Test void missingStandardInsideOuterReadTransactionGivesNullRateAndCommits() {
        var outer = context.getBean(DashboardLikeRead.class);
        assertThat(outer.pendingQuoteRate()).isNull();
        verify(mapper).selectEffectiveStandard("QUOTE_APRV_RT", "202611");
    }

    @Test void throwingLookupCaughtInsideOuterTransactionWouldFailAtCommit() {
        // 시험 틀 확인: 고치기 전 방식(던지는 effective를 잡기)은 바깥 트랜잭션 끝에서 실패한다.
        var outer = context.getBean(DashboardLikeRead.class);
        assertThatThrownBy(outer::oldCatchingLookup).isInstanceOf(UnexpectedRollbackException.class);
    }

    @Test void presentStandardsStillGiveRate() {
        var row = new com.company.common.calculation.mapper.CalculationStandardRow();
        row.standardCode = "QUOTE_APRV_RT"; row.effectiveMonth = "202611"; row.value = new BigDecimal("15");
        var gap = new com.company.common.calculation.mapper.CalculationStandardRow();
        gap.standardCode = "QUOTE_REAPRV_GAP"; gap.effectiveMonth = "202611"; gap.value = new BigDecimal("0.5");
        when(mapper.selectEffectiveStandard("QUOTE_APRV_RT", "202611")).thenReturn(row);
        when(mapper.selectEffectiveStandard("QUOTE_REAPRV_GAP", "202611")).thenReturn(gap);
        assertThat(context.getBean(DashboardLikeRead.class).pendingQuoteRate()).isEqualByComparingTo("15");
    }

    public static class DashboardLikeRead {
        private final SalesOpportunityCalculations calculations;
        private final SalesPipelineApprovalStandards approvals;
        public DashboardLikeRead(SalesOpportunityCalculations calculations, SalesPipelineApprovalStandards approvals) {
            this.calculations = calculations; this.approvals = approvals;
        }
        @Transactional(readOnly = true)
        public BigDecimal pendingQuoteRate() { return calculations.approvalRate(); }
        @Transactional(readOnly = true)
        public BigDecimal oldCatchingLookup() {
            try { return approvals.effective(YearMonth.of(2026, 11)).approvalRate(); }
            catch (CodeFailure failure) { return null; }
        }
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean PlatformTransactionManager transactionManager() { return new FlagTransactionManager(); }
        @Bean CalculationRuleMapper calculationRuleMapper() { return mock(CalculationRuleMapper.class); }
        @Bean UserPreferencePeriodSourceMapper periods() { return mock(UserPreferencePeriodSourceMapper.class); }
        @Bean BusinessCalendar calendar() { return mock(BusinessCalendar.class); }
        @Bean CalculationRuleService calculationRuleService(CalculationRuleMapper mapper, UserPreferencePeriodSourceMapper periods) {
            return new CalculationRuleService(mapper, periods);
        }
        @Bean SalesPipelineApprovalStandards approvals(CalculationRuleService standards) {
            return new SalesPipelineApprovalStandards(standards);
        }
        @Bean SalesOpportunityCalculations calculations(SalesPipelineApprovalStandards approvals, CalculationRuleService standards,
            BusinessCalendar calendar) {
            return new SalesOpportunityCalculations(mock(SalesPipelineDefinitions.class), approvals, standards,
                mock(SalesOpportunityMapper.class), calendar);
        }
        @Bean DashboardLikeRead dashboardLikeRead(SalesOpportunityCalculations calculations, SalesPipelineApprovalStandards approvals) {
            return new DashboardLikeRead(calculations, approvals);
        }
    }

    /** 실제 DB 없이 참여 트랜잭션의 롤백 전용 표시만 흉내 내는 관리자. */
    static class FlagTransactionManager extends AbstractPlatformTransactionManager {
        static final class Tx implements SmartTransactionObject {
            boolean rollbackOnly;
            @Override public boolean isRollbackOnly() { return rollbackOnly; }
            @Override public void flush() {}
        }
        private final ThreadLocal<Tx> current = new ThreadLocal<>();
        @Override protected Object doGetTransaction() { Tx tx = current.get(); return tx == null ? new Tx() : tx; }
        @Override protected boolean isExistingTransaction(Object transaction) { return current.get() == transaction; }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { current.set((Tx) transaction); }
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
        @Override protected void doSetRollbackOnly(DefaultTransactionStatus status) { ((Tx) status.getTransaction()).rollbackOnly = true; }
        @Override protected void doCleanupAfterCompletion(Object transaction) { current.remove(); }
    }
}
