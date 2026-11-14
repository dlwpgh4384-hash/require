package com.company.common.developers.controller;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.developers.mapper.DeveloperMapper;
import com.company.common.developers.service.*;
import com.company.common.schema.SchemaPreparation;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.company.common.users.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="NAVIGATION_TEST_DB_URL",matches="jdbc:postgresql:.*")
@WebMvcTest(CommonDeveloperController.class)
@ContextConfiguration(classes={CommonDeveloperController.class,CommonDeveloperService.class,DeveloperNumberReservations.class,
    DeveloperManMonthRules.class,CalculationRuleService.class,CurrentBusinessActor.class,ExcelExportService.class,
    CodeExceptionHandler.class,ApiExceptionHandler.class,DeveloperPersistenceTest.Persistence.class})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DeveloperPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired DeveloperDatabase owned;
    JdbcTemplate db;
    record DeveloperDatabase(String name) {}
    @TestConfiguration(proxyBeanMethods=false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean DeveloperDatabase developerDatabase() { return new DeveloperDatabase("developers_"+UUID.randomUUID().toString().replace("-","")); }
        @Bean DataSource dataSource(DeveloperDatabase owned) throws Exception {
            String url=System.getenv("NAVIGATION_TEST_DB_URL"),user=System.getenv("NAVIGATION_TEST_DB_USER"),password=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
                assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);statement.execute("CREATE SCHEMA "+owned.name());
            }
            var source=new UnpooledDataSource("org.postgresql.Driver",url,user,password);var props=new Properties();props.setProperty("currentSchema",owned.name());source.setDriverProperties(props);
            var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare(); return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory=new SqlSessionFactoryBean();factory.setDataSource(source);factory.setMapperLocations(new ClassPathResource("mapper/developers/DeveloperMapper.xml"),new ClassPathResource("mapper/UserMapper.xml"),new ClassPathResource("mapper/CalculationRuleMapper.xml"),new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var config=new org.apache.ibatis.session.Configuration();config.setMapUnderscoreToCamelCase(false);config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config);return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean DeveloperMapper developerMapper(SqlSessionTemplate session) { return session.getMapper(DeveloperMapper.class); }
        @Bean UserMapper userMapper(SqlSessionTemplate session) { return session.getMapper(UserMapper.class); }
        @Bean CalculationRuleMapper calculationRuleMapper(SqlSessionTemplate session) { return session.getMapper(CalculationRuleMapper.class); }
        @Bean UserPreferencePeriodSourceMapper userPreferencePeriodSourceMapper(SqlSessionTemplate session) { return session.getMapper(UserPreferencePeriodSourceMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean BusinessCalendar businessCalendar() { return new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"),ZoneOffset.UTC)); }
    }
    @BeforeEach void setup() { db=new JdbcTemplate(source); }
    @AfterEach void cleanup() throws Exception { try(var connection=source.getConnection();var statement=connection.createStatement()) { statement.execute("DROP SCHEMA "+owned.name()+" CASCADE"); } }
    private ObjectNode input() {
        String part=db.queryForObject("SELECT part_id FROM TB_BS_PART WHERE use_yn='Y' ORDER BY hq_yn DESC,part_id LIMIT 1",String.class);
        return json.createObjectNode().put("name","통합 개발자").put("employeeTypeCode","FT").put("organizationId","").put("primaryJobCode","DEV").put("directClassCode","DRCT").put("salesPartId",part);
    }
    private String create() throws Exception {
        String body=mvc.perform(post("/api/common/developers").contentType("application/json").content(input().toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("developerId").asText();
    }
    @Test void firstSeededAdminCreatesAndASecondRequestReadsPersistedSkillsAndAddress() throws Exception {
        var body=input().put("homeAreaCode","1168010100");body.putArray("skillCodes").add("JAVA");
        String raw=mvc.perform(post("/api/common/developers").contentType("application/json").content(body.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id=json.readTree(raw).path("data").path("developerId").asText();
        mvc.perform(get("/api/common/developers/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.skillCodes[0]").value("JAVA")).andExpect(jsonPath("$.data.homeAreaCode").value("1168010100"));
        assertThat(db.queryForObject("SELECT count(*) FROM TB_HR_DEV_SKILL WHERE dev_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT reg_user_id FROM TB_HR_DEV WHERE dev_id=?",String.class,id)).isEqualTo("admin");
    }
    @Test void sameKoreanMonthUpdatesOnePricePeriodKeepsFutureAndWritesEachAudit() throws Exception {
        String id=create();db.update("INSERT INTO TB_HR_DEV_PRC(dev_id,aply_bgn_ym,mon_uprc,reg_user_id) VALUES(?, '202701',999,'admin')",id);
        for(int amount:new int[]{100,200}) mvc.perform(put("/api/common/developers/"+id).contentType("application/json").content(input().put("monthlyRate",amount).put("monthlyPay",50).toString())).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT count(*) FROM TB_HR_DEV_PRC WHERE dev_id=? AND aply_bgn_ym='202611'",Integer.class,id)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT mon_uprc FROM TB_HR_DEV_PRC WHERE dev_id=? AND aply_bgn_ym='202611'",Integer.class,id)).isEqualTo(200);
        assertThat(db.queryForObject("SELECT mon_uprc FROM TB_HR_DEV_PRC WHERE dev_id=? AND aply_bgn_ym='202701'",Integer.class,id)).isEqualTo(999);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key=? AND chg_item_nm='월 단가'",Integer.class,id)).isEqualTo(2);
    }
    @Test
    @DisplayName("auth side-effect: 개발자 수정은 TB_HR_DEV_PRC·TB_SY_CHG_HIST 저장, 과거 TB_HR_MON_PAY.EMP_TYPE_CD 보존")
    void employeeTypeChangeDoesNotRewriteExistingMonthlyPayrollSnapshot() throws Exception {
        String id=create();
        db.update("INSERT INTO TB_HR_MON_PAY(pay_ym,dev_id,pay_amt,alwnc_amt,insr_cmpny_amt,retire_rsv_amt,emp_type_cd,drct_cls_cd,reg_user_id) VALUES('202610',?,100,0,0,0,'FT','DRCT','admin')",id);
        String nextType=db.queryForObject("SELECT code FROM TB_SY_CODE WHERE code_grp_id='EMP_TYPE' AND code<>'FT' AND use_yn='Y' AND del_yn='N' ORDER BY sort_ord,code LIMIT 1",String.class);
        mvc.perform(put("/api/common/developers/{developerId}",id).contentType("application/json")
            .content(input().put("employeeTypeCode",nextType).put("monthlyRate",200).put("monthlyPay",100).toString()))
            .andExpect(status().isOk())
            .andExpect(com.company.common.support.controller.OpenApiResponseContract.response("PUT","/api/common/developers/{developerId}"))
            .andExpect(jsonPath("$.data.employeeTypeCode").value(nextType));
        assertThat(db.queryForObject("SELECT emp_type_cd FROM TB_HR_DEV WHERE dev_id=?",String.class,id)).isEqualTo(nextType);
        assertThat(db.queryForObject("SELECT emp_type_cd FROM TB_HR_MON_PAY WHERE dev_id=? AND pay_ym='202610'",String.class,id)).isEqualTo("FT");
        assertThat(db.queryForObject("SELECT pay_amt FROM TB_HR_MON_PAY WHERE dev_id=? AND pay_ym='202610'",Integer.class,id)).isEqualTo(100);
        assertThat(db.queryForObject("SELECT mon_uprc FROM TB_HR_DEV_PRC WHERE dev_id=? AND aply_bgn_ym='202611'",Integer.class,id)).isEqualTo(200);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key=? AND chg_item_nm='직원구분' AND bfr_val='FT' AND aft_val=? AND reg_user_id='admin'",Integer.class,id,nextType)).isEqualTo(1);
    }
    @Test void auditFailureRollsBackMasterSkillAndPriceTogether() throws Exception {
        String id=create();db.execute("ALTER TABLE TB_SY_CHG_HIST ADD CONSTRAINT developer_history_guard CHECK(chg_item_nm <> '이름')");
        mvc.perform(put("/api/common/developers/"+id).contentType("application/json").content(input().put("name","롤백 대상").put("monthlyRate",500).toString())).andExpect(status().is5xxServerError());
        assertThat(db.queryForObject("SELECT dev_nm FROM TB_HR_DEV WHERE dev_id=?",String.class,id)).isEqualTo("통합 개발자");
        assertThat(db.queryForObject("SELECT mon_uprc FROM TB_HR_DEV_PRC WHERE dev_id=?",Object.class,id)).isNull();
    }
    @Test void retirementAndCancellationRestoreLinkedLeaveAndBasicCopyAtomically() throws Exception {
        String id=create();
        mvc.perform(post("/api/common/developers/"+id+"/statuses").contentType("application/json").content("{\"statusName\":\"휴직\",\"date\":\"2026-11-01\"}")).andExpect(status().isOk());
        String retired=mvc.perform(post("/api/common/developers/"+id+"/statuses").contentType("application/json").content("{\"statusName\":\"퇴사\",\"date\":\"2026-11-14\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(db.queryForObject("SELECT use_yn FROM TB_HR_DEV WHERE dev_id=?",String.class,id)).isEqualTo("N");
        String statusId=json.readTree(retired).path("data").path("items").get(0).path("statusId").asText();
        mvc.perform(post("/api/common/developers/"+id+"/statuses/"+statusId+"/retirement-cancel").contentType("application/json").content("{\"reason\":\"취소 이유\"}")).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT retire_dt FROM TB_HR_DEV WHERE dev_id=?",Object.class,id)).isNull();
        assertThat(db.queryForObject("SELECT use_yn FROM TB_HR_DEV WHERE dev_id=?",String.class,id)).isEqualTo("Y");
        assertThat(db.queryForObject("SELECT end_dt FROM TB_HR_DEV_STAT WHERE dev_id=?",Object.class,id)).isNull();
        assertThat(db.queryForObject("SELECT chg_rsn FROM TB_SY_CHG_HIST WHERE trgt_key=? AND chg_item_nm='퇴사 취소'",String.class,id)).isEqualTo("취소 이유");
    }
}
