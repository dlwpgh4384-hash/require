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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
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
        // 같은 「담당 프로젝트」 역할에 프로젝트 원가 분석(M015) 조회도 준다(범위 밖 원인·월 원가 403 시험).
        db.update("INSERT INTO TB_SY_ROLE_MENU_AUTH(role_id,menu_id,inq_yn,reg_yn,upd_yn,del_yn,upld_yn,dnld_yn,cnfm_yn,reg_user_id) "
            + "VALUES ('RS_PROJ03','M015','Y','N','N','N','N','N','N','SYSTEM')");
        // 「조회만, 다운로드 N」 역할(전체 범위, 민감 끔): 엑셀 내려받기 거부 시험용.
        db.update("INSERT INTO TB_SY_ROLE(role_id,role_nm,data_scope_cd,snstv_view_yn,sys_role_yn,sort_ord,use_yn,reg_user_id) "
            + "VALUES ('RS_NODL','역할 시험 다운로드 없음','ALL','N','N',991,'Y','SYSTEM')");
        for (String menu : new String[]{"M019", "M021", "M023"})
            db.update("INSERT INTO TB_SY_ROLE_MENU_AUTH(role_id,menu_id,inq_yn,reg_yn,upd_yn,del_yn,upld_yn,dnld_yn,cnfm_yn,reg_user_id) "
                + "VALUES ('RS_NODL',?,'Y','N','N','N','N','N','N','SYSTEM')", menu);
        db.update("INSERT INTO TB_SY_USER(user_id,user_nm,email_addr,pwd_hash,temp_pwd_yn,pwd_err_cnt,mfa_err_cnt,user_stat_cd,"
            + "mfa_use_yn,pwd_chg_dttm,reg_user_id) VALUES ('rs-nodl','rs-nodl','rs-nodl@example.invalid',?,'N',0,0,'USE','N',clock_timestamp(),'SYSTEM')", hash);
        db.update("INSERT INTO TB_SY_USER_ROLE(user_id,role_id,reg_user_id) VALUES ('rs-nodl','RS_NODL','SYSTEM')");
        // 거래처 1곳·외부 인력 후보 1명(거르지 않는 화면 시험: 명세 로그인 ⑧-3).
        db.update("INSERT INTO TB_BS_CLNT(CLNT_ID,CLNT_TYPE_CD,CLNT_NM,BIZ_REG_NO,REG_USER_ID) VALUES ('rs-clnt','CUST','역할 시험 고객','999-99-99999','SYSTEM')");
        db.update("INSERT INTO TB_HR_CAND(CAND_ID,CAND_NM,EMP_TYPE_CD,JOB_CD,GRADE_CD,HOPE_UPRC,INPUT_PSBL_DT,CNTC_STAT_CD,CAND_SRC_CD,REG_USER_ID) "
            + "VALUES ('rs-cand','역할 시험 후보','FL','BE','MD',6000000,?,'BEFORE','SITE','SYSTEM')", TODAY.plusDays(10));
        // 개발자 풀(M003) 업로드 권한이 있는 「담당 파트」 역할(민감 끔): 급여 업로드 403 시험용(처음 값 파트장은 업로드 권한이 없음).
        db.update("INSERT INTO TB_SY_ROLE(role_id,role_nm,data_scope_cd,snstv_view_yn,sys_role_yn,sort_ord,use_yn,reg_user_id) "
            + "VALUES ('RS_LDRUP','역할 시험 파트장 업로드','PART','N','N',992,'Y','SYSTEM')");
        db.update("INSERT INTO TB_SY_ROLE_MENU_AUTH(role_id,menu_id,inq_yn,reg_yn,upd_yn,del_yn,upld_yn,dnld_yn,cnfm_yn,reg_user_id) "
            + "VALUES ('RS_LDRUP','M003','Y','Y','Y','Y','Y','Y','Y','SYSTEM')");
        db.update("INSERT INTO TB_SY_USER(user_id,user_nm,email_addr,pwd_hash,temp_pwd_yn,pwd_err_cnt,mfa_err_cnt,user_stat_cd,"
            + "mfa_use_yn,pwd_chg_dttm,reg_user_id) VALUES ('rs-ldrup','rs-ldrup','rs-ldrup@example.invalid',?,'N',0,0,'USE','N',clock_timestamp(),'SYSTEM')", hash);
        db.update("INSERT INTO TB_SY_USER_ROLE(user_id,role_id,reg_user_id) VALUES ('rs-ldrup','RS_LDRUP','SYSTEM')");
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
        }).when(mailbox).deliver(anyList(), anyString(), anyString());
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

    // ── 화면별 메뉴·쓰기 403·범위·민감 칸(시험 케이스 장부 빈칸, 처음 값 권한 = DML·V5) ──

    static final Map<String, Set<String>> MENU_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    static final List<String> FIVE = List.of("rs-admin", "rs-exec", "rs-fin", "rs-ldr", "rs-pm");

    Set<String> cachedMenus(String userId) throws Exception {
        var found = MENU_CACHE.get(userId);
        if (found == null) { found = menus(userId); MENU_CACHE.put(userId, found); }
        return found;
    }

    @ParameterizedTest(name = "{0}: 메뉴 {1}은 [{2}]에게만 보인다")
    @CsvSource(delimiter = '|', value = {
        "CLI-25|M002|rs-admin rs-exec rs-fin rs-ldr rs-pm",
        "PBD-22|M005|rs-admin rs-exec rs-fin rs-ldr rs-pm",
        "ASN-24|M006|rs-admin rs-exec rs-fin rs-ldr rs-pm",
        "WDR-20|M007|rs-admin rs-exec rs-fin rs-ldr rs-pm",
        "MPC-17|M008|rs-admin rs-exec rs-fin rs-ldr rs-pm",
        "MPB-21|M009|rs-admin rs-exec rs-fin rs-ldr rs-pm",
        "MPF-15|M010|rs-admin rs-exec rs-fin rs-ldr rs-pm",
        "BIL-29|M013|rs-admin rs-exec rs-fin rs-ldr rs-pm",
        "PCP-17|M014|rs-admin rs-exec rs-fin",
        "PCA-20|M015|rs-admin rs-exec rs-fin",
        "RRN-16|M017|rs-admin rs-exec rs-fin rs-ldr",
        "FIN-34|M018|rs-admin rs-exec rs-fin",
        "PFS-29|M019|rs-admin rs-exec rs-fin",
        "OPS-28|M020|rs-admin rs-exec rs-fin",
        "NPS-24|M021|rs-admin rs-exec rs-fin",
        "BPA-27|M022|rs-admin rs-exec rs-fin",
        "CF-31|M023|rs-admin rs-exec rs-fin",
        "AIR-19|M026|rs-admin rs-exec rs-fin rs-ldr rs-pm"})
    void menuIsVisibleOnlyToRolesWithReadPermission(String row, String menuId, String visibleTo) throws Exception {
        var expected = Set.of(visibleTo.split(" "));
        for (String user : FIVE)
            assertThat(cachedMenus(user).contains(menuId)).as(row + " " + user + " " + menuId).isEqualTo(expected.contains(user));
    }

    MockHttpServletRequestBuilder call(String method, String path, String form, MockHttpSession session) {
        var builder = csrf(request(HttpMethod.valueOf(method), path).servletPath(path), session);
        if (!form.isBlank()) builder.param("formTypeCode", form);
        return method.equals("GET") ? builder : builder.contentType("application/json").content("{}");
    }

    @ParameterizedTest(name = "{0}: {1} {2} {3}{4} → 403 「권한이 없습니다.」")
    @CsvSource(delimiter = '|', value = {
        "PRJ-21|rs-exec|POST|/api/common/projects|",
        "PRJ-21|rs-exec|PUT|/api/common/projects/rs-proj-a|",
        "PRJ-21|rs-exec|POST|/api/common/projects/rs-proj-a/lose|",
        "DEV-36|rs-exec|POST|/api/common/developers|",
        "DEV-36|rs-exec|PUT|/api/common/developers/rs-dev|",
        "DEV-36|rs-exec|PUT|/api/common/developers/use-status|",
        "DEV-36|rs-exec|POST|/api/common/uploads|B8",
        "DEV-36|rs-ldr|POST|/api/common/uploads|B8",
        "DEV-36|rs-pm|POST|/api/common/uploads|B8",
        "WDR-21|rs-exec|PUT|/api/common/withdrawals/rs-asgn-a/extension-possibility|",
        "WDR-21|rs-exec|PUT|/api/common/withdrawals/rs-asgn-a/tentative-assignment|",
        "ASN-24|rs-exec|POST|/api/common/assignments|",
        "ASN-24|rs-exec|POST|/api/common/assignments/rs-asgn-a/changes|",
        "ASN-24|rs-exec|PUT|/api/common/assignments/extension-possibility|",
        "BIL-29|rs-exec|POST|/api/common/bills|",
        "BIL-29|rs-exec|POST|/api/common/bills/b-rs-proj-a/cancel|",
        "BIL-29|rs-exec|POST|/api/common/receipts|",
        "MCL-18|rs-exec|PUT|/api/common/month-closes/2026-10/confirm|",
        "MCL-18|rs-ldr|PUT|/api/common/month-closes/2026-10/confirm|",
        "MCL-18|rs-pm|PUT|/api/common/month-closes/2026-10/confirm|",
        "CLI-25|rs-exec|POST|/api/common/clients|",
        "CLI-25|rs-exec|PUT|/api/common/clients/rs-clnt|",
        "CLI-25|rs-exec|PUT|/api/common/clients/use-status|",
        "CLI-25|rs-ldr|POST|/api/common/parts|",
        "CLI-25|rs-pm|POST|/api/common/parts|",
        "CLI-25|rs-ldr|PUT|/api/common/parts/rs-part-a/standards|",
        "CLI-25|rs-exec|POST|/api/common/uploads|B7",
        "CLI-25|rs-ldr|POST|/api/common/uploads|B7",
        "CLI-25|rs-pm|POST|/api/common/uploads|B7",
        "FIN-34|rs-exec|PUT|/api/common/finance-items/2026-10|",
        "FIN-34|rs-exec|POST|/api/common/finance-items/tax-years/2026|",
        "FIN-34|rs-ldr|GET|/api/common/finance-items/2026-10|",
        "FIN-34|rs-pm|GET|/api/common/finance-items/2026-10|",
        "OPS-28|rs-exec|POST|/api/common/uploads|B11",
        "OPS-28|rs-fin|POST|/api/common/uploads|B11",
        "OPS-28|rs-ldr|GET|/api/common/operating-profit-statements|",
        "OPS-28|rs-pm|GET|/api/common/operating-profit-statements/excel|",
        "BPA-27|rs-exec|POST|/api/common/uploads|B10",
        "BPA-27|rs-ldr|POST|/api/common/uploads|B10",
        "BPA-27|rs-pm|POST|/api/common/uploads|B10",
        "BPA-27|rs-ldr|GET|/api/common/business-plan-comparisons|",
        "PST-21|rs-exec|PUT|/api/common/part-settlements/rs-part-a/2026-10/confirm|",
        "PST-21|rs-exec|PUT|/api/common/part-settlements/rs-part-a/2026-10/cancel|",
        "PST-21|rs-exec|PUT|/api/common/part-settlements/rs-part-a/2026-10/card|",
        "PST-21|rs-exec|PUT|/api/common/part-settlements/close-confirm|",
        "PST-21|rs-ldr|PUT|/api/common/part-settlements/rs-part-a/2026-10/confirm|",
        "PST-21|rs-ldr|PUT|/api/common/part-settlements/rs-part-a/2026-10/cancel|",
        "PST-21|rs-ldr|PUT|/api/common/part-settlements/close-confirm|",
        "ROL-18|rs-exec|POST|/api/common/roles|",
        "ROL-18|rs-ldr|PUT|/api/common/roles/PM|",
        "ROL-18|rs-pm|POST|/api/common/roles/PM/stop|",
        "MNU-19|rs-exec|PUT|/api/common/menu-settings|",
        "MNU-19|rs-ldr|PUT|/api/common/menu-settings|",
        "MNU-19|rs-pm|PUT|/api/common/menu-settings|",
        "CDE-23|rs-exec|PUT|/api/common/operating-standards|",
        "CDE-23|rs-ldr|POST|/api/common/code-maps|",
        "CDE-23|rs-pm|PUT|/api/common/code-groups/JOB/codes|",
        "CDE-23|rs-exec|POST|/api/common/code-groups/JOB/codes|",
        "CAN-21|rs-exec|POST|/api/common/candidates|",
        "CAN-21|rs-exec|PUT|/api/common/candidates/rs-cand|",
        "CAN-21|rs-exec|POST|/api/common/candidates/rs-cand/contacts|",
        "PBD-22|rs-exec|POST|/api/common/project-issues|",
        "PBD-22|rs-exec|PUT|/api/common/project-issues/1|",
        "AIR-19|rs-exec|POST|/api/common/ai-recommendations|",
        "AIR-19|rs-exec|POST|/api/common/ai-recommendations/1/exclusions|",
        "AIR-19|rs-exec|PUT|/api/common/ai-recommendations/1/tentative-assignment|",
        "PCP-17|rs-ldr|GET|/api/common/personal-cost-profits|",
        "PCP-17|rs-pm|GET|/api/common/personal-cost-profits|",
        "PCA-20|rs-ldr|GET|/api/common/project-cost-analyses|",
        "PCA-20|rs-pm|GET|/api/common/project-cost-analyses|",
        "PCA-21|rs-ldr|GET|/api/common/project-cost-analyses/rs-proj-a/causes|",
        "PCA-21|rs-pm|GET|/api/common/project-cost-analyses/rs-proj-a/causes|",
        "CF-31|rs-ldr|GET|/api/common/cash-flow-statements|",
        "CF-31|rs-pm|GET|/api/common/cash-flow-statements|",
        "CF-32|rs-nodl|GET|/api/common/cash-flow-statements/excel|",
        "CF-32|rs-pm|GET|/api/common/cash-flow-statements/excel|",
        "NPS-24|rs-nodl|GET|/api/common/net-profit-statements/excel|",
        "NPS-24|rs-ldr|GET|/api/common/net-profit-statements|",
        "NPS-24|rs-pm|GET|/api/common/net-profit-statements|",
        "PFS-29|rs-pm|GET|/api/common/profit-structures|",
        "PFS-29|rs-nodl|GET|/api/common/profit-structures/excel|",
        "MPB-21|rs-pm|GET|/api/common/manpower-board/excel|",
        "MPF-15|rs-pm|GET|/api/common/manpower-forecasts/excel|",
        "MPC-17|rs-pm|GET|/api/common/manpower-comparisons/excel|",
        "WDR-20|rs-pm|GET|/api/common/withdrawals/excel|",
        "RRN-16|rs-pm|GET|/api/common/rate-renegotiations|",
        "RRN-16|rs-pm|GET|/api/common/rate-renegotiations/excel|",
        "USR-21|rs-ldr|GET|/api/common/users|",
        "USR-21|rs-pm|PUT|/api/common/users/rs-pm|"})
    void actionWithoutMenuPermissionIsForbidden(String row, String user, String method, String path, String form) throws Exception {
        mvc.perform(call(method, path, form == null ? "" : form, login(user)))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("권한이 없습니다."));
    }

    @ParameterizedTest(name = "{0}: {1} {2} {3}는 권한 거르기를 통과한다(403 아님)")
    @CsvSource(delimiter = '|', value = {
        "PCA-21|rs-fin|GET|/api/common/project-cost-analyses/rs-proj-a/causes",
        "PCA-21|rs-exec|GET|/api/common/project-cost-analyses/rs-proj-a/causes",
        "PCA-21|rs-admin|GET|/api/common/project-cost-analyses/rs-proj-a/causes",
        "PCP-17|rs-exec|GET|/api/common/personal-cost-profits",
        "OPS-28|rs-exec|GET|/api/common/operating-profit-statements",
        "CF-27|rs-exec|GET|/api/common/cash-flow-statements?year=2026",
        "PFS-29|rs-exec|GET|/api/common/profit-structures",
        "NPS-24|rs-nodl|GET|/api/common/net-profit-statements",
        "MPF-15|rs-pm|GET|/api/common/manpower-forecasts?year=2026",
        "MPB-21|rs-pm|GET|/api/common/manpower-board?year=2026",
        "MPC-17|rs-pm|GET|/api/common/manpower-comparisons?year=2026",
        "RRN-16|rs-ldr|GET|/api/common/rate-renegotiations",
        "MCL-18|rs-fin|GET|/api/common/month-close-badge"})
    void allowedActionPassesTheMenuGate(String row, String user, String method, String path) throws Exception {
        String bare = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        var builder = csrf(request(HttpMethod.valueOf(method), path).servletPath(bare), login(user));
        int code = mvc.perform(builder).andReturn().getResponse().getStatus();
        assertThat(code).as(row + " " + user + " " + path).isNotEqualTo(403);
    }

    @ParameterizedTest(name = "{0}: {1} {2} {3} → 403 「이 건은 지금 볼 수 있는 범위 밖입니다.」")
    @CsvSource(delimiter = '|', value = {
        "WDR-22|rs-pm|GET|/api/common/withdrawals/rs-asgn-b/recommendations",
        "WDR-22|rs-ldr|GET|/api/common/withdrawals/rs-asgn-b/tentative-assignment",
        "WDR-22|rs-pm|PUT|/api/common/withdrawals/rs-asgn-b/extension-possibility",
        "ASN-25|rs-pm|GET|/api/common/assignments/rs-asgn-b",
        "ASN-25|rs-ldr|GET|/api/common/assignments/rs-asgn-b",
        "ASN-25|rs-pm|GET|/api/common/assignments/rs-asgn-b/history",
        "BIL-32|rs-pm|GET|/api/common/bills/b-rs-proj-b",
        "BIL-32|rs-ldr|GET|/api/common/bills/b-rs-proj-b",
        "BIL-32|rs-pm|POST|/api/common/bills/b-rs-proj-b/cancel",
        "PCA-22|rs-p03|GET|/api/common/project-cost-analyses/rs-proj-b/causes",
        "PCA-22|rs-p03|GET|/api/common/project-cost-analyses/rs-proj-b/monthly-costs",
        "PST-23|rs-ldr|GET|/api/common/part-settlements/rs-part-b/2026-10",
        "PST-23|rs-ldr|GET|/api/common/part-settlements/rs-part-b/2026-10/trends"})
    void outOfScopeDetailIsRejected(String row, String user, String method, String path) throws Exception {
        mvc.perform(call(method, path, "", login(user)))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("이 건은 지금 볼 수 있는 범위 밖입니다."));
    }

    @ParameterizedTest(name = "{0}: 범위 안 {1} {2}는 범위 거르기를 통과한다")
    @CsvSource(delimiter = '|', value = {
        "ASN-25|rs-pm|/api/common/assignments/rs-asgn-a",
        "BIL-32|rs-pm|/api/common/bills/b-rs-proj-a",
        "PCA-22|rs-p03|/api/common/project-cost-analyses/rs-proj-a/causes",
        "PST-23|rs-ldr|/api/common/part-settlements/rs-part-a/2026-10"})
    void inScopeDetailPasses(String row, String user, String path) throws Exception {
        int code = mvc.perform(call("GET", path, "", login(user))).andReturn().getResponse().getStatus();
        assertThat(code).as(row + " " + user + " " + path).isNotEqualTo(403);
    }

    String raw(String path, MockHttpSession session) throws Exception {
        String bare = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        return mvc.perform(csrf(get(path).servletPath(bare), session)).andExpect(status().isOk()).andReturn()
            .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    @ParameterizedTest(name = "{0}: {2} — {1}은 {3}만, 재무는 {3}·{4} 모두")
    @CsvSource(delimiter = '|', value = {
        "ASN-25|rs-pm|/api/common/assignments?year=YEAR|rs-asgn-a|rs-asgn-b",
        "ASN-25|rs-ldr|/api/common/assignments?year=YEAR|rs-asgn-a|rs-asgn-b",
        "BIL-32|rs-pm|/api/common/bills?year=YEAR|b-rs-proj-a|b-rs-proj-b",
        "RRN-17|rs-ldr|/api/common/rate-renegotiations?renewalMonths=12&profitRateThreshold=20|rs-proj-a|rs-proj-b",
        "MPB-21|rs-pm|/api/common/manpower-board?year=YEAR|rs-proj-a|rs-proj-b",
        "MPB-21|rs-ldr|/api/common/manpower-board?year=YEAR|rs-proj-a|rs-proj-b"})
    void listsAreFilteredToScope(String row, String user, String path, String inside, String outside) throws Exception {
        String url = path.replace("YEAR", String.valueOf(TODAY.getYear()));
        assertThat(raw(url, login("rs-fin"))).as(row + " 재무").contains(inside, outside);
        assertThat(raw(url, login(user))).as(row + " " + user).contains(inside).doesNotContain(outside);
    }

    @Test
    @DisplayName("MPC-18: PM은 맡은 프로젝트 줄만 — 범위 밖 프로젝트 ID로 조건을 걸어도 그 줄은 오지 않는다")
    void manpowerComparisonFollowsScope() throws Exception {
        for (String project : new String[]{"rs-proj-a", "rs-proj-b"})
            db.update("INSERT INTO TB_PJ_MNPW_PLAN(PROJ_ID,JOB_CD,PLAN_MM,SRC_CD,REG_USER_ID) VALUES (?,'BE',3,'MANUAL','SYSTEM')", project);
        try {
            String url = "/api/common/manpower-comparisons?year=" + TODAY.getYear();
            assertThat(raw(url, login("rs-fin"))).contains("rs-proj-a", "rs-proj-b");
            var pm = login("rs-pm");
            assertThat(raw(url, pm)).contains("rs-proj-a").doesNotContain("rs-proj-b");
            assertThat(raw(url + "&projectId=rs-proj-b", pm)).doesNotContain("rs-proj-b");
        } finally {
            db.update("DELETE FROM TB_PJ_MNPW_PLAN WHERE PROJ_ID IN ('rs-proj-a','rs-proj-b')");
        }
    }

    @Test
    @DisplayName("PBD-23: 현황판은 PM이 맡은 프로젝트만 — 재무는 둘 다(이번 달을 고를 수 있게 자금수지 시작 월을 이번 달로 잠깐 바꿈)")
    void projectBoardFollowsScope() throws Exception {
        String before = db.queryForObject("SELECT std_val::bigint::text FROM TB_SY_OPER_STD WHERE std_cd='CASH_BASE_YM' ORDER BY aply_bgn_ym DESC LIMIT 1", String.class);
        db.update("UPDATE TB_SY_OPER_STD SET std_val=? WHERE std_cd='CASH_BASE_YM'", new java.math.BigDecimal(YM));
        try {
            assertThat(raw("/api/common/project-board", login("rs-fin"))).as("재무").contains("rs-proj-a", "rs-proj-b");
            assertThat(raw("/api/common/project-board", login("rs-pm"))).as("PM").contains("rs-proj-a").doesNotContain("rs-proj-b");
        } finally {
            db.update("UPDATE TB_SY_OPER_STD SET std_val=? WHERE std_cd='CASH_BASE_YM'", new java.math.BigDecimal(before));
        }
    }

    @Test
    @DisplayName("현황판: 이번 달이 원가 분석에서 고를 수 없는 달(마감 없음·시작 월이 지난 달)이어도 500 없이 열린다")
    void projectBoardOpensWhenCurrentMonthIsNotSelectable() throws Exception {
        raw("/api/common/project-board", login("rs-fin"));
    }

    @ParameterizedTest(name = "{0}: {2}는 거르지 않는 화면 — {1}도 {3}을 모두 본다(명세 로그인 ⑧-3)")
    @CsvSource(delimiter = '|', value = {
        "CLI-26|rs-ldr|/api/common/parts|rs-part-a rs-part-b",
        "CLI-26|rs-ldr|/api/common/clients|rs-clnt",
        "CAN-22|rs-ldr|/api/common/candidates|rs-cand",
        "CAN-22|rs-pm|/api/common/candidates|rs-cand"})
    void unfilteredScreensShowEverything(String row, String user, String path, String ids) throws Exception {
        assertThat(raw(path, login(user))).as(row + " " + user).contains(ids.split(" "));
    }

    @Test
    @DisplayName("CLI-26: 파트장이 다른 파트의 기준 이력을 열어도 막지 않는다(거래처·영업 파트는 범위로 거르지 않음)")
    void partStandardsAreNotScoped() throws Exception {
        int code = mvc.perform(csrf(get("/api/common/parts/rs-part-b/standards").servletPath("/api/common/parts/rs-part-b/standards"), login("rs-ldr")))
            .andReturn().getResponse().getStatus();
        assertThat(code).isEqualTo(200);
    }

    @Test
    @DisplayName("MPF-15: 인력 수요 예측은 회사 전체 — PM·파트장 응답이 재무와 같다")
    void manpowerForecastIsCompanyWide() throws Exception {
        String url = "/api/common/manpower-forecasts?year=" + TODAY.getYear();
        var fin = json.readTree(raw(url, login("rs-fin"))).path("data");
        assertThat(json.readTree(raw(url, login("rs-pm"))).path("data")).isEqualTo(fin);
        assertThat(json.readTree(raw(url, login("rs-ldr"))).path("data")).isEqualTo(fin);
    }

    @Test
    @DisplayName("BIL-32: PM이 맡지 않은 프로젝트 청구에 입금을 등록하면 403 범위 밖")
    void receiptOnOutOfScopeBillIsRejected() throws Exception {
        try {
            mvc.perform(body(post("/api/common/receipts").servletPath("/api/common/receipts"), login("rs-pm"),
                    Map.of("receiptDate", TODAY.toString(), "items", List.of(Map.of("billId", "b-rs-proj-b", "amount", 1000)))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("이 건은 지금 볼 수 있는 범위 밖입니다."));
        } finally {
            // 잘못 들어간 입금은 되돌린다(다른 시험의 미수 건수가 흔들리지 않게).
            db.update("DELETE FROM TB_BL_RCPT WHERE BILL_ID='b-rs-proj-b'");
            db.update("UPDATE TB_BL_BILL SET RCPT_SUM_AMT=0, UNPAID_AMT=TOT_AMT WHERE BILL_ID='b-rs-proj-b'");
        }
    }

    @Test
    @DisplayName("MNU-19: 관리자가 아닌 역할(PM)이 메뉴 등록(POST /menus)을 요청하면 403")
    void menuRegistrationIsForbiddenForNonAdmins() throws Exception {
        mvc.perform(body(post("/api/common/menus").servletPath("/api/common/menus"), login("rs-pm"), Map.of()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("권한이 없습니다."));
    }

    @Test
    @DisplayName("SPL-28: 파트장은 자기 파트 영업 기회만 — 다른 파트 기회 상세는 403, PM은 메뉴가 없다")
    void salesOpportunitiesFollowPartScope() throws Exception {
        for (String[] opp : new String[][]{{"rs-opp-a", "rs-part-a"}, {"rs-opp-b", "rs-part-b"}})
            db.update("INSERT INTO TB_SL_OPP(OPP_ID,OPP_NM,CUST_CLNT_ID,PART_ID,SALES_STEP_CD,WIN_PRBL_RT,EXPCT_AMT,BGN_PLAN_YM,OPP_STAT_CD,"
                + "APRV_STAT_CD,REG_USER_ID) VALUES (?,?,'rs-clnt',?,'PROP',50,100000000,?,'ING','NONE','SYSTEM')", opp[0], opp[0], opp[1], YM);
        try {
            assertThat(raw("/api/common/sales-pipeline", login("rs-fin"))).contains("rs-opp-a", "rs-opp-b");
            var leader = login("rs-ldr");
            assertThat(raw("/api/common/sales-pipeline", leader)).contains("rs-opp-a").doesNotContain("rs-opp-b");
            mvc.perform(csrf(get("/api/common/sales-opportunities/rs-opp-b").servletPath("/api/common/sales-opportunities/rs-opp-b"), leader))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("이 건은 지금 볼 수 있는 범위 밖입니다."));
            mvc.perform(csrf(get("/api/common/sales-pipeline").servletPath("/api/common/sales-pipeline"), login("rs-pm")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("권한이 없습니다."));
        } finally {
            db.update("DELETE FROM TB_SL_OPP WHERE OPP_ID IN ('rs-opp-a','rs-opp-b')");
        }
    }

    static List<String> keys(JsonNode node) {
        var names = new ArrayList<String>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    @DisplayName("PST-24: 파트장 응답의 사람별 줄에는 급여·퇴직금·비용 계·이익·이익금이 없고, 재무에게는 있다")
    void partSettlementHidesSensitiveColumnsForLeader() throws Exception {
        String path = "/api/common/part-settlements/rs-part-a/" + openMonth();
        var leaderItems = getJson(path, login("rs-ldr")).path("data").path("items");
        assertThat(leaderItems).isNotEmpty();
        var person = keys(leaderItems.get(0));
        assertThat(person).contains("developerName", "projectNames", "monthlyMm", "unitPrice")
            .doesNotContain("salary", "retirementAmount", "totalCost", "profit", "profitShare");
        var finPerson = keys(getJson(path, login("rs-fin")).path("data").path("items").get(0));
        assertThat(finPerson).contains("salary", "totalCost", "profit", "profitShare");
        // 기간 추이: 사람별 달 칸의 비용·이익·이익금이 빠지고, 월별 파트 합계 줄만 남는다.
        var trends = getJson(path + "/trends", login("rs-ldr")).path("data");
        trends.path("items").forEach(item -> item.path("months").forEach(month ->
            assertThat(keys(month)).doesNotContain("totalCost", "profit", "profitShare")));
        assertThat(trends.path("monthlyTotals")).isNotEmpty();
    }

    @Test
    @DisplayName("RRN-18: 실제 로그인한 파트장은 현재 단가·월 원가·목표 단가를 받지 않고 이익률·필요 인상률은 받는다")
    void rateRenegotiationHidesCostForLeader() throws Exception {
        String url = "/api/common/rate-renegotiations?renewalMonths=12&profitRateThreshold=20";
        var items = json.readTree(raw(url, login("rs-ldr"))).path("data").path("items");
        assertThat(items).isNotEmpty();
        assertThat(keys(items.get(0))).contains("profitRate", "requiredIncreaseRate")
            .doesNotContain("currentUnitPrice", "monthlyCost", "targetUnitPrice");
        var fin = json.readTree(raw(url, login("rs-fin"))).path("data").path("items");
        assertThat(keys(fin.get(0))).contains("currentUnitPrice", "monthlyCost", "targetUnitPrice");
    }

    @Test
    @DisplayName("PCP-21: 민감 체크 없는 역할(담당 프로젝트)은 개인 원가·수익표에서 월 급여·개인별 원가 열을 받지 않는다 — 재무는 받는다")
    void personalCostProfitHidesSalaryWithoutSensitiveFlag() throws Exception {
        String path = "/api/common/personal-cost-profits?year=" + openMonth().getYear() + "&baseMonth=" + openMonth().getMonthValue() + "&displayMode=연누계";
        String scoped = raw(path, login("rs-p03"));
        assertThat(scoped).contains("\"rs-dev\"").doesNotContain("\"monthlySalary\"").doesNotContain("\"personalCost\"");
        assertThat(raw(path, login("rs-fin"))).contains("\"monthlySalary\"", "\"personalCost\"");
    }

    @Test
    @DisplayName("DEV-35: 단가·급여가 안 보이는 파트장이 개발자를 수정·저장해도 저장된 월 단가·급여는 그대로다")
    void nonSensitiveSaveKeepsStoredPrice() throws Exception {
        var leader = login("rs-ldr");
        var detail = getJson("/api/common/developers/rs-dev", leader).path("data");
        assertThat(detail.has("monthlyRate")).isFalse();
        var input = new java.util.HashMap<String, Object>(Map.of("name", "역할 시험 개발자", "employeeTypeCode", "FT", "organizationId", "",
            "primaryJobCode", "BE", "directClassCode", "DRCT", "salesPartId", "rs-part-a", "monthlyRate", 1, "monthlyPay", 1));
        input.put("modifiedAt", detail.path("modifiedAt").isNull() ? null : detail.path("modifiedAt").asText());
        mvc.perform(body(put("/api/common/developers/rs-dev").servletPath("/api/common/developers/rs-dev"), leader, input))
            .andExpect(status().isOk());
        var price = db.queryForMap("SELECT mon_uprc, mon_pay_amt FROM TB_HR_DEV_PRC WHERE dev_id='rs-dev' ORDER BY aply_bgn_ym DESC LIMIT 1");
        assertThat(((Number) price.get("mon_uprc")).longValue()).isEqualTo(7000000L);
        assertThat(((Number) price.get("mon_pay_amt")).longValue()).isEqualTo(5000000L);
    }

    @ParameterizedTest(name = "AIA-21: {0}의 급여 질문 → 거절={1}")
    @CsvSource({"rs-admin,false", "rs-exec,false", "rs-fin,false", "rs-ldr,true", "rs-pm,true"})
    void salaryQuestionFollowsSensitiveFlag(String user, boolean denied) throws Exception {
        String conclusion = ask(user, "이번 달 급여 합계는?").path("conclusion").asText();
        if (denied) assertThat(conclusion).isEqualTo("이 내용을 볼 권한이 없습니다.");
        else assertThat(conclusion).isNotEqualTo("이 내용을 볼 권한이 없습니다.");
    }

    @Test
    @DisplayName("DEV-38: 휴직 기간에 걸친 배정의 상세에는 「휴직 기간에 배정됩니다.」 경고가 붙는다")
    void assignmentDuringLeaveWarns() throws Exception {
        var fin = login("rs-fin");
        assertThat(getJson("/api/common/assignments/rs-asgn-b", fin).toString()).doesNotContain("휴직 기간에 배정됩니다.");
        db.update("INSERT INTO TB_HR_DEV_STAT(DEV_ID,STAT_SN,WORK_STAT_CD,BGN_DT,END_DT,REG_USER_ID) VALUES ('rs-dev-b',1,'LEAVE',?,?,'SYSTEM')",
            TODAY.minusDays(10), TODAY.plusDays(20));
        try {
            assertThat(getJson("/api/common/assignments/rs-asgn-b", fin).path("data").path("warnings").toString()).contains("휴직 기간에 배정됩니다.");
        } finally {
            db.update("DELETE FROM TB_HR_DEV_STAT WHERE DEV_ID='rs-dev-b'");
        }
    }

    void resetDeveloperStatuses() {
        db.update("DELETE FROM TB_HR_DEV_STAT WHERE DEV_ID='rs-dev'");
        db.update("UPDATE TB_HR_DEV SET RETIRE_DT=NULL, USE_YN='Y', WORK_STAT_CD='WORK' WHERE DEV_ID='rs-dev'");
    }

    void insertStatus(int sequence, String code, LocalDate start, LocalDate end, Integer closedLeave, LocalDate originalEnd) {
        db.update("INSERT INTO TB_HR_DEV_STAT(DEV_ID,STAT_SN,WORK_STAT_CD,BGN_DT,END_DT,CLOSE_LEAVE_SN,ORGNL_LEAVE_END_DT,REG_USER_ID) "
            + "VALUES ('rs-dev',?,?,?,?,?,?,'SYSTEM')", sequence, code, start, end, closedLeave, originalEnd);
    }

    @ParameterizedTest(name = "{0}: {1} {2} {3} → 403 「권한이 없습니다.」(M003 등록·수정 Y여도 근무 상태는 관리자·재무만)")
    @CsvSource(delimiter = '|', value = {
        "DEV-40|rs-ldr|POST|/api/common/developers/rs-dev/statuses|{\"statusName\":\"휴직\",\"date\":\"2099-01-01\"}",
        "DEV-40|rs-ldr|PUT|/api/common/developers/rs-dev/statuses/1/end-date|{\"endDate\":\"2099-01-02\"}",
        "DEV-40|rs-ldr|POST|/api/common/developers/rs-dev/statuses/2/return-cancel|{}",
        "DEV-40|rs-ldr|POST|/api/common/developers/rs-dev/statuses/1/retirement-cancel|{\"reason\":\"취소\"}",
        "DEV-40|rs-pm|POST|/api/common/developers/rs-dev/statuses|{\"statusName\":\"휴직\",\"date\":\"2099-01-01\"}",
        "DEV-40|rs-pm|PUT|/api/common/developers/rs-dev/statuses/1/end-date|{\"endDate\":\"2099-01-02\"}",
        "DEV-40|rs-pm|POST|/api/common/developers/rs-dev/statuses/2/return-cancel|{}",
        "DEV-40|rs-pm|POST|/api/common/developers/rs-dev/statuses/1/retirement-cancel|{\"reason\":\"취소\"}"})
    void statusWritesNeedAdminOrFinance(String row, String user, String method, String path, String content) throws Exception {
        resetDeveloperStatuses();
        insertStatus(1, "LEAVE", TODAY.minusDays(10), TODAY.minusDays(1), null, null);
        insertStatus(2, "WORK", TODAY, null, 1, TODAY.plusDays(10));
        try {
            mvc.perform(raw(method, path, content, login(user)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("권한이 없습니다."));
            assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_HR_DEV_STAT WHERE DEV_ID='rs-dev'", Integer.class))
                .as(row + " " + user + " " + path).isEqualTo(2);
            assertThat(db.queryForObject("SELECT END_DT FROM TB_HR_DEV_STAT WHERE DEV_ID='rs-dev' AND STAT_SN=1", LocalDate.class))
                .isEqualTo(TODAY.minusDays(1));
        } finally {
            resetDeveloperStatuses();
        }
    }

    @ParameterizedTest(name = "DEV-40: {0}은 근무 상태 등록·종료일 수정·재직 취소·(퇴사일 전) 퇴사 취소가 된다")
    @CsvSource({"rs-fin", "rs-admin"})
    void statusManagersCanWriteStatuses(String user) throws Exception {
        var session = login(user);
        resetDeveloperStatuses();
        try {
            mvc.perform(raw("POST", "/api/common/developers/rs-dev/statuses", json.writeValueAsString(Map.of(
                    "statusName", "휴직", "date", TODAY.plusDays(30).toString(), "endDate", TODAY.plusDays(40).toString())), session))
                .andExpect(status().isOk());
            int leave = db.queryForObject("SELECT MAX(STAT_SN) FROM TB_HR_DEV_STAT WHERE DEV_ID='rs-dev' AND WORK_STAT_CD='LEAVE'", Integer.class);
            String endDatePath = "/api/common/developers/rs-dev/statuses/" + leave + "/end-date";
            mvc.perform(raw("PUT", endDatePath, "{\"endDate\":\"" + TODAY.plusDays(45) + "\"}", session)).andExpect(status().isOk());
            assertThat(db.queryForObject("SELECT END_DT FROM TB_HR_DEV_STAT WHERE DEV_ID='rs-dev' AND STAT_SN=?", LocalDate.class, leave))
                .isEqualTo(TODAY.plusDays(45));

            resetDeveloperStatuses();
            insertStatus(1, "LEAVE", TODAY.minusDays(10), TODAY.minusDays(1), null, null);
            insertStatus(2, "WORK", TODAY, null, 1, TODAY.plusDays(10));
            mvc.perform(raw("POST", "/api/common/developers/rs-dev/statuses/2/return-cancel", "{}", session)).andExpect(status().isOk());
            assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_HR_DEV_STAT WHERE DEV_ID='rs-dev' AND WORK_STAT_CD='WORK'", Integer.class)).isZero();

            resetDeveloperStatuses();
            insertStatus(1, "RETIRE", TODAY.plusDays(5), null, null, null);
            mvc.perform(raw("POST", "/api/common/developers/rs-dev/statuses/1/retirement-cancel", "{\"reason\":\"계속 근무\"}", session))
                .andExpect(status().isOk());
            assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_HR_DEV_STAT WHERE DEV_ID='rs-dev'", Integer.class)).isZero();
        } finally {
            resetDeveloperStatuses();
        }
    }

    @Test
    @DisplayName("DEV-40: 퇴사일이 지난 퇴사 취소는 시스템 관리자만 — 재무 403, 관리자 200")
    void pastRetirementCancelIsAdminOnly() throws Exception {
        resetDeveloperStatuses();
        insertStatus(1, "RETIRE", TODAY.minusDays(1), null, null, null);
        String path = "/api/common/developers/rs-dev/statuses/1/retirement-cancel";
        try {
            mvc.perform(raw("POST", path, "{\"reason\":\"실수\"}", login("rs-fin")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("퇴사일이 지난 퇴사는 시스템 관리자만 취소할 수 있습니다."));
            assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_HR_DEV_STAT WHERE DEV_ID='rs-dev'", Integer.class)).isEqualTo(1);
            mvc.perform(raw("POST", path, "{\"reason\":\"실수\"}", login("rs-admin"))).andExpect(status().isOk());
            assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_HR_DEV_STAT WHERE DEV_ID='rs-dev'", Integer.class)).isZero();
        } finally {
            resetDeveloperStatuses();
        }
    }

    MockHttpServletRequestBuilder raw(String method, String path, String content, MockHttpSession session) {
        return csrf(request(HttpMethod.valueOf(method), path).servletPath(path), session)
            .contentType("application/json").content(content);
    }

    @ParameterizedTest(name = "{0}: {1} {2} {3} 본문 대상이 범위 밖 → 403 「이 건은 지금 볼 수 있는 범위 밖입니다.」")
    @CsvSource(delimiter = '|', value = {
        "BIL-32|rs-ldr|POST|/api/common/receipts|{\"receiptDate\":\"2026-01-01\",\"items\":[{\"billId\":\"b-rs-proj-b\",\"amount\":1000}]}",
        "BIL-32|rs-pm|POST|/api/common/bills|{\"projectId\":\"rs-proj-b\"}",
        "BIL-32|rs-ldr|POST|/api/common/bills|{\"projectId\":\"rs-proj-b\"}",
        "BIL-32|rs-pm|POST|/api/common/bill-checks/missing|{\"projectId\":\"rs-proj-b\"}",
        "BIL-32|rs-pm|POST|/api/common/bill-checks/correction|{\"projectId\":\"rs-proj-b\"}",
        "ASN-25|rs-pm|POST|/api/common/assignments|{\"projectId\":\"rs-proj-b\"}",
        "ASN-25|rs-ldr|POST|/api/common/assignments|{\"projectId\":\"rs-proj-b\"}",
        "ASN-25|rs-pm|POST|/api/common/assignments|{\"projectId\":\"rs-proj-a\",\"earlyWithdrawalAssignmentIds\":[\"rs-asgn-b\"]}",
        "ASN-25|rs-pm|POST|/api/common/assignments/cancel|{\"assignments\":[{\"assignmentId\":\"rs-asgn-b\"}]}",
        "ASN-25|rs-ldr|PUT|/api/common/assignments/extension-possibility|{\"assignments\":[{\"assignmentId\":\"rs-asgn-b\"}]}",
        "PBD-23|rs-pm|POST|/api/common/project-issues|{\"projectId\":\"rs-proj-b\"}",
        "PBD-23|rs-ldr|POST|/api/common/project-issues|{\"projectId\":\"rs-proj-b\"}",
        "SPL-28|rs-ldr|POST|/api/common/sales-opportunities|{\"salesPartId\":\"rs-part-b\"}",
        "AIR-00|rs-ldr|POST|/api/common/ai-recommendations|{\"targetType\":\"project\",\"targetId\":\"rs-proj-b\"}",
        "DEV-37|rs-ldr|PUT|/api/common/developers/use-status|{\"developerIds\":[\"rs-dev-b\"],\"useYn\":\"N\"}"})
    void outOfScopeBodyTargetWriteIsRejected(String row, String user, String method, String path, String content) throws Exception {
        try {
            mvc.perform(raw(method, path, content, login(user)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("이 건은 지금 볼 수 있는 범위 밖입니다."));
        } finally {
            db.update("DELETE FROM TB_BL_RCPT WHERE BILL_ID='b-rs-proj-b'");
            db.update("UPDATE TB_BL_BILL SET RCPT_SUM_AMT=0, UNPAID_AMT=TOT_AMT WHERE BILL_ID='b-rs-proj-b'");
        }
    }

    @ParameterizedTest(name = "{0}: 범위 안 대상 {1} {2} {3}는 범위 거르기를 통과한다(본문 검사 오류만)")
    @CsvSource(delimiter = '|', value = {
        "BIL-32|rs-pm|POST|/api/common/receipts|{\"items\":[{\"billId\":\"b-rs-proj-a\",\"amount\":1000}]}",
        "BIL-32|rs-pm|POST|/api/common/bills|{\"projectId\":\"rs-proj-a\"}",
        "PBD-23|rs-pm|POST|/api/common/project-issues|{\"projectId\":\"rs-proj-a\"}",
        "SPL-28|rs-ldr|POST|/api/common/sales-opportunities|{\"salesPartId\":\"rs-part-a\"}"})
    void inScopeBodyTargetWritePassesScope(String row, String user, String method, String path, String content) throws Exception {
        var response = mvc.perform(raw(method, path, content, login(user))).andReturn().getResponse();
        assertThat(response.getStatus()).as(row + " " + user + " " + path).isEqualTo(400);
    }

    @Test
    @DisplayName("BIL-32: 재무(전체 범위)는 본문 대상과 상관없이 범위 거르기를 받지 않는다")
    void allScopeSkipsBodyGuard() throws Exception {
        var response = mvc.perform(raw("POST", "/api/common/receipts", "{\"items\":[{\"billId\":\"b-rs-proj-b\",\"amount\":1000}]}",
            login("rs-fin"))).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("PRJ-23: 계약금액·예산은 민감 칸이 아니어서 PM(자기 프로젝트)·파트장(자기 파트 프로젝트)도 상세에서 받는다")
    void contractAmountAndBudgetAreNotSensitive() throws Exception {
        db.update("UPDATE TB_PJ_PROJ SET BDGT_AMT=80000000 WHERE PROJ_ID='rs-proj-a'");
        try {
            for (String user : new String[]{"rs-pm", "rs-ldr"}) {
                var detail = getJson("/api/common/projects/rs-proj-a", login(user)).path("data");
                assertThat(detail.path("contractAmount").asLong()).as(user + " 계약금액").isEqualTo(100000000L);
                assertThat(detail.path("budgetAmount").asLong()).as(user + " 예산").isEqualTo(80000000L);
            }
        } finally {
            db.update("UPDATE TB_PJ_PROJ SET BDGT_AMT=NULL WHERE PROJ_ID='rs-proj-a'");
        }
    }

    MockHttpServletRequestBuilder payrollUpload(MockHttpSession session) {
        byte[] file = new com.company.common.uploads.service.UploadWorkbookService(new com.company.common.support.service.ExcelExportService())
            .tabularFile(com.company.common.developers.service.PayrollUploadForm.HEADERS,
                List.of(List.of("rs-none", "없는 사람", "1000000", "", "", "", "")), null);
        return csrf(multipart("/api/common/uploads").file(new org.springframework.mock.web.MockMultipartFile("file", "payroll.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file))
            .param("formTypeCode", "B1").param("yearMonth", YearMonth.from(TODAY).toString())
            .servletPath("/api/common/uploads"), session);
    }

    @Test
    @DisplayName("CMN-34: 급여 업로드(B.1)는 범위 전체+민감 체크가 있는 사람만 — 업로드 권한 있는 파트장은 검사·반영 403, 재무는 된다")
    void payrollUploadNeedsAllScopeAndSensitiveFlag() throws Exception {
        String denied = "급여 업로드는 전체 범위와 민감 항목 권한이 있는 사람만 할 수 있습니다.";
        var leader = login("rs-ldrup");
        mvc.perform(payrollUpload(leader)).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(denied));
        // 양식 내려받기는 자료가 없어 막지 않는다.
        mvc.perform(csrf(get("/api/common/uploads/templates/B1").servletPath("/api/common/uploads/templates/B1"), leader))
            .andExpect(status().isOk());
        var fin = login("rs-fin");
        var checked = json.readTree(mvc.perform(payrollUpload(fin)).andExpect(status().isOk()).andReturn()
            .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
        String applyPath = "/api/common/uploads/" + checked.path("uploadId").asText() + "/apply";
        var body = json.createObjectNode();
        if (checked.path("modifiedAt").isNull() || checked.path("modifiedAt").isMissingNode()) body.putNull("modifiedAt");
        else body.put("modifiedAt", checked.path("modifiedAt").asText());
        // 남이 올린 업로드는 보안 필터가 먼저 403(올린 사람만). 반영 때 다시 보는 급여 규칙은 UploadDataScopeTest가 본다.
        mvc.perform(csrf(post(applyPath).servletPath(applyPath), leader).contentType("application/json").content(body.toString()))
            .andExpect(status().isForbidden());
        mvc.perform(csrf(post(applyPath).servletPath(applyPath), fin).contentType("application/json").content(body.toString()))
            .andExpect(status().isOk());
    }
}
