package com.company.common.uploads;

import com.company.common.controller.*;
import com.company.common.mapper.*;
import com.company.common.schema.SchemaPreparation;
import com.company.common.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.DriverManager;
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
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static com.company.common.contracts.OpenApiResponseContract.response;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="NAVIGATION_TEST_DB_URL",matches="jdbc:postgresql:.*")
@WebMvcTest({CommonUploadController.class,CommonClientController.class})
@ContextConfiguration(classes={CommonUploadController.class,CommonUploadService.class,ClientUploadRules.class,
    UploadClientWriter.class,CommonClientController.class,CommonClientService.class,CurrentBusinessActor.class,
    CodeTextResolver.class,UploadWorkbookService.class,ExcelExportService.class,
    CodeExceptionHandler.class,ApiExceptionHandler.class,UploadApplyIntegrationTest.Persistence.class})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UploadApplyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired OwnedSchema owned;
    @Autowired UploadWorkbookService workbooks;
    @Autowired UploadFileStore files;
    @Autowired CommonUploadService service;
    @Autowired PaymentAccountCipher cipher;
    JdbcTemplate db;
    record OwnedSchema(String name,Path directory) {}
    @TestConfiguration(proxyBeanMethods=false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean OwnedSchema ownedSchema() {
            String name="uploads_"+UUID.randomUUID().toString().replace("-","");
            return new OwnedSchema(name,Path.of(System.getProperty("java.io.tmpdir"),name));
        }
        @Bean DataSource dataSource(OwnedSchema owned) throws Exception {
            String url=System.getenv("NAVIGATION_TEST_DB_URL"),user=System.getenv("NAVIGATION_TEST_DB_USER"),password=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
                assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
                statement.execute("CREATE SCHEMA "+owned.name());
            }
            var source=new UnpooledDataSource("org.postgresql.Driver",url,user,password);
            var properties=new Properties(); properties.setProperty("currentSchema",owned.name()); source.setDriverProperties(properties);
            var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();
            return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory=new SqlSessionFactoryBean(); factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/uploads/UploadMapper.xml"),new ClassPathResource("mapper/ClientMapper.xml"),
                new ClassPathResource("mapper/UserMapper.xml"),new ClassPathResource("mapper/CalculationRuleMapper.xml"));
            var config=new org.apache.ibatis.session.Configuration(); config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config); return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean UploadMapper uploadMapper(SqlSessionTemplate session) { return session.getMapper(UploadMapper.class); }
        @Bean ClientMapper clientMapper(SqlSessionTemplate session) { return session.getMapper(ClientMapper.class); }
        @Bean UserMapper userMapper(SqlSessionTemplate session) { return session.getMapper(UserMapper.class); }
        @Bean CalculationRuleMapper calculationRuleMapper(SqlSessionTemplate session) { return session.getMapper(CalculationRuleMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean BusinessCalendar businessCalendar() { return new BusinessCalendar(); }
        @Bean UploadFileStore uploadFileStore(OwnedSchema owned) throws Exception {
            Files.createDirectory(owned.directory()); return new UploadFileStore(owned.directory().resolve("originals"));
        }
        @Bean PaymentAccountCipher paymentAccountCipher(OwnedSchema owned) { return new PaymentAccountCipher(owned.directory().resolve("keys")); }
    }
    @BeforeEach void setup() { db=new JdbcTemplate(source); }
    @AfterEach void cleanup() throws Exception {
        try(var connection=source.getConnection();var statement=connection.createStatement()) { statement.execute("DROP SCHEMA "+owned.name()+" CASCADE"); }
        if(Files.exists(owned.directory())) try(var paths=Files.walk(owned.directory())) {
            for(var path:paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
    private List<String> row(String number) {
        return List.of("CUST","검사 거래처",number,"대표","","담당","010","client@example.test","","","");
    }
    private String check(List<List<String>> rows) throws Exception {
        byte[] bytes=workbooks.tabularFile(ClientUploadRules.HEADERS,rows,null);
        var response=mvc.perform(multipart("/api/common/uploads").file(new MockMultipartFile("file","clients.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes)).param("formTypeCode","B7"))
            .andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).at("/data/uploadId").asText();
    }
    private org.springframework.test.web.servlet.ResultActions apply(String id) throws Exception {
        return mvc.perform(post("/api/common/uploads/"+id+"/apply").contentType("application/json").content("{\"modifiedAt\":null}"));
    }
    private long count(String table) { return db.queryForObject("SELECT count(*) FROM "+table,Long.class); }
    @Test void checkedRecordIsDurableAndClosingIsNotApplyAndReopenedStoreUsesSameBytes() throws Exception {
        String id=check(List.of(row("123-45-67890")));
        assertThat(count("TB_BS_CLNT")).isZero();
        assertThat(db.queryForObject("SELECT upld_stat_cd FROM TB_SY_UPLD WHERE upld_id=?",String.class,Long.parseLong(id))).isEqualTo("CHECKED");
        assertThat(db.queryForObject("SELECT reg_user_id FROM TB_SY_UPLD WHERE upld_id=?",String.class,Long.parseLong(id))).isEqualTo("admin");
        mvc.perform(get("/api/common/uploads/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.successCount").value(1));
        assertThat(new UploadFileStore(owned.directory().resolve("originals")).read(Long.parseLong(id))).isEqualTo(files.read(Long.parseLong(id)));
        assertThat(count("TB_BS_CLNT")).isZero();
    }
    @Test
    @DisplayName("auth happy side-effect: PostgreSQL TB_BS_CLNT·TB_SY_UPLD·TB_SY_UPLD_ERR 전량 반영 후 DB 재조회")
    void warningOnlyApplyCommitsAllClientsHistoriesAndCountsWithEncryptedAccount() throws Exception {
        db.update("INSERT INTO TB_SY_CODE_MAP(src_text,code_grp_id,code,reg_user_id) VALUES('이관고객','CLNT_TYPE','CUST','admin')");
        var warning=new ArrayList<>(row("123-45-67890")); warning.set(0,"이관고객");
        String bank=db.queryForObject("SELECT code FROM TB_SY_CODE WHERE code_grp_id='BANK' AND use_yn='Y' AND del_yn='N' ORDER BY sort_ord LIMIT 1",String.class);
        var vendor=new ArrayList<>(row("987-65-43210")); vendor.set(0,"VNDR"); vendor.set(7,""); vendor.set(8,bank); vendor.set(9,"12345678"); vendor.set(10,"예금주");
        String id=check(List.of(warning,vendor)); assertThat(count("TB_BS_CLNT")).isZero();
        mvc.perform(get("/api/common/uploads/"+id)).andExpect(jsonPath("$.data.warningCount").value(1))
            .andExpect(jsonPath("$.data.issues[0].inputValue").value("이관고객"));
        assertThat(db.queryForObject("SELECT rslt_type_cd FROM TB_SY_UPLD_ERR WHERE upld_id=?",String.class,Long.parseLong(id))).isEqualTo("WARN");
        mvc.perform(post("/api/common/uploads/{uploadId}/apply", id).contentType("application/json").content("{\"modifiedAt\":null}"))
            .andExpect(status().isOk()).andExpect(response("POST", "/api/common/uploads/{uploadId}/apply"))
            .andExpect(jsonPath("$.data.successCount").value(1)).andExpect(jsonPath("$.data.warningCount").value(1));
        assertThat(count("TB_BS_CLNT")).isEqualTo(2);
        assertThat(db.queryForObject("SELECT succ_cnt+warn_cnt+err_cnt FROM TB_SY_UPLD WHERE upld_id=?",Integer.class,Long.parseLong(id))).isEqualTo(2);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_BS_CLNT WHERE use_yn='Y' AND reg_user_id='admin' AND clnt_id ~ '^[a-f0-9]{20}$'",Long.class)).isEqualTo(2);
        String encrypted=db.queryForObject("SELECT acct_no_enc FROM TB_BS_CLNT WHERE biz_reg_no='987-65-43210'",String.class);
        assertThat(encrypted).isNotEqualTo("12345678"); assertThat(cipher.decrypt(encrypted)).isEqualTo("12345678");
        mvc.perform(get("/api/common/clients")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2));
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_tbl_nm='TB_BS_CLNT'",Long.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT aply_dttm IS NOT NULL AND mod_user_id='admin' FROM TB_SY_UPLD WHERE upld_id=?",Boolean.class,Long.parseLong(id))).isTrue();
        apply(id).andExpect(status().isOk()); assertThat(count("TB_BS_CLNT")).isEqualTo(2);
    }
    @Test void concurrentBusinessNumberCreationBetweenCheckAndApplyLeavesExistingValuesAndNoPartialInsert() throws Exception {
        String id=check(List.of(row("123-45-67890"),row("987-65-43210")));
        db.update("INSERT INTO TB_BS_CLNT(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES('existing','CUST','먼저 저장','987-65-43210','admin')");
        apply(id).andExpect(status().isOk()).andExpect(jsonPath("$.data.errorCount").value(1));
        assertThat(count("TB_BS_CLNT")).isEqualTo(1);
        assertThat(db.queryForObject("SELECT clnt_nm FROM TB_BS_CLNT WHERE clnt_id='existing'",String.class)).isEqualTo("먼저 저장");
        assertThat(db.queryForObject("SELECT mod_dttm FROM TB_SY_UPLD WHERE upld_id=?",java.sql.Timestamp.class,Long.parseLong(id))).isNotNull();
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_tbl_nm='TB_BS_CLNT'",Long.class)).isZero();
    }
    @Test void oneInvalidRowBlocksWholeFileAndIssuesReadBackFromDatabase() throws Exception {
        var bad=new ArrayList<>(row("987-65-43210")); bad.set(7,"");
        String id=check(List.of(row("123-45-67890"),bad));
        apply(id).andExpect(status().isOk()).andExpect(jsonPath("$.data.errorCount").value(1));
        assertThat(count("TB_BS_CLNT")).isZero(); assertThat(count("TB_SY_UPLD_ERR")).isEqualTo(1);
        mvc.perform(get("/api/common/uploads/"+id)).andExpect(jsonPath("$.data.issues[0].row").value(3))
            .andExpect(jsonPath("$.data.issues[0].column").value("이메일"));
    }
    @Test
    @DisplayName("side-effect: PostgreSQL SQL 실패 시 TB_BS_CLNT·TB_SY_CHG_HIST 전체 rollback, TB_SY_UPLD 상태 보존")
    void lateSqlFailureRollsBackEarlierClientsHistoriesAndUploadStatus() throws Exception {
        var bad=new ArrayList<>(row("987-65-43210")); bad.set(1,"저장실패");
        String id=check(List.of(row("123-45-67890"),bad));
        db.execute("ALTER TABLE TB_BS_CLNT ADD CONSTRAINT reject_import CHECK (clnt_nm <> '저장실패')");
        mvc.perform(post("/api/common/uploads/{uploadId}/apply", id).contentType("application/json").content("{\"modifiedAt\":null}"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false));
        assertThat(count("TB_BS_CLNT")).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_tbl_nm='TB_BS_CLNT'",Long.class)).isZero();
        assertThat(db.queryForObject("SELECT upld_stat_cd FROM TB_SY_UPLD WHERE upld_id=?",String.class,Long.parseLong(id))).isEqualTo("CHECKED");
    }
    @Test void historyFailureRollsBackAllWrites() throws Exception {
        String id=check(List.of(row("123-45-67890")));
        db.execute("ALTER TABLE TB_SY_CHG_HIST ADD CONSTRAINT reject_import_history CHECK (trgt_tbl_nm <> 'TB_BS_CLNT')");
        apply(id).andExpect(status().isInternalServerError()); assertThat(count("TB_BS_CLNT")).isZero();
        assertThat(db.queryForObject("SELECT upld_stat_cd FROM TB_SY_UPLD WHERE upld_id=?",String.class,Long.parseLong(id))).isEqualTo("CHECKED");
    }
    @Test void simultaneousApplyIsIdempotentAndCommitsOneBatch() throws Exception {
        String id=check(List.of(row("123-45-67890"),row("987-65-43210")));
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var tasks=List.<java.util.concurrent.Callable<UploadContracts.Result>>of(
                () -> service.postCommonUploadApply(id,new UploadContracts.Apply(null)),
                () -> service.postCommonUploadApply(id,new UploadContracts.Apply(null)));
            for(var future:executor.invokeAll(tasks)) assertThat(future.get().errorCount()).isZero();
        } finally { executor.shutdownNow(); }
        assertThat(count("TB_BS_CLNT")).isEqualTo(2);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_tbl_nm='TB_BS_CLNT'",Long.class)).isEqualTo(2);
    }
}
