package com.company.common.developers.service;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.codegroups.service.CodeTextResolver;
import com.company.common.developers.mapper.DeveloperMapper;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.support.service.ExcelExportService;
import com.company.common.uploads.controller.CommonUploadController;
import com.company.common.uploads.mapper.UploadMapper;
import com.company.common.uploads.service.CommonUploadService;
import com.company.common.uploads.service.UploadFileStore;
import com.company.common.uploads.service.UploadWorkbookService;
import com.company.common.users.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static com.company.common.support.controller.OpenApiResponseContract.response;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** SFR-102-08 개발자(B.8)·급여(B.1) 업로드를 실제 PostgreSQL에서 검사·반영까지 확인한다. */
@EnabledIfEnvironmentVariable(named="NAVIGATION_TEST_DB_URL",matches="jdbc:postgresql:.*")
@WebMvcTest(CommonUploadController.class)
@ContextConfiguration(classes={CommonUploadController.class,CommonUploadService.class,DeveloperUploadForm.class,PayrollUploadForm.class,
    CommonDeveloperService.class,DeveloperNumberReservations.class,DeveloperManMonthRules.class,CalculationRuleService.class,
    CodeTextResolver.class,CurrentBusinessActor.class,UploadWorkbookService.class,ExcelExportService.class,
    CodeExceptionHandler.class,ApiExceptionHandler.class,DeveloperUploadIntegrationTest.Persistence.class})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DeveloperUploadIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired Owned owned;
    @Autowired UploadWorkbookService workbooks;
    JdbcTemplate db;
    record Owned(String name, Path directory) {}
    @TestConfiguration(proxyBeanMethods=false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean Owned owned() {
            String name="devupload_"+UUID.randomUUID().toString().replace("-","");
            return new Owned(name,Path.of(System.getProperty("java.io.tmpdir"),name));
        }
        @Bean DataSource dataSource(Owned owned) throws Exception {
            String url=System.getenv("NAVIGATION_TEST_DB_URL"),user=System.getenv("NAVIGATION_TEST_DB_USER"),password=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
                statement.execute("CREATE SCHEMA "+owned.name());
            }
            var source=new UnpooledDataSource("org.postgresql.Driver",url,user,password);
            var props=new Properties(); props.setProperty("currentSchema",owned.name()); source.setDriverProperties(props);
            var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();
            return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory=new SqlSessionFactoryBean(); factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/uploads/UploadMapper.xml"),new ClassPathResource("mapper/developers/DeveloperMapper.xml"),
                new ClassPathResource("mapper/UserMapper.xml"),new ClassPathResource("mapper/CalculationRuleMapper.xml"),
                new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var config=new org.apache.ibatis.session.Configuration(); config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config); return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean UploadMapper uploadMapper(SqlSessionTemplate s) { return s.getMapper(UploadMapper.class); }
        @Bean DeveloperMapper developerMapper(SqlSessionTemplate s) { return s.getMapper(DeveloperMapper.class); }
        @Bean UserMapper userMapper(SqlSessionTemplate s) { return s.getMapper(UserMapper.class); }
        @Bean CalculationRuleMapper calculationRuleMapper(SqlSessionTemplate s) { return s.getMapper(CalculationRuleMapper.class); }
        @Bean UserPreferencePeriodSourceMapper userPreferencePeriodSourceMapper(SqlSessionTemplate s) { return s.getMapper(UserPreferencePeriodSourceMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        // 업무 달력: 2026-11-15(한국)
        @Bean BusinessCalendar businessCalendar() { return new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"),ZoneOffset.UTC)); }
        @Bean UploadFileStore uploadFileStore(Owned owned) throws Exception {
            Files.createDirectory(owned.directory()); return new UploadFileStore(owned.directory().resolve("originals"));
        }
    }
    @BeforeEach void setup() { db=new JdbcTemplate(source); }
    @AfterEach void cleanup() throws Exception {
        try(var connection=source.getConnection();var statement=connection.createStatement()) { statement.execute("DROP SCHEMA "+owned.name()+" CASCADE"); }
        if(Files.exists(owned.directory())) try(var paths=Files.walk(owned.directory())) {
            for(var path:paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
    private ResultActions upload(String form, String month, List<String> headers, List<List<String>> rows) throws Exception {
        var request=multipart("/api/common/uploads").file(new MockMultipartFile("file","upload.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",workbooks.tabularFile(headers,rows,null))).param("formTypeCode",form);
        if (month!=null) request.param("yearMonth",month);
        return mvc.perform(request);
    }
    private JsonNode data(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }
    private ResultActions apply(JsonNode checked) throws Exception {
        var body=json.createObjectNode(); if (checked.path("modifiedAt").isNull()) body.putNull("modifiedAt"); else body.put("modifiedAt",checked.path("modifiedAt").asText());
        return mvc.perform(post("/api/common/uploads/"+checked.path("uploadId").asText()+"/apply").contentType("application/json").content(body.toString()));
    }
    private static List<String> developer(String number, String name, String type, String hire) {
        return List.of(number,name,type,"자사","","백엔드 개발","","","직접","본사(직접 영업)",hire,"5,500,000","","Java","서울","광진구","자양3동");
    }

    @Test void templatesAreServedForDeveloperAndPayroll() throws Exception {
        mvc.perform(get("/api/common/uploads/templates/B8")).andExpect(status().isOk());
        mvc.perform(get("/api/common/uploads/templates/B1")).andExpect(status().isOk());
    }

    @Test void developerCheckPlansNumbersWarnsAndApplyCreatesRows() throws Exception {
        db.update("INSERT INTO TB_HR_DEV(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,jncmp_dt,work_stat_cd,use_yn,reg_user_id) "
            +"VALUES('Dold','K-0001','김민수','FT','BE','DRCT','HQ','2021-03-02','IDLE','Y','admin')");
        var rows=List.of(developer("","박새로","정규직","2024-03-02"),
            developer("","김민수","정규직","2021-03-02"),
            developer("K-0001","중복번호","정규직",""),
            developer("X-9","직접번호","정규직",""));
        var checked=data(upload("B8",null,DeveloperUploadForm.HEADERS,rows).andExpect(status().isOk())
            .andExpect(response("POST","/api/common/uploads")));
        assertThat(checked.path("errorCount").asInt()).isEqualTo(1);
        assertThat(checked.toString()).contains("이미 등록된 관리번호입니다.").contains("같은 이름·입사일의 개발자가 이미 있습니다(K-0001).")
            .contains("'자양동'으로 바꿔 읽었습니다.");
        // 관리번호를 비운 줄에 붙을 번호(K-0001은 이미 있어 건너뜀)
        assertThat(checked.path("plannedDevelopers").findValuesAsText("managementNumber")).containsExactly("K-0002","K-0003");
        // 오류가 하나라도 있으면 반영하지 않는다
        apply(checked).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT count(*) FROM TB_HR_DEV",Integer.class)).isEqualTo(1);

        var clean=data(upload("B8",null,DeveloperUploadForm.HEADERS,List.of(rows.get(0),rows.get(3))).andExpect(status().isOk()));
        assertThat(clean.path("errorCount").asInt()).isZero();
        var applied=data(apply(clean).andExpect(status().isOk()).andExpect(response("POST","/api/common/uploads/{uploadId}/apply")));
        assertThat(applied.path("statusName").asText()).isEqualTo("반영");
        assertThat(applied.path("plannedDevelopers").toString()).contains("K-0002");
        assertThat(db.queryForObject("SELECT count(*) FROM TB_HR_DEV",Integer.class)).isEqualTo(3);
        assertThat(db.queryForObject("SELECT home_addr FROM TB_HR_DEV WHERE mng_no='K-0002'",String.class)).isEqualTo("서울 광진구 자양동");
        assertThat(db.queryForObject("SELECT part_id FROM TB_HR_DEV WHERE mng_no='X-9'",String.class)).isEqualTo("HQ");
        assertThat(db.queryForObject("SELECT count(*) FROM TB_HR_DEV_SKILL s JOIN TB_HR_DEV d ON d.dev_id=s.dev_id WHERE d.mng_no='K-0002'",Integer.class)).isEqualTo(1);
    }

    private static List<String> lives(String name, String sido, String sgg, String dong) {
        return List.of("",name,"정규직","자사","","백엔드 개발","","","직접","본사","","","","",sido,sgg,dong);
    }
    @Test void addressMatchesShortAndMergedProvinceNamesAndKeepsMissingAsWarnings() throws Exception {
        // SFR-021-04: 「서울시」→서울, 합쳐지기 전 「광주광역시」→전남광주, 세종은 시·군·구 없이 동, 못 찾은 시·도는 오류가 아니라 경고
        var rows=List.of(lives("서울시민","서울시","광진구","자양동"), lives("목포시민","전라남도","목포시","용당동"),
            lives("세종시민","세종특별자치시","","반곡동"), lives("모름","없는도","",""));
        var checked=data(upload("B8",null,DeveloperUploadForm.HEADERS,rows).andExpect(status().isOk()));
        assertThat(checked.path("errorCount").asInt()).isZero();
        assertThat(checked.toString()).contains("거주지를 못 찾아 비웠습니다.");
        apply(checked).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT home_addr FROM TB_HR_DEV WHERE dev_nm='서울시민'",String.class)).isEqualTo("서울 광진구 자양동");
        assertThat(db.queryForObject("SELECT home_addr FROM TB_HR_DEV WHERE dev_nm='목포시민'",String.class)).isEqualTo("전남광주 목포시 용당동");
        assertThat(db.queryForObject("SELECT home_addr FROM TB_HR_DEV WHERE dev_nm='세종시민'",String.class)).isEqualTo("세종 반곡동");
        assertThat(db.queryForObject("SELECT home_addr FROM TB_HR_DEV WHERE dev_nm='모름'",String.class)).isNull();
    }
    private void developerRow(String id, String number, String name, String type, String position, String retire) {
        db.update("INSERT INTO TB_HR_DEV(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,jbps_cd,drct_cls_cd,part_id,retire_dt,work_stat_cd,use_yn,reg_user_id) "
            +"VALUES(?,?,?,?,'BE',?,'DRCT','HQ',?::date,'IDLE',?,'admin')",id,number,name,type,position,retire,retire==null?"Y":"N");
    }
    @Test void payrollReplacesTheWholeMonthAndComputesAllowanceRetirementAndWithholding() throws Exception {
        developerRow("D1","K-0001","정규팀장","FT","TEAM",null);
        developerRow("D2","K-0002","프리","FL","MEMBER",null);
        developerRow("D3","K-0003","예전사람","FT","MEMBER",null);
        db.update("INSERT INTO TB_HR_MON_PAY(pay_ym,dev_id,pay_amt,alwnc_amt,insr_cmpny_amt,retire_rsv_amt,emp_type_cd,drct_cls_cd,reg_user_id) VALUES('202610','D3',1,0,0,0,'FT','DRCT','admin')");
        var rows=List.of(List.of("K-0001","정규팀장","4,000,000","","350000","100000",""),
            List.of("K-0002","다른이름","5000000","","","",""));
        var checked=data(upload("B1","2026-10",PayrollUploadForm.HEADERS,rows).andExpect(status().isOk())
            .andExpect(response("POST","/api/common/uploads")));
        assertThat(checked.path("yearMonth").asText()).isEqualTo("2026-10");
        assertThat(checked.path("warningCount").asInt()).isEqualTo(1);
        assertThat(checked.toString()).contains("개발자 풀 이름(프리)과 다릅니다.");
        apply(checked).andExpect(status().isOk());
        // 그 달 전체 교체: 파일에 없는 D3 줄은 지워진다
        assertThat(db.queryForObject("SELECT count(*) FROM TB_HR_MON_PAY WHERE pay_ym='202610'",Integer.class)).isEqualTo(2);
        var team=db.queryForMap("SELECT * FROM TB_HR_MON_PAY WHERE pay_ym='202610' AND dev_id='D1'");
        assertThat(((Number)team.get("alwnc_amt")).longValue()).isEqualTo(200000);          // 식-08 팀장
        assertThat(((Number)team.get("insr_cmpny_amt")).longValue()).isEqualTo(376000);     // 4,000,000 × 9.4%
        assertThat(((Number)team.get("retire_rsv_amt")).longValue()).isEqualTo(349860);     // 4,200,000 × 8.33% 버림
        assertThat(((Number)team.get("wthld_tax_amt")).longValue()).isZero();               // 정규직은 원천세 없음
        assertThat(((Number)team.get("real_pay_amt")).longValue()).isEqualTo(3750000);      // 4,200,000 − 350,000 − 100,000
        assertThat(team.get("emp_type_cd")).isEqualTo("FT");
        var free=db.queryForMap("SELECT * FROM TB_HR_MON_PAY WHERE pay_ym='202610' AND dev_id='D2'");
        assertThat(((Number)free.get("wthld_tax_amt")).longValue()).isEqualTo(165000);      // 5,000,000 × 3.3%
        assertThat(((Number)free.get("insr_cmpny_amt")).longValue()).isZero();
        assertThat(((Number)free.get("retire_rsv_amt")).longValue()).isZero();
    }
    @Test void payrollRejectsClosedOrFutureMonthsAndPayAfterRetirement() throws Exception {
        developerRow("D1","K-0001","퇴사자","FT","MEMBER","2026-09-30");
        db.update("UPDATE TB_SY_OPER_STD SET std_val=202601 WHERE std_cd='CASH_BASE_YM'");
        upload("B1","2025-12",PayrollUploadForm.HEADERS,List.of(List.of("K-0001","퇴사자","1","","","",""))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("yearMonth")).andExpect(jsonPath("$.message").value("마감한 달은 올릴 수 없습니다."));
        upload("B1","2026-12",PayrollUploadForm.HEADERS,List.of(List.of("K-0001","퇴사자","1","","","",""))).andExpect(status().isBadRequest());
        upload("B1",null,PayrollUploadForm.HEADERS,List.of(List.of("K-0001","퇴사자","1","","","",""))).andExpect(status().isBadRequest());
        // 퇴사·사용 중지된 사람도 퇴사한 달까지는 올린다
        var september=data(upload("B1","2026-09",PayrollUploadForm.HEADERS,List.of(List.of("K-0001","퇴사자","3000000","","","",""))).andExpect(status().isOk()));
        assertThat(september.path("errorCount").asInt()).isZero();
        var october=data(upload("B1","2026-10",PayrollUploadForm.HEADERS,List.of(List.of("K-0001","퇴사자","3000000","","","",""))).andExpect(status().isOk()));
        assertThat(october.toString()).contains("퇴사한 달(2026-09) 뒤의 급여는 올릴 수 없습니다.");
    }
}
