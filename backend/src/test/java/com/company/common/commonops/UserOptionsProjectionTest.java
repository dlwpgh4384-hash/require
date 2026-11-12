package com.company.common.commonops;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;

class UserOptionsProjectionTest {
    private Configuration configuration() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        try (var input = new ClassPathResource("mapper/UserOptionsMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, config, "mapper/UserOptionsMapper.xml", config.getSqlFragments()).parse();
        }
        return config;
    }

    private <T> T project(Configuration config, String method, T row, Map<String, Object> labels) {
        var statement = config.getMappedStatement(UserOptionsMapper.class.getName() + "." + method);
        var map = statement.getResultMaps().get(0);
        assertThat(map.getAutoMapping()).isFalse();
        assertThat(map.getType()).isEqualTo(row.getClass());
        assertThat(map.getResultMappings()).extracting(mapping -> mapping.getColumn())
            .containsExactlyElementsOf(labels.keySet());
        var meta = config.newMetaObject(row);
        String sql = statement.getBoundSql(null).getSql().replaceAll("\\s+", " ").trim();
        for (var mapping : map.getResultMappings()) {
            assertThat(sql).contains("AS " + mapping.getColumn());
            assertThat(mapping.getJavaType()).isEqualTo(meta.getSetterType(mapping.getProperty()));
            assertThat(mapping.getTypeHandler()).isNotNull();
            meta.setValue(mapping.getProperty(), labels.get(mapping.getColumn()));
        }
        return row;
    }

    @Test
    void roleLabelsAndBooleanTypeMatchExplicitBeanMapping() throws Exception {
        var config = configuration();
        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put("role_cd", "CUSTOM_PROJECT_ROLE");
        labels.put("role_name", "담당 역할");
        labels.put("assigned_project_scope", true);
        var row = project(config, "selectRoles", new UserOptionRows.Role(), labels);
        assertThat(row.getRoleCd()).isEqualTo("CUSTOM_PROJECT_ROLE");
        assertThat(row.getRoleName()).isEqualTo("담당 역할");
        assertThat(row.isAssignedProjectScope()).isTrue();
        labels.put("assigned_project_scope", false);
        assertThat(project(config, "selectRoles", row, labels).isAssignedProjectScope()).isFalse();
        assertThat(config.getMappedStatement(UserOptionsMapper.class.getName() + ".selectRoles")
            .getBoundSql(null).getSql().replaceAll("\\s+", " "))
            .contains("r.ROLE_ID AS role_cd", "r.ROLE_NM AS role_name",
                "r.DATA_SCOPE_CD = 'PROJ'", "ORDER BY r.SORT_ORD, r.ROLE_ID");
    }

    @Test
    void projectLabelsAndStableOrderingDoNotIntroduceScopeOrStatusRestrictions() throws Exception {
        var config = configuration();
        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put("project_id", "projection-project");
        labels.put("project_name", "모든 상태 프로젝트");
        var row = project(config, "selectProjects", new UserOptionRows.Project(), labels);
        assertThat(row.getProjectId()).isEqualTo("projection-project");
        assertThat(row.getProjectName()).isEqualTo("모든 상태 프로젝트");
        var statement = config.getMappedStatement(UserOptionsMapper.class.getName() + ".selectProjects");
        assertThat(statement.getBoundSql(null).getSql().replaceAll("\\s+", " "))
            .contains("p.PROJ_ID AS project_id", "p.PROJ_NM AS project_name",
                "FROM TB_PJ_PROJ p", "ORDER BY p.PROJ_NM, p.PROJ_ID")
            .doesNotContain("WHERE", "JOIN", "LIMIT");
        assertThat(statement.getBoundSql(null).getParameterMappings()).isEmpty();
    }

    @Test
    void userStatesUseOnlyTheirCodeGroupAndPreserveDatabaseLabels() throws Exception {
        var config = configuration();
        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put("code", "LOCK");
        labels.put("name", "잠김");
        var row = project(config, "selectUserStates", new UserOptionRows.UserState(), labels);
        assertThat(row.getCode()).isEqualTo("LOCK");
        assertThat(row.getName()).isEqualTo("잠김");
        assertThat(config.getMappedStatement(UserOptionsMapper.class.getName() + ".selectUserStates")
            .getBoundSql(null).getSql().replaceAll("\\s+", " "))
            .contains("FROM TB_SY_CODE c", "WHERE c.CODE_GRP_ID = 'USER_STAT'",
                "ORDER BY c.SORT_ORD, c.CODE");
    }
}
