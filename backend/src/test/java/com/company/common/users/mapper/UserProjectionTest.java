package com.company.common.users.mapper;

import com.company.common.users.service.CommonUserService;
import com.company.common.users.dto.UserRequests;
import com.company.common.users.service.UserTestData;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class UserProjectionTest {
    private Configuration configuration() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        try (var input = new ClassPathResource("mapper/UserMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, config, "mapper/UserMapper.xml", config.getSqlFragments()).parse();
        }
        return config;
    }
    @Test void explicitAccountProjectionPreservesAllNonDefaultFieldsAndMicroseconds() throws Exception {
        var config = configuration();
        var labels = new LinkedHashMap<String, Object>();
        labels.put("user_id", "test"); labels.put("user_nm", "합성 사용자"); labels.put("email_addr", "test@example.invalid");
        labels.put("user_stat_cd", "LOCK"); labels.put("mod_dttm", UserTestData.TOKEN);
        labels.put("last_login_dttm", UserTestData.TOKEN.minus(java.time.Duration.ofDays(1)));
        labels.put("temp_pwd_yn", "Y");
        var row = new UserRows.Account();
        materialize(config, "account", row, labels);
        assertThat(row.userId).isEqualTo("test"); assertThat(row.name).isEqualTo("합성 사용자");
        assertThat(row.email).isEqualTo("test@example.invalid"); assertThat(row.userStatCd).isEqualTo("LOCK");
        assertThat(CommonUserService.time(row.modDttm)).isEqualTo("2026-01-02T03:04:05.123456Z");
        assertThat(row.lastAccessAt).isEqualTo(UserTestData.TOKEN.minus(java.time.Duration.ofDays(1)));
        var statement = config.getMappedStatement(UserMapper.class.getName() + ".selectAccount");
        assertThat(statement.getBoundSql(Map.of("id", "test")).getSql()).contains("u.user_id", "u.user_nm", "u.email_addr", "u.last_login_dttm");
    }
    @Test void projectOwnerAndAccessLogMappingsAgreeWithTheirLabelsAndTypes() throws Exception {
        var config = configuration();
        var project = new UserRows.Project();
        materialize(config, "project", project, Map.of("proj_id", "project-a", "proj_nm", "합성 프로젝트"));
        assertThat(project.projectId).isEqualTo("project-a"); assertThat(project.projectName).isEqualTo("합성 프로젝트");
        var owner = new UserRows.Owner();
        materialize(config, "owner", owner, Map.of("user_id", "oldpm", "proj_id", "project-a", "proj_nm", "합성 프로젝트"));
        assertThat(owner.userId).isEqualTo("oldpm"); assertThat(owner.projectId).isEqualTo("project-a"); assertThat(owner.projectName).isEqualTo("합성 프로젝트");
        var log = new UserRows.Log();
        materialize(config, "log", log, Map.of("act_dttm", UserTestData.TOKEN, "action", "사용자 등록", "trgt_desc", "대상 test"));
        assertThat(log.occurredAt).isEqualTo(UserTestData.TOKEN); assertThat(log.action).isEqualTo("사용자 등록"); assertThat(log.target).isEqualTo("대상 test");
    }
    private void materialize(Configuration config, String name, Object row, Map<String, Object> labels) {
        var map = config.getResultMap(UserMapper.class.getName() + "." + name);
        assertThat(map.getAutoMapping()).isFalse();
        assertThat(map.getResultMappings()).extracting(mapping -> mapping.getColumn()).containsExactlyInAnyOrderElementsOf(labels.keySet());
        var meta = config.newMetaObject(row);
        for (var mapping : map.getResultMappings()) {
            assertThat(mapping.getJavaType()).isEqualTo(meta.getSetterType(mapping.getProperty()));
            assertThat(mapping.getTypeHandler()).isNotNull();
            meta.setValue(mapping.getProperty(), labels.get(mapping.getColumn()));
            assertThat(meta.getValue(mapping.getProperty())).isEqualTo(labels.get(mapping.getColumn()));
        }
    }
    @Test void optionalPredicatesAreIdenticalForListAndCountAndHaveNoNullBinds() throws Exception {
        var config = configuration();
        for (var filter : List.of(UserRequests.filter(1, 20, null, null, null), UserRequests.filter(2, 50, "PM", "LOCK", "_%!"))) {
            String list = config.getMappedStatement(UserMapper.class.getName() + ".selectUsers").getBoundSql(filter).getSql().replaceAll("\\s+", " ").trim();
            String count = config.getMappedStatement(UserMapper.class.getName() + ".countUsers").getBoundSql(filter).getSql().replaceAll("\\s+", " ").trim();
            assertThat(list.substring(list.indexOf("FROM TB_SY_USER u"), list.indexOf("ORDER BY")))
                .isEqualTo(count.substring(count.indexOf("FROM TB_SY_USER u")) + " ");
            assertThat(list).contains("ORDER BY u.user_nm, u.user_id").doesNotContain("IS NULL OR");
        }
        assertThat(UserRequests.filter(1,20,null,null,"_%!").keyword()).isEqualTo("%!_!%!!%");
    }
    @Test void logsHaveStableOrderAndEveryVersionWriteUsesNullSafeComparison() throws Exception {
        var config = configuration();
        String sql = config.getMappedStatement(UserMapper.class.getName() + ".selectLogs")
            .getBoundSql(Map.of("id", "test", "filter", UserRequests.filter(1,20,null,null,null))).getSql();
        assertThat(sql).contains("l.act_dttm DESC, l.log_id DESC");
        var parameters = new java.util.HashMap<String,Object>();
        parameters.put("id", "test"); parameters.put("token", null); parameters.put("state", "USE"); parameters.put("unlock", true); parameters.put("hash", "redacted");
        for (String method : List.of("touch", "resetPassword", "changeState")) {
            String update = config.getMappedStatement(UserMapper.class.getName() + "." + method).getBoundSql(parameters).getSql();
            assertThat(update).contains("IS NOT DISTINCT FROM", "1 microsecond", "mod_user_id='admin'");
        }
    }
}
