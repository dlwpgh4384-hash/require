package com.company.common.rolesecurity;

import com.company.common.NavigationApplication;
import com.company.common.bills.service.BillLocalMailbox;
import com.company.common.schema.SchemaPreparation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 역할별 시험(명세 ⑮-2 가): 시험용 보안 문맥 없이 실제 로그인 API로 세션을 만들고, 실제 보안 필터를 거친다.
 * 관리자·경영진·재무·영업 파트장·PM 5개 역할 — 메뉴, 권한 없는 저장 403, 데이터 범위, 민감 칸.
 * 이 묶음은 시험 도우미(AuthenticatedMockMvcCustomizerFactory)의 자동 admin 로그인을 쓰지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@SpringBootTest(classes = NavigationApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RoleSecurityIntegrationTest {
    static final String SCHEMA = "role_security_" + UUID.randomUUID().toString().replace("-", "");
    static final String SECRET = "Rs-" + UUID.randomUUID().toString().substring(0, 8) + "1!";
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));
    static final String YM = YearMonth.from(TODAY).toString().replace("-", "");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @SpyBean BillLocalMailbox mailbox;
    final List<String> mail = new CopyOnWriteArrayList<>();
    final List<Path> files = new CopyOnWriteArrayList<>();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
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

    /** 시험 계정 5개(역할 하나씩), 파트 2개·프로젝트 2개(파트마다 하나)·청구 2건·개발자 1명(월 단가 있음). */
    static void seed(JdbcTemplate db) {
        String hash = new BCryptPasswordEncoder(10).encode(SECRET);
        for (String[] user : new String[][]{{"rs-admin", "ADMIN"}, {"rs-exec", "EXEC"}, {"rs-fin", "FIN"},
                {"rs-ldr", "SALES_LDR"}, {"rs-pm", "PM"}}) {
            db.update("INSERT INTO TB_SY_USER(user_id,user_nm,email_addr,pwd_hash,temp_pwd_yn,pwd_err_cnt,mfa_err_cnt,user_stat_cd,"
                + "mfa_use_yn,pwd_chg_dttm,reg_user_id) VALUES (?,?,?,?,'N',0,0,'USE',?,clock_timestamp(),'SYSTEM')",
                user[0], user[0], user[0] + "@example.invalid", hash, user[1].equals("ADMIN") ? "Y" : "N");
            db.update("INSERT INTO TB_SY_USER_ROLE(user_id,role_id,reg_user_id) VALUES (?,?,'SYSTEM')", user[0], user[1]);
        }
        for (String part : new String[]{"rs-part-a", "rs-part-b"})
            db.update("INSERT INTO TB_BS_PART(part_id,part_nm,reg_user_id) VALUES (?,?,'SYSTEM')", part, part);
        db.update("INSERT INTO TB_BS_PART_STD(part_id,aply_bgn_ym,part_ldr_user_id,prft_dstrb_rt,sales_exps_amt,reg_user_id) "
            + "VALUES ('rs-part-a','200001','rs-ldr',10,0,'SYSTEM')");
        db.update("INSERT INTO TB_BS_PART_STD(part_id,aply_bgn_ym,part_ldr_user_id,prft_dstrb_rt,sales_exps_amt,reg_user_id) "
            + "VALUES ('rs-part-b','200001','rs-fin',10,0,'SYSTEM')");
        for (String[] project : new String[][]{{"rs-proj-a", "rs-part-a"}, {"rs-proj-b", "rs-part-b"}}) {
            db.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,"
                + "PROJ_EXPS_AMT,REG_USER_ID,REG_DTTM) VALUES (?,?,'MO','ING',?,?,100000000,0,0,'SYSTEM',clock_timestamp())",
                project[0], project[0], TODAY.minusYears(1), TODAY.plusYears(1));
            db.update("INSERT INTO TB_PJ_PROJ_PART(PROJ_ID,APLY_BGN_YM,PART_ID,REG_USER_ID) VALUES (?,'200001',?,'SYSTEM')",
                project[0], project[1]);
            db.update("INSERT INTO TB_BL_BILL(BILL_ID,PROJ_ID,BILL_ORD,BILL_YM,BILL_DT,SPLY_AMT,VAT_RT,VAT_AMT,TOT_AMT,RCPT_SUM_AMT,"
                + "UNPAID_AMT,PAY_DUE_DT,REG_USER_ID,REG_DTTM) VALUES (?,?,1,?,?,1000000,10,100000,1100000,0,1100000,?,'SYSTEM',clock_timestamp())",
                "b-" + project[0], project[0], YM, TODAY, TODAY.plusDays(40));
        }
        db.update("INSERT INTO TB_SY_USER_SCOPE(user_id,scope_sn,scope_type_cd,proj_id,reg_user_id) VALUES ('rs-pm',1,'PROJ','rs-proj-a','SYSTEM')");
        db.update("INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,JOB_CD,DRCT_CLS_CD,PART_ID,WORK_STAT_CD,JNCMP_DT,REG_USER_ID,REG_DTTM) "
            + "VALUES ('rs-dev','rs-dev','역할 시험 개발자','FT','BE','DRCT','rs-part-a','WORK',?,'SYSTEM',clock_timestamp())", TODAY.minusYears(1));
        db.update("INSERT INTO TB_HR_DEV_PRC(dev_id,aply_bgn_ym,mon_uprc,mon_pay_amt,reg_user_id) VALUES ('rs-dev','200001',7000000,5000000,'SYSTEM')");
        // 다른 파트(b) 개발자 하나, 파트마다 진행 중 배정 하나(개인 원가·수익표 범위 시험).
        db.update("INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,JOB_CD,DRCT_CLS_CD,PART_ID,WORK_STAT_CD,JNCMP_DT,REG_USER_ID,REG_DTTM) "
            + "VALUES ('rs-dev-b','rs-dev-b','다른 파트 개발자','FT','BE','DRCT','rs-part-b','WORK',?,'SYSTEM',clock_timestamp())", TODAY.minusYears(1));
        db.update("INSERT INTO TB_HR_DEV_PRC(dev_id,aply_bgn_ym,mon_uprc,mon_pay_amt,reg_user_id) VALUES ('rs-dev-b','200001',6000000,4000000,'SYSTEM')");
        for (String[] assignment : new String[][]{{"rs-asgn-a", "rs-proj-a", "rs-dev"}, {"rs-asgn-b", "rs-proj-b", "rs-dev-b"}})
            db.update("INSERT INTO TB_PJ_ASGN(ASGN_ID,PROJ_ID,DEV_ID,JOB_CD,INPUT_BGN_DT,INPUT_END_DT,CTRT_MM,BILL_UPRC,INPUT_TYPE_CD,EXT_CNT,"
                + "ASGN_STAT_CD,REG_USER_ID) VALUES (?,?,?,'BE',?,?,1,7000000,'NEW',0,'ACT','SYSTEM')",
                assignment[0], assignment[1], assignment[2], TODAY.minusYears(1), TODAY.plusMonths(6));
        // 개인 원가·수익표(M014)를 볼 수 있는 「담당 프로젝트」 역할(처음 값에는 없음): 범위 거르기 시험용.
        db.update("INSERT INTO TB_SY_ROLE(role_id,role_nm,data_scope_cd,snstv_view_yn,sys_role_yn,sort_ord,use_yn,reg_user_id) "
            + "VALUES ('RS_PROJ03','역할 시험 UI-03','PROJ','N','N',990,'Y','SYSTEM')");
        db.update("INSERT INTO TB_SY_ROLE_MENU_AUTH(role_id,menu_id,inq_yn,reg_yn,upd_yn,del_yn,upld_yn,dnld_yn,cnfm_yn,reg_user_id) "
            + "VALUES ('RS_PROJ03','M014','Y','N','N','N','N','N','N','SYSTEM')");
        db.update("INSERT INTO TB_SY_USER(user_id,user_nm,email_addr,pwd_hash,temp_pwd_yn,pwd_err_cnt,mfa_err_cnt,user_stat_cd,"
            + "mfa_use_yn,pwd_chg_dttm,reg_user_id) VALUES ('rs-p03','rs-p03','rs-p03@example.invalid',?,'N',0,0,'USE','N',clock_timestamp(),'SYSTEM')", hash);
        db.update("INSERT INTO TB_SY_USER_ROLE(user_id,role_id,reg_user_id) VALUES ('rs-p03','RS_PROJ03','SYSTEM')");
        db.update("INSERT INTO TB_SY_USER_SCOPE(user_id,scope_sn,scope_type_cd,proj_id,reg_user_id) VALUES ('rs-p03',1,'PROJ','rs-proj-a','SYSTEM')");
    }

    @AfterAll
    static void dropSchema() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("NAVIGATION_TEST_DB_URL"),
                System.getenv("NAVIGATION_TEST_DB_USER"), System.getenv("NAVIGATION_TEST_DB_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @BeforeEach void captureMail() {
        doAnswer(call -> {
            mail.add(call.getArgument(1));
            Path path = (Path) call.callRealMethod();
            files.add(path);
            return path;
        }).when(mailbox).deliver(anyList(), anyString());
    }

    @AfterEach void discardMail() { files.forEach(mailbox::discard); }

    MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder builder, MockHttpSession session) {
        return builder.session(session).cookie(new Cookie("XSRF-TOKEN", "role-token")).header("X-XSRF-TOKEN", "role-token");
    }

    MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder builder, MockHttpSession session, Object value) throws Exception {
        return csrf(builder, session).contentType("application/json").content(json.writeValueAsString(value));
    }

    /** 실제 로그인 API. 관리자는 메일 파일함에 간 인증번호로 2단계까지 마친다. */
    MockHttpSession login(String userId) throws Exception {
        var session = new MockHttpSession();
        var result = mvc.perform(body(post("/api/common/login").servletPath("/api/common/login"), session,
                Map.of("userId", userId, "password", SECRET))).andExpect(status().isOk()).andReturn();
        String next = json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data").path("nextScreenId").asText();
        if (next.equals("UI-39")) {
            assertThat(mail).isNotEmpty();
            var code = Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])").matcher(mail.get(mail.size() - 1));
            assertThat(code.find()).isTrue();
            mvc.perform(body(post("/api/common/mfa/verify").servletPath("/api/common/mfa/verify"), session,
                Map.of("code", code.group()))).andExpect(status().isOk());
        }
        return session;
    }

    JsonNode getJson(String path, MockHttpSession session) throws Exception {
        var result = mvc.perform(csrf(get(path).servletPath(path), session)).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    Set<String> menus(String userId) throws Exception {
        var ids = new HashSet<String>();
        getJson("/api/common/menus", login(userId)).path("data").forEach(item -> ids.add(item.path("menuId").asText()));
        return ids;
    }

    @Test
    @DisplayName("메뉴 API는 조회 권한 있는 메뉴만, 대메뉴는 아래 메뉴가 있을 때만 준다(역할 5개)")
    void menusFollowReadPermissionPerRole() throws Exception {
        var admin = menus("rs-admin");
        assertThat(admin).contains("M001", "M011", "M024", "M027", "M030", "G010");
        var exec = menus("rs-exec");
        assertThat(exec).contains("M001", "M011", "M023").doesNotContain("M027", "M028", "M029", "M030", "G010");
        var fin = menus("rs-fin");
        assertThat(fin).contains("M013", "M018", "M024", "M027");
        var leader = menus("rs-ldr");
        assertThat(leader).contains("M004", "M011", "M016").doesNotContain("M014", "M015", "M018", "M024", "M027", "G010");
        var pm = menus("rs-pm");
        assertThat(pm).contains("M004", "M013").doesNotContain("M011", "M014", "M016", "M024", "M027", "G010");
    }

    @Test
    @DisplayName("권한 없는 저장은 403 「권한이 없습니다.」 — 경영진 사용자 등록, PM 영업 기회 등록")
    void forbiddenWritesAreRejected() throws Exception {
        mvc.perform(body(put("/api/common/users").servletPath("/api/common/users"), login("rs-exec"), Map.of("userId", "x")))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("권한이 없습니다."));
        mvc.perform(body(post("/api/common/sales-opportunities").servletPath("/api/common/sales-opportunities"), login("rs-pm"), Map.of()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("권한이 없습니다."));
    }

    @Test
    @DisplayName("PM은 맡은 프로젝트만 보고, 범위 밖 프로젝트 상세는 403 — 파트장은 자기 파트 프로젝트만")
    void projectScopeFollowsRole() throws Exception {
        var pm = login("rs-pm");
        String list = getJson("/api/common/projects", pm).toString();
        assertThat(list).contains("rs-proj-a").doesNotContain("rs-proj-b");
        mvc.perform(csrf(get("/api/common/projects/rs-proj-b").servletPath("/api/common/projects/rs-proj-b"), pm))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("이 건은 지금 볼 수 있는 범위 밖입니다."));
        assertThat(getJson("/api/common/bills/unpaid-count", pm).path("data").path("count").asInt()).isEqualTo(1);
        assertThat(getJson("/api/common/bills/unpaid-count", login("rs-fin")).path("data").path("count").asInt()).isGreaterThanOrEqualTo(2);
        var leader = login("rs-ldr");
        String leaderList = getJson("/api/common/projects", leader).toString();
        assertThat(leaderList).contains("rs-proj-a").doesNotContain("rs-proj-b");
        String all = getJson("/api/common/projects", login("rs-fin")).toString();
        assertThat(all).contains("rs-proj-a", "rs-proj-b");
    }

    @Test
    @DisplayName("민감 체크가 꺼진 역할(파트장·PM)은 월 단가 칸을 받지 않는다 — 재무는 받는다")
    void nonSensitiveUsersDoNotReceivePriceColumns() throws Exception {
        String leader = getJson("/api/common/developers", login("rs-ldr")).toString();
        assertThat(leader).contains("rs-dev").doesNotContain("\"monthlyRate\"").doesNotContain("\"monthlyPay\"");
        String fin = getJson("/api/common/developers", login("rs-fin")).toString();
        assertThat(fin).contains("rs-dev").contains("\"monthlyRate\"");
    }

    @Test
    @DisplayName("로그인하면 그 사람의 화면 기간 기억(PERIOD)만 지운다")
    void loginClearsOnlyOwnPeriodPreferences() throws Exception {
        for (String user : new String[]{"rs-pm", "rs-fin"})
            db.update("INSERT INTO TB_SY_USER_PREF(user_id,pref_type_cd,pref_key,pref_val,reg_user_id) VALUES (?,'PERIOD','UI-04','{\"year\":2020,\"month\":1}',?)", user, user);
        db.update("INSERT INTO TB_SY_USER_PREF(user_id,pref_type_cd,pref_key,pref_val,reg_user_id) VALUES ('rs-pm','PAGE_SIZE','GLOBAL','50','rs-pm')");
        login("rs-pm");
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_USER_PREF WHERE user_id='rs-pm' AND pref_type_cd='PERIOD'", Long.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_USER_PREF WHERE user_id='rs-pm' AND pref_type_cd='PAGE_SIZE'", Long.class)).isEqualTo(1L);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_USER_PREF WHERE user_id='rs-fin' AND pref_type_cd='PERIOD'", Long.class)).isEqualTo(1L);
        db.update("DELETE FROM TB_SY_USER_PREF WHERE user_id IN ('rs-pm','rs-fin')");
    }

    @Test
    @DisplayName("화면 설정은 사람마다 따로 저장된다(로그인 사용자 기준)")
    void preferencesAreKeyedByTheLoggedInUser() throws Exception {
        var pm = login("rs-pm");
        mvc.perform(body(put("/api/common/user-preferences/PAGE_SIZE/GLOBAL").servletPath("/api/common/user-preferences/PAGE_SIZE/GLOBAL"),
            pm, new java.util.HashMap<>(java.util.Map.of("prefVal", "100")) {{ put("modDttm", null); }})).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT pref_val FROM TB_SY_USER_PREF WHERE user_id='rs-pm' AND pref_type_cd='PAGE_SIZE'", String.class)).isEqualTo("100");
        assertThat(getJson("/api/common/user-preferences/PAGE_SIZE/GLOBAL", login("rs-fin")).path("data").path("found").asBoolean()).isFalse();
        db.update("DELETE FROM TB_SY_USER_PREF WHERE user_id IN ('rs-pm','rs-fin')");
    }

    /** 고를 수 있는 가장 늦은 달: 마감이 없으면 자금수지 시작 월(CASH_BASE_YM, 기간 기준 latestYm). */
    YearMonth openMonth() {
        String base = db.queryForObject("SELECT std_val::bigint::text FROM TB_SY_OPER_STD WHERE std_cd='CASH_BASE_YM' ORDER BY aply_bgn_ym DESC LIMIT 1", String.class);
        return YearMonth.of(Integer.parseInt(base.substring(0, 4)), Integer.parseInt(base.substring(4)));
    }

    JsonNode dashboard(String userId) throws Exception {
        String month = openMonth().toString();
        var session = login(userId);
        var result = mvc.perform(csrf(get("/api/common/dashboard").servletPath("/api/common/dashboard").param("yearMonth", month), session))
            .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }

    static Set<String> todoKeys(JsonNode data) {
        var keys = new HashSet<String>();
        data.path("todos").forEach(t -> keys.add(t.path("key").asText()));
        return keys;
    }

    @Test
    @DisplayName("대시보드 모양은 범위로 — 재무 전체, 파트장 자기 파트(마감 진행·자금·법인세 숨김), PM 맡은 프로젝트(요약·하단 숨김)")
    void dashboardShapeFollowsScope() throws Exception {
        var fin = dashboard("rs-fin");
        assertThat(fin.path("dashMode").asText()).isEqualTo("all");
        assertThat(fin.path("closing").isObject()).isTrue();
        assertThat(fin.path("summary").path("unpaidCount").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(todoKeys(fin)).contains("taxPayment", "cashShortage");
        // 영업 파트별(SFR-116-07): 마감 전 달도 영업 파트 이익금의 실시간 계산 값(잠정)이 나온다 — 파트마다 월단위 배정 7,000,000원.
        var finParts = new java.util.HashMap<String, Long>();
        fin.path("salesParts").forEach(p -> finParts.put(p.path("partId").asText(), p.path("revenue").asLong()));
        assertThat(finParts).containsEntry("rs-part-a", 7000000L).containsEntry("rs-part-b", 7000000L);
        assertThat(fin.path("provisional").asBoolean()).isTrue();

        var leader = dashboard("rs-ldr");
        assertThat(leader.path("dashMode").asText()).isEqualTo("part");
        assertThat(leader.path("closing").isNull()).isTrue();
        assertThat(leader.path("cash")).isEmpty();
        assertThat(leader.path("minimumCash").isNull()).isTrue();
        assertThat(leader.path("summary").path("unpaidCount").asInt()).isEqualTo(1);
        assertThat(leader.path("summary").path("revenueTargetRate").isNull()).isTrue();
        assertThat(todoKeys(leader)).doesNotContain("taxPayment", "cashShortage", "unassignedSalesPart").contains("overdueBills");
        leader.path("todos").forEach(t -> { if (t.path("key").asText().equals("manpowerShortage"))
            assertThat(t.path("title").asText()).endsWith("(회사 전체)"); });
        assertThat(leader.toString()).doesNotContain("rs-part-b");
        assertThat(leader.path("salesParts")).hasSize(1);
        assertThat(leader.path("salesParts").get(0).path("revenue").asLong()).isEqualTo(7000000L);
        assertThat(leader.path("summary").path("revenue").asLong()).isEqualTo(7000000L);

        var pm = dashboard("rs-pm");
        assertThat(pm.path("dashMode").asText()).isEqualTo("pm");
        assertThat(pm.path("summary").isNull()).isTrue();
        assertThat(pm.path("closing").isNull()).isTrue();
        assertThat(pm.path("pipeline").isNull()).isTrue();
        assertThat(pm.path("trend")).isEmpty();
        assertThat(pm.path("salesParts")).isEmpty();
        assertThat(todoKeys(pm)).doesNotContain("overdueBills", "billingMissing", "pendingQuotes", "taxPayment", "cashShortage");
        assertThat(pm.path("manpower")).hasSize(6);
    }

    JsonNode ask(String userId, String question) throws Exception {
        var result = mvc.perform(body(post("/api/common/ai-analysis/questions").servletPath("/api/common/ai-analysis/questions"),
                login(userId), Map.of("requestId", "rs-" + UUID.randomUUID().toString().substring(0, 8), "question", question,
                    "screenId", "UI-26"))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }

    @Test
    @DisplayName("AI 분석 도우미: 화면 권한이 없는 회사 손익은 답하지 않고, 미수 답은 범위 안 프로젝트만, 급여는 민감 체크 없으면 거절")
    void aiAnswersStayInsideScopeAndPermission() throws Exception {
        var pmProfit = ask("rs-pm", "이번 달 매출과 영업이익은?");
        assertThat(pmProfit.path("answerStatus").asText()).isEqualTo("NO_ANS");
        assertThat(pmProfit.path("conclusion").asText()).isEqualTo("이 내용을 볼 권한이 없습니다.");
        assertThat(ask("rs-fin", "이번 달 매출과 영업이익은?").path("conclusion").asText()).isNotEqualTo("이 내용을 볼 권한이 없습니다.");

        var leaderBills = ask("rs-ldr", "지금 미수금은 얼마?");
        assertThat(leaderBills.path("answerStatus").asText()).isEqualTo("OK");
        assertThat(leaderBills.toString()).contains("rs-proj-a").doesNotContain("rs-proj-b");
        var finBills = ask("rs-fin", "지금 미수금은 얼마?");
        assertThat(finBills.toString()).contains("rs-proj-a", "rs-proj-b");

        var salary = ask("rs-ldr", "이번 달 급여 합계는?");
        assertThat(salary.path("answerStatus").asText()).isEqualTo("NO_ANS");
        assertThat(salary.path("conclusion").asText()).isEqualTo("이 내용을 볼 권한이 없습니다.");
        // 저장된 답(질문 기록)에도 범위 밖 프로젝트가 없다.
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_AI_QNA WHERE user_id='rs-ldr' AND COALESCE(ans_cn,'') LIKE '%rs-proj-b%'", Long.class)).isZero();
    }

    @Test
    @DisplayName("개인 원가·수익표는 범위 안 프로젝트에 투입된 사람만 — 담당 프로젝트 역할")
    void personalCostProfitsFollowScope() throws Exception {
        String path = "/api/common/personal-cost-profits";
        var params = new String[]{String.valueOf(openMonth().getYear()), String.valueOf(openMonth().getMonthValue())};
        var scoped = mvc.perform(csrf(get(path).servletPath(path).param("year", params[0]).param("baseMonth", params[1]).param("displayMode", "연누계"), login("rs-p03")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(scoped).contains("\"rs-dev\"").doesNotContain("\"rs-dev-b\"");
        var all = mvc.perform(csrf(get(path).servletPath(path).param("year", params[0]).param("baseMonth", params[1]).param("displayMode", "연누계"), login("rs-fin")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(all).contains("\"rs-dev\"", "\"rs-dev-b\"");
    }

    @Test
    @DisplayName("개발자 풀: 파트장은 자기 파트 인력만(다른 파트 상세는 403), PM·재무는 모두")
    void developerPoolIsPartOnlyForPartLeaders() throws Exception {
        var leader = login("rs-ldr");
        String list = getJson("/api/common/developers", leader).toString();
        assertThat(list).contains("\"rs-dev\"").doesNotContain("\"rs-dev-b\"");
        mvc.perform(csrf(get("/api/common/developers/rs-dev-b").servletPath("/api/common/developers/rs-dev-b"), leader))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("이 건은 지금 볼 수 있는 범위 밖입니다."));
        mvc.perform(csrf(get("/api/common/developers/rs-dev").servletPath("/api/common/developers/rs-dev"), leader))
            .andExpect(status().isOk());
        assertThat(getJson("/api/common/developers", login("rs-pm")).toString()).contains("\"rs-dev\"", "\"rs-dev-b\"");
        assertThat(getJson("/api/common/developers", login("rs-fin")).toString()).contains("\"rs-dev\"", "\"rs-dev-b\"");
    }
}
