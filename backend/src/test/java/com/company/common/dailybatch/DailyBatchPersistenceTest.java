package com.company.common.dailybatch;

import com.company.common.assignments.service.CommonAssignmentService;
import com.company.common.calculation.dto.CalculationRuleContracts.Standard;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.cashflowstatements.dto.CashFlowStatementContracts.*;
import com.company.common.cashflowstatements.service.CommonCashFlowStatementService;
import com.company.common.dailybatch.mapper.DailyBatchMapper;
import com.company.common.dailybatch.service.DailyBatch;
import com.company.common.developers.service.CommonDeveloperService;
import com.company.common.notifications.mapper.NotificationMapper;
import com.company.common.notifications.service.NotificationPublisher;
import com.company.common.notifications.service.NotificationRetention;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.users.dto.UserContracts.Response;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 일 배치(명세 ⑪)와 알림 받는 사람(⑩-4·⑩-6)을 실제 PostgreSQL에서 본다: 역할·범위별 받는 사람, 다시 돌려도 중복 0,
 * 30일 지난 알림·3년 지난 기록 정리, 퇴사·계약 종료 단계, 업로드 완료는 올린 사람만, 미수·견적 반려 검사.
 */
@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class DailyBatchPersistenceTest {
    /** 2026-11-27 02:00 한국 시각. */
    static final Instant NOW = Instant.parse("2026-11-26T17:00:00Z");
    static final LocalDate DAY = LocalDate.of(2026, 11, 27);
    String schema;
    DataSource source;
    JdbcTemplate db;
    DailyBatch batch;
    NotificationPublisher publisher;
    TransactionTemplate tx;
    CommonDeveloperService developers = mock(CommonDeveloperService.class);
    CommonAssignmentService assignments = mock(CommonAssignmentService.class);
    CommonCashFlowStatementService cash = mock(CommonCashFlowStatementService.class);
    CalculationRuleService standards = mock(CalculationRuleService.class);

    @BeforeEach void setup() throws Exception {
        schema = "daily_batch_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("NAVIGATION_TEST_DB_URL"), user = System.getenv("NAVIGATION_TEST_DB_USER"),
            password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        var unpooled = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var props = new Properties(); props.setProperty("currentSchema", schema); unpooled.setDriverProperties(props);
        source = unpooled;
        var config = new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",
            new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), source));
        new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setMapperLocations(new ClassPathResource("mapper/notifications/NotificationMapper.xml"),
            new ClassPathResource("mapper/dailybatch/DailyBatchMapper.xml"));
        var sessions = new SqlSessionTemplate(factory.getObject());
        var notificationMapper = sessions.getMapper(NotificationMapper.class);
        var batchMapper = sessions.getMapper(DailyBatchMapper.class);
        var transactions = new DataSourceTransactionManager(source);
        tx = new TransactionTemplate(transactions);
        var calendar = new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC));
        publisher = new NotificationPublisher(notificationMapper, calendar);
        batch = new DailyBatch(developers, assignments, cash, standards, publisher,
            new NotificationRetention(notificationMapper, calendar), batchMapper, calendar, transactions);
        db = new JdbcTemplate(source);
        when(standards.effectiveStandard(eq("WTHDR_NOTI_D"), any()))
            .thenReturn(new Standard("WTHDR_NOTI_D", YearMonth.of(2026, 1), new BigDecimal("30")));
        var minimum = new BigDecimal("1000000000");
        when(cash.getCommonCashFlowStatements(any())).thenReturn(new Response<>(true, Map.of(), new Result(2026, minimum,
            List.of(), List.of(new Warning("202611", new BigDecimal("900000000"), minimum),
                new Warning("202612", new BigDecimal("800000000"), minimum)), null)));
        data();
    }

    @AfterEach void cleanup() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    void user(String id, String role, String status) {
        db.update("INSERT INTO TB_SY_USER(USER_ID,USER_NM,EMAIL_ADDR,PWD_HASH,TEMP_PWD_YN,PWD_ERR_CNT,USER_STAT_CD,MFA_USE_YN,MFA_ERR_CNT,REG_USER_ID) "
            + "VALUES(?,?,?,'test-only-hash','N',0,?,'N',0,'SYSTEM')", id, id, id + "@example.test", status);
        db.update("INSERT INTO TB_SY_USER_ROLE(USER_ID,ROLE_ID,REG_USER_ID) VALUES(?,?,'SYSTEM')", id, role);
    }

    void data() {
        user("t-adm", "ADMIN", "USE"); user("t-ceo", "EXEC", "USE"); user("t-fin", "FIN", "USE");
        user("t-fin-stop", "FIN", "STOP"); user("t-ldr1", "SALES_LDR", "USE"); user("t-ldr2", "SALES_LDR", "USE");
        user("t-pm1", "PM", "USE"); user("t-pm2", "PM", "USE");
        for (String part : List.of("t-part1", "t-part2"))
            db.update("INSERT INTO TB_BS_PART(PART_ID,PART_NM,HQ_YN,USE_YN,REG_USER_ID) VALUES(?,?,'N','Y','SYSTEM')", part, part);
        db.update("INSERT INTO TB_BS_PART_STD(PART_ID,APLY_BGN_YM,PART_LDR_USER_ID,PRFT_DSTRB_RT,SALES_EXPS_AMT,REG_USER_ID) VALUES('t-part1','202601','t-ldr1',0,0,'SYSTEM')");
        db.update("INSERT INTO TB_BS_PART_STD(PART_ID,APLY_BGN_YM,PART_LDR_USER_ID,PRFT_DSTRB_RT,SALES_EXPS_AMT,REG_USER_ID) VALUES('t-part2','202601','t-ldr2',0,0,'SYSTEM')");
        project("t-proj-a", "t-part1", "2027-12-31");
        project("t-proj-b", "t-part2", "2027-12-31");
        project("t-proj-old", "t-part1", "2026-11-26");
        db.update("INSERT INTO TB_SY_USER_SCOPE(USER_ID,SCOPE_SN,SCOPE_TYPE_CD,PROJ_ID,REG_USER_ID) VALUES('t-pm1',1,'PROJ','t-proj-a','SYSTEM')");
        db.update("INSERT INTO TB_SY_USER_SCOPE(USER_ID,SCOPE_SN,SCOPE_TYPE_CD,PROJ_ID,REG_USER_ID) VALUES('t-pm2',1,'PROJ','t-proj-b','SYSTEM')");
        String job = db.queryForObject("SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB' AND USE_YN='Y'", String.class);
        for (String dev : List.of("t-dev-a", "t-dev-b", "t-dev-c"))
            db.update("INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,JOB_CD,DRCT_CLS_CD,PART_ID,WORK_STAT_CD,REG_USER_ID) VALUES(?,?,?,'FT',?,'DIRECT','t-part1','INPUT','SYSTEM')", dev, dev, dev, job);
        db.update("UPDATE TB_HR_DEV SET RETIRE_DT='2026-11-20' WHERE DEV_ID='t-dev-c'");
        assignment("t-asgn-a", "t-proj-a", "t-dev-a", "2026-12-27", "ACT");
        assignment("t-asgn-b", "t-proj-b", "t-dev-b", "2026-12-27", "ACT");
        assignment("t-asgn-late", "t-proj-a", "t-dev-c", "2026-12-28", "ACT");
        assignment("t-asgn-cncl", "t-proj-a", "t-dev-c", "2026-12-27", "CNCL");
        db.update("INSERT INTO TB_BL_BILL(BILL_ID,PROJ_ID,BILL_ORD,BILL_YM,BILL_DT,SPLY_AMT,VAT_RT,VAT_AMT,TOT_AMT,RCPT_SUM_AMT,UNPAID_AMT,PAY_DUE_DT,REG_USER_ID) "
            + "VALUES('t-bill-a','t-proj-a',1,'202609','2026-09-30',1000,10,100,1100,0,1100,'2026-10-15','SYSTEM')");
    }

    void project(String id, String part, String end) {
        db.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) "
            + "VALUES(?,?,'MO','ING','2026-01-01',?::date,100000000,0,0,'SYSTEM')", id, id, end);
        db.update("INSERT INTO TB_PJ_PROJ_PART(PROJ_ID,APLY_BGN_YM,PART_ID,REG_USER_ID) VALUES(?,'202601',?,'SYSTEM')", id, part);
    }

    void assignment(String id, String project, String dev, String end, String status) {
        String job = db.queryForObject("SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB' AND USE_YN='Y'", String.class);
        db.update("INSERT INTO TB_PJ_ASGN(ASGN_ID,PROJ_ID,DEV_ID,JOB_CD,INPUT_BGN_DT,INPUT_END_DT,CTRT_MM,BILL_UPRC,INPUT_TYPE_CD,EXT_CNT,EARLY_WTHDR_YN,ASGN_STAT_CD,REG_USER_ID) "
            + "VALUES(?,?,?,?,'2026-01-01',?::date,1,8000000,'NEW',0,'N',?,'SYSTEM')", id, project, dev, job, end, status);
    }

    Map<String, List<String>> rows(String type) {
        var result = new TreeMap<String, List<String>>();
        db.query("SELECT RCVR_USER_ID,NOTI_CN FROM TB_SY_NOTI WHERE NOTI_TYPE_CD=? AND RCVR_USER_ID LIKE 't-%' ORDER BY RCVR_USER_ID,LINK_KEY",
            rs -> { result.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2)); }, type);
        return result;
    }

    @Test void withdrawalReachesOnlyInScopeReadersExactlyNDaysBeforeAndRerunAddsNothing() {
        var first = batch.run(DAY);
        assertThat(first.succeeded()).isTrue();
        var expected = new TreeMap<String, List<String>>(Map.of(
            "t-adm", List.of("철수 30일 전: 2명"), "t-ceo", List.of("철수 30일 전: 2명"), "t-fin", List.of("철수 30일 전: 2명"),
            "t-ldr1", List.of("철수 30일 전: 1명"), "t-ldr2", List.of("철수 30일 전: 1명"),
            "t-pm1", List.of("철수 30일 전: 1명"), "t-pm2", List.of("철수 30일 전: 1명")));
        assertThat(rows("WTHDR")).isEqualTo(expected);
        assertThat(db.queryForList("SELECT DISTINCT LINK_KEY||'|'||LINK_SCRN_ID||'|'||SEND_MTHD_CD||'|'||REG_USER_ID FROM TB_SY_NOTI WHERE NOTI_TYPE_CD='WTHDR'", String.class))
            .containsExactly("2026-12-27|UI-05|SYS|SYSTEM");
        long total = db.queryForObject("SELECT count(*) FROM TB_SY_NOTI", Long.class);
        assertThat(batch.run(DAY).succeeded()).isTrue();
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_NOTI", Long.class)).isEqualTo(total);
        assertThat(rows("WTHDR")).isEqualTo(expected);
    }

    @Test void cashShortageGoesToEveryoneWithCashFlowReadPermissionOncePerMonthPerDay() {
        batch.run(DAY);
        var expected = List.of("2026-11 자금 부족 예상 (최소 운영자금 10억 원 미만)", "2026-12 자금 부족 예상 (최소 운영자금 10억 원 미만)");
        assertThat(rows("CASH_SHORT")).isEqualTo(new TreeMap<>(Map.of("t-adm", expected, "t-ceo", expected, "t-fin", expected)));
        // 처음 관리자(admin, DML)도 같은 권한이라 받는다. 파트장·PM·중지 계정은 받지 않는다.
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_NOTI WHERE NOTI_TYPE_CD='CASH_SHORT' AND RCVR_USER_ID='admin'", Long.class)).isEqualTo(2);
        batch.run(DAY);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_NOTI WHERE NOTI_TYPE_CD='CASH_SHORT'", Long.class)).isEqualTo(8);
    }

    @Test void permissionNotRoleNameDecidesCashShortage() {
        db.update("UPDATE TB_SY_ROLE_MENU_AUTH SET INQ_YN='N' WHERE ROLE_ID='EXEC' AND MENU_ID='M023'");
        db.update("UPDATE TB_SY_ROLE_MENU_AUTH SET INQ_YN='Y' WHERE ROLE_ID='PM' AND MENU_ID='M023'");
        batch.run(DAY);
        assertThat(rows("CASH_SHORT").keySet()).containsExactly("t-adm", "t-fin", "t-pm1", "t-pm2");
    }

    @Test void expiredNotificationsAndThreeYearRecordsAreRemovedButPermanentHistoryStays() {
        db.update("INSERT INTO TB_SY_NOTI(RCVR_USER_ID,NOTI_TYPE_CD,NOTI_CN,SEND_MTHD_CD,NOTI_DTTM,REG_USER_ID) VALUES('t-fin','DUN','old','SYS',?::timestamptz - interval '31 days','SYSTEM')", NOW.toString());
        db.update("INSERT INTO TB_SY_NOTI(RCVR_USER_ID,NOTI_TYPE_CD,NOTI_CN,SEND_MTHD_CD,NOTI_DTTM,REG_USER_ID) VALUES('t-fin','DUN','recent','SYS',?::timestamptz - interval '29 days','SYSTEM')", NOW.toString());
        db.update("INSERT INTO TB_SY_ACCS_LOG(USER_ID,ACT_TYPE_CD,ACT_DTTM,REG_USER_ID) VALUES('t-fin','LOGIN',?::timestamptz - interval '3 years 1 day','SYSTEM')", NOW.toString());
        db.update("INSERT INTO TB_SY_ACCS_LOG(USER_ID,ACT_TYPE_CD,ACT_DTTM,REG_USER_ID) VALUES('t-fin','LOGIN',?::timestamptz - interval '2 years','SYSTEM')", NOW.toString());
        for (String[] row : new String[][]{{"TB_SY_CODE", "코드명"}, {"TB_HR_DEV", "이름"}, {"TB_HR_DEV", "월 단가"},
            {"TB_PJ_ASGN_HIST", "종료일"}, {"TB_CO_PART_SETL", "정산"}, {"TB_HR_DEV_PRC", "단가"}})
            db.update("INSERT INTO TB_SY_CHG_HIST(TRGT_TBL_NM,TRGT_KEY,CHG_TYPE_CD,CHG_ITEM_NM,REG_USER_ID,REG_DTTM) VALUES(?,'k','UPD',?,'SYSTEM',?::timestamptz - interval '3 years 1 day')", row[0], row[1], NOW.toString());
        var report = batch.run(DAY);
        assertThat(report.steps().get(6).succeeded()).isTrue();
        assertThat(db.queryForList("SELECT NOTI_CN FROM TB_SY_NOTI WHERE NOTI_TYPE_CD='DUN'", String.class)).containsExactly("recent");
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE USER_ID='t-fin'", Long.class)).isEqualTo(1);
        assertThat(db.queryForList("SELECT TRGT_TBL_NM||':'||CHG_ITEM_NM FROM TB_SY_CHG_HIST WHERE TRGT_KEY='k' ORDER BY 1", String.class))
            .containsExactly("TB_CO_PART_SETL:정산", "TB_HR_DEV:월 단가", "TB_HR_DEV_PRC:단가", "TB_PJ_ASGN_HIST:종료일");
        assertThat(report.steps().get(7).detail()).isEqualTo("접근 1건, 변경 2건");
    }

    @Test void retiredDeveloperStopsAndExpiredRunningProjectEnds() {
        var report = batch.run(DAY);
        assertThat(db.queryForObject("SELECT USE_YN||MOD_USER_ID FROM TB_HR_DEV WHERE DEV_ID='t-dev-c'", String.class)).isEqualTo("NSYSTEM");
        assertThat(db.queryForObject("SELECT USE_YN FROM TB_HR_DEV WHERE DEV_ID='t-dev-a'", String.class)).isEqualTo("Y");
        assertThat(db.queryForObject("SELECT PROJ_STAT_CD||MOD_USER_ID FROM TB_PJ_PROJ WHERE PROJ_ID='t-proj-old'", String.class)).isEqualTo("ENDSYSTEM");
        assertThat(db.queryForObject("SELECT PROJ_STAT_CD FROM TB_PJ_PROJ WHERE PROJ_ID='t-proj-a'", String.class)).isEqualTo("ING");
        assertThat(report.steps().get(1).detail()).isEqualTo("1명");
        assertThat(report.steps().get(2).detail()).isEqualTo("1건");
        assertThat(batch.run(DAY).steps().get(2).detail()).isEqualTo("0건");
    }

    @Test void overdueAndQuoteChecksUseStatusPermissionAndScope() {
        Map<String, Boolean> dun = new TreeMap<>();
        for (String user : List.of("t-adm", "t-ceo", "t-fin", "t-fin-stop", "t-ldr1", "t-ldr2", "t-pm1", "t-pm2"))
            dun.put(user, publisher.canReceive(user, "DUN", "UI-13", "t-bill-a"));
        assertThat(dun).isEqualTo(new TreeMap<>(Map.of("t-adm", true, "t-ceo", true, "t-fin", true, "t-fin-stop", false,
            "t-ldr1", true, "t-ldr2", false, "t-pm1", true, "t-pm2", false)));
        assertThat(publisher.canReceive("t-pm1", "DUN", "UI-13", "no-such-bill")).isFalse();
        db.update("UPDATE TB_SY_ROLE_MENU_AUTH SET INQ_YN='N' WHERE ROLE_ID='PM' AND MENU_ID='M013'");
        assertThat(publisher.canReceive("t-pm1", "DUN", "UI-13", "t-bill-a")).isFalse();
    }

    @Test void uploadCompletionGoesToUploaderOnly() {
        Long id = db.queryForObject("INSERT INTO TB_SY_UPLD(FORM_TYPE_CD,SCRN_ID,FILE_NM,FILE_SIZE,TOT_ROW_CNT,SUCC_CNT,WARN_CNT,ERR_CNT,UPLD_STAT_CD,APLY_DTTM,REG_USER_ID) "
            + "VALUES('B9','UI-12','a.xlsx',1,3,1,2,0,'APPLIED',clock_timestamp(),'t-pm1') RETURNING UPLD_ID", Long.class);
        assertThat((Boolean) tx.execute(status -> publisher.uploadCompleted(Long.toString(id), "UI-12", "투입 배정"))).isTrue();
        assertThat(db.queryForList("SELECT RCVR_USER_ID||'|'||NOTI_CN||'|'||LINK_SCRN_ID FROM TB_SY_NOTI WHERE NOTI_TYPE_CD='UPLD_DONE'", String.class))
            .containsExactly("t-pm1|투입 배정 업로드 완료 (경고 2건)|UI-12");
        Long unapplied = db.queryForObject("INSERT INTO TB_SY_UPLD(FORM_TYPE_CD,SCRN_ID,FILE_NM,FILE_SIZE,TOT_ROW_CNT,SUCC_CNT,WARN_CNT,ERR_CNT,UPLD_STAT_CD,REG_USER_ID) "
            + "VALUES('B9','UI-12','b.xlsx',1,1,1,0,0,'CHECKED','t-pm1') RETURNING UPLD_ID", Long.class);
        assertThat((Boolean) tx.execute(status -> publisher.uploadCompleted(Long.toString(unapplied), "UI-12", "투입 배정"))).isFalse();
    }
}
