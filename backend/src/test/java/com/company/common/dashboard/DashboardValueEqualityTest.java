package com.company.common.dashboard;

import com.company.common.NavigationApplication;
import com.company.common.bills.dto.BillContracts;
import com.company.common.bills.service.CommonBillService;
import com.company.common.cashflowstatements.dto.CashFlowStatementContracts;
import com.company.common.cashflowstatements.service.CommonCashFlowStatementService;
import com.company.common.commonops.dto.BusinessPlanComparisonContracts;
import com.company.common.commonops.service.CommonBusinessPlanComparisonService;
import com.company.common.dashboard.dto.DashboardContracts;
import com.company.common.dashboard.service.CommonDashboardService;
import com.company.common.manpowerboard.dto.ManpowerBoardContracts;
import com.company.common.manpowerboard.service.CommonManpowerBoardService;
import com.company.common.manpowerforecasts.service.CommonManpowerForecastService;
import com.company.common.operatingprofitstatements.dto.OperatingProfitStatementContracts;
import com.company.common.operatingprofitstatements.service.CommonOperatingProfitStatementService;
import com.company.common.partsettlements.service.CommonPartSettlementService;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.projects.service.CommonProjectService;
import com.company.common.schema.SchemaPreparation;
import com.company.common.withdrawals.dto.WithdrawalContracts;
import com.company.common.withdrawals.service.CommonWithdrawalService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

/**
 * 값 일치(SFR-116-02, 명세 0장 합격 기준 ①): 같은 자료로 원래 화면의 서버 계산과 대시보드 값을 직접 비교한다.
 * 실제 DB의 독립 스키마에 준비 명령과 같은 처음 값을 넣고, 앱 전체 빈(웹 서버·예약 작업 없이)으로 부른다.
 */
@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@SpringBootTest(classes = NavigationApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DashboardValueEqualityTest {
    static final String SCHEMA = "dashboard_eq_" + UUID.randomUUID().toString().replace("-", "");
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));
    static final YearMonth CURRENT = YearMonth.from(TODAY);

    @Autowired CommonDashboardService dashboard;
    @Autowired CommonOperatingProfitStatementService profits;
    @Autowired CommonBusinessPlanComparisonService plans;
    @Autowired CommonCashFlowStatementService cashFlows;
    @Autowired CommonWithdrawalService withdrawals;
    @Autowired CommonManpowerForecastService forecasts;
    @Autowired CommonManpowerBoardService board;
    @Autowired CommonBillService bills;
    @Autowired CommonPartSettlementService parts;
    @Autowired CommonProjectService projects;
    @Autowired UserPreferencePeriodBasis periods;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
        var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", SCHEMA);
        source.setDriverProperties(properties);
        new SchemaPreparation(new SqlSessionFactoryBuilder().build(
            new Configuration(new Environment("postgresql", new JdbcTransactionFactory(), source)))).prepare();
        seed(new JdbcTemplate(source));
        registry.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
    }

    /** 오늘 기준 자료: 직접 인력 2명(1명 투입·1명 대기), 다음 달 끝나는 배정, 지난해 연체 청구와 올해 미수 청구, 파트 미지정 프로젝트. */
    static void seed(JdbcTemplate db) {
        String ym = CURRENT.toString().replace("-", "");
        db.update("INSERT INTO TB_BS_PART(PART_ID,PART_NM,REG_USER_ID) VALUES('eq-part','값 일치 파트','admin')");
        db.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID,REG_DTTM) "
            + "VALUES('eq-proj','값 일치 프로젝트','MO','ING',?,?,100000000,0,0,'admin',clock_timestamp())",
            LocalDate.of(TODAY.getYear() - 1, 1, 1), LocalDate.of(TODAY.getYear() + 1, 12, 31));
        db.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID,REG_DTTM) "
            + "VALUES('eq-internal','값 일치 내부','IN','ING',?,?,0,0,0,'admin',clock_timestamp())",
            LocalDate.of(TODAY.getYear(), 1, 1), LocalDate.of(TODAY.getYear() + 1, 12, 31));
        for (String id : new String[]{"eq-dev1", "eq-dev2"})
            db.update("INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,JOB_CD,DRCT_CLS_CD,PART_ID,WORK_STAT_CD,JNCMP_DT,REG_USER_ID,REG_DTTM) "
                + "VALUES(?,?,?,'FT','BE','DRCT','eq-part','WORK',?,'admin',clock_timestamp())",
                id, id, id, LocalDate.of(TODAY.getYear() - 1, 1, 1));
        db.update("INSERT INTO TB_PJ_ASGN(ASGN_ID,PROJ_ID,DEV_ID,JOB_CD,INPUT_BGN_DT,INPUT_END_DT,CTRT_MM,INPUT_TYPE_CD,EXT_CNT,ASGN_STAT_CD,REG_USER_ID,REG_DTTM) "
            + "VALUES('eq-asg','eq-proj','eq-dev1','BE',?,?,1,'PAID',0,'ACT','admin',clock_timestamp())",
            CURRENT.atDay(1), CURRENT.plusMonths(1).atEndOfMonth());
        db.update("INSERT INTO TB_PJ_ASGN_MON(ASGN_ID,INPUT_YM,DEV_ID,PROJ_ID,INPUT_DAY_CNT,MON_MM,APLY_UPRC,SALES_AMT,REG_USER_ID,REG_DTTM) "
            + "VALUES('eq-asg',?,'eq-dev1','eq-proj',20,1,7000000,7000000,'admin',clock_timestamp())", ym);
        var lastYear = YearMonth.of(TODAY.getYear() - 1, 12);
        db.update("INSERT INTO TB_BL_BILL(BILL_ID,PROJ_ID,BILL_ORD,BILL_YM,BILL_DT,SPLY_AMT,VAT_RT,VAT_AMT,TOT_AMT,RCPT_SUM_AMT,UNPAID_AMT,PAY_DUE_DT,REG_USER_ID,REG_DTTM) "
            + "VALUES('eq-late','eq-proj',1,?,?,1000000,10,100000,1100000,0,1100000,?,'admin',clock_timestamp())",
            lastYear.toString().replace("-", ""), lastYear.atDay(15), lastYear.plusMonths(1).atDay(25));
        db.update("INSERT INTO TB_BL_BILL(BILL_ID,PROJ_ID,BILL_ORD,BILL_YM,BILL_DT,SPLY_AMT,VAT_RT,VAT_AMT,TOT_AMT,RCPT_SUM_AMT,UNPAID_AMT,PAY_DUE_DT,REG_USER_ID,REG_DTTM) "
            + "VALUES('eq-open','eq-proj',2,?,?,2000000,10,200000,2200000,0,2200000,?,'admin',clock_timestamp())",
            ym, TODAY, TODAY.plusDays(40));
    }

    @AfterAll
    static void dropSchema() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("NAVIGATION_TEST_DB_URL"),
                System.getenv("NAVIGATION_TEST_DB_USER"), System.getenv("NAVIGATION_TEST_DB_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    /** 고를 수 있는 가장 늦은 달(기간 기준 latestYm, 마감이 없으면 시작 월). 다른 월 화면과 같은 규칙. */
    YearMonth selected() {
        var basis = (UserPreferencePeriodBasis.Basis) periods.metadata().get("periodBasis");
        return YearMonth.of(Integer.parseInt(basis.latestYm().substring(0, 4)), Integer.parseInt(basis.latestYm().substring(4)));
    }

    DashboardContracts.Result read() { return dashboard.getCommonDashboard(selected().toString()).data(); }

    @Test void summaryEqualsOperatingProfitStatementAndBusinessPlan() {
        var result = read();
        var month = selected();
        String key = month.toString().replace("-", "");
        var report = profits.getCommonOperatingProfitStatements(new OperatingProfitStatementContracts.Filter(month.getYear(), key)).data();
        var plan = plans.getCommonBusinessPlanComparisons(new BusinessPlanComparisonContracts.Filter(month.getYear(), key)).data();
        int index = month.getMonthValue() - 1;
        var s = result.summary();
        assertSame(s.revenue(), line(report, "매출액").values().get(index).value());
        assertSame(s.operatingProfit(), line(report, "영업이익").values().get(index).value());
        assertSame(s.operatingProfitRate(), line(report, "영업이익률").values().get(index).value());
        assertSame(s.cumulativeOperatingProfit(), report.summary().cumulativeOperatingProfit().amount());
        assertSame(s.cumulativeAchievementRate(), plan.summary().cumulativeOperatingProfitAchievementRate());
        assertSame(s.revenueTargetRate(), plan.summary().baseMonthRevenueAchievementRate());
        for (int i = 0; i <= index; i++) {
            assertSame(result.trend().get(i).revenue(), line(report, "매출액").values().get(i).value());
            assertSame(result.trend().get(i).cost(), line(report, "매출원가").values().get(i).value());
        }
        assertThat(result.trend()).hasSize(12);
    }

    @Test void unpaidEqualsBillServiceAllYearsAndLateStatus() {
        var s = read().summary();
        assertThat(s.unpaidCount()).isEqualTo(bills.getCommonBillUnpaidCount().count()).isEqualTo(2);
        BigDecimal total = BigDecimal.ZERO;
        int late = 0;
        for (int year = TODAY.getYear() - 1; year <= TODAY.getYear(); year++) {
            var list = bills.getCommonBills(new BillContracts.Filter(year, null, null, null));
            total = total.add(list.summary().unpaidTotal());
            late += list.summary().overdueCount();
        }
        assertThat(s.unpaidAmount()).isEqualByComparingTo(total).isEqualByComparingTo("3300000");
        assertThat(s.overdueCount()).isEqualTo(late).isEqualTo(1);
        assertThat(s.overdueAmount()).isEqualByComparingTo("1100000");
    }

    @Test void idleEqualsManpowerBoardSummary() {
        var expected = board.getCommonManpowerBoard(new ManpowerBoardContracts.Filter(TODAY.getYear(), null, null, null, false));
        assertThat(read().summary().idlePeople()).isEqualTo(expected.summary().idleCount());
    }

    @Test void cashThreeMonthsEqualCashFlowStatement() {
        var result = read();
        var monthly = new HashMap<String, CashFlowStatementContracts.Month>();
        for (int year : new int[]{TODAY.getYear() - 1, TODAY.getYear(), TODAY.getYear() + 1})
            for (var m : cashFlows.getCommonCashFlowStatements(new CashFlowStatementContracts.Filter(year)).data().months())
                monthly.put(m.yearMonth(), m);
        assertThat(result.cash()).hasSize(3);
        for (var c : result.cash()) {
            var expected = monthly.get(c.month().replace("-", ""));
            assertThat(expected).as(c.month()).isNotNull();
            assertSame(c.monthEndBalance(), expected.closingBalance());
        }
        assertSame(result.minimumCash(), cashFlows.getCommonCashFlowStatements(
            new CashFlowStatementContracts.Filter(TODAY.getYear())).data().minimumCash());
    }

    @Test void withdrawalTodoEqualsWithdrawalBanner() {
        var banner = withdrawals.getCommonWithdrawals(new WithdrawalContracts.Filter(TODAY.getYear(), null, null, null)).banner();
        var todo = read().todos().stream().filter(t -> "withdrawal".equals(t.key())).findFirst().orElseThrow();
        assertThat(todo.count()).isEqualTo(banner.soonCount());
        assertThat(todo.title()).isEqualTo("3개월 안 철수 예정 " + banner.soonCount() + "명");
        assertThat(todo.description()).endsWith("다음 배정 없는 " + banner.unassignedCount() + "명 중 "
            + banner.matchedCount() + "명은 추천 자리 있음");
        assertThat(todo.visible()).isTrue();
        assertThat(todo.alwaysVisible()).isTrue();
    }

    @Test void manpowerEqualsManpowerForecast() {
        var expected = new HashMap<String, com.company.common.manpowerforecasts.dto.ManpowerForecastContracts.Month>();
        for (int year : new int[]{TODAY.getYear(), TODAY.getYear() + 1})
            forecasts.getCommonManpowerForecasts(year).months().forEach(m -> expected.put(m.month(), m));
        var manpower = read().manpower();
        assertThat(manpower).hasSize(6);
        for (var m : manpower) {
            var source = expected.get(m.month());
            assertThat(source).as(m.month()).isNotNull();
            assertSame(m.supply(), source.supply());
            assertSame(m.demand(), source.demand());
            assertSame(m.difference(), source.difference());
        }
    }

    @Test void salesPartsEqualPartSettlementsPerPart() {
        var month = selected().toString();
        var tabs = parts.getCommonPartSettlements(month).data().items();
        var rows = read().salesParts();
        assertThat(rows).extracting(DashboardContracts.SalesPart::partId)
            .containsExactlyInAnyOrderElementsOf(tabs.stream().map(t -> t.partId()).toList());
        for (var row : rows) {
            var detail = parts.getCommonPartSettlement(row.partId(), month);
            var revenue = detail.items().stream().map(w -> w.unitPrice()).reduce(BigDecimal.ZERO, BigDecimal::add);
            var profit = detail.items().stream().map(w -> w.profit()).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertSame(row.revenue(), revenue);
            assertSame(row.profitRate(), revenue.signum() == 0 ? null
                : profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 1, RoundingMode.HALF_UP));
        }
    }

    @Test void unassignedSalesPartEqualsProjectListAndExcludesInternal() {
        var todo = read().todos().stream().filter(t -> "unassignedSalesPart".equals(t.key())).findFirst().orElseThrow();
        assertThat(todo.count()).isEqualTo(projects.getCommonProjects().unassignedCount()).isEqualTo(1);
        assertThat(todo.targetScreenId()).isEqualTo("UI-01");
    }

    private static OperatingProfitStatementContracts.Line line(OperatingProfitStatementContracts.Result report, String label) {
        return report.numericTable().rows().stream().filter(r -> label.equals(r.label())).findFirst().orElseThrow();
    }

    private static void assertSame(BigDecimal actual, BigDecimal expected) {
        if (expected == null) assertThat(actual).isNull();
        else assertThat(actual).isEqualByComparingTo(expected);
    }
}
