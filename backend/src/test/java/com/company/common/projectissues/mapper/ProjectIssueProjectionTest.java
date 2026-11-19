package com.company.common.projectissues.mapper;

import com.company.common.projectissues.dto.ProjectIssueContracts.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class ProjectIssueProjectionTest {
    Configuration config() throws Exception {
        var c = new Configuration();
        c.setMapUnderscoreToCamelCase(false);
        c.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/projectissues/ProjectIssueMapper.xml";
        try (var in = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(in, c, resource, c.getSqlFragments()).parse();
        }
        return c;
    }
    final String namespace = ProjectIssueMapper.class.getName() + ".";
    final LocalDate today = LocalDate.of(2026, 11, 15);

    @Test void mapperRegisteredAndEveryInterfaceMethodHasStatement() throws Exception {
        var scan = com.company.common.config.MyBatisConfig.class.getAnnotation(org.mybatis.spring.annotation.MapperScan.class);
        assertThat(scan.basePackages()).contains("com.company.common.projectissues.mapper");
        var c = config();
        for (var method : ProjectIssueMapper.class.getDeclaredMethods())
            assertThat(c.hasStatement(namespace + method.getName())).as(method.getName()).isTrue();
    }

    @Test void everySelectedLabelUsesExplicitMappingAndMaterializesAllFieldsWithoutDb() throws Exception {
        var c = config();
        for (String statement : List.of("selectIssues", "selectIssue", "lockProject")) {
            Object parameters = statement.equals("selectIssues")
                ? new Query(new Filter(null, null, null, null), today)
                : Map.of("today", today, "issueId", 3L, "projectId", "P-selected");
            var mapped = c.getMappedStatement(namespace + statement);
            var result = mapped.getResultMaps().get(0);
            var object = result.getType().getDeclaredConstructor().newInstance();
            var meta = c.newMetaObject(object);
            var labels = new LinkedHashSet<String>();
            var matcher = Pattern.compile("(?i)\\bAS\\s+\"([a-zA-Z][a-zA-Z0-9]*)\"")
                .matcher(mapped.getBoundSql(parameters).getSql());
            while (matcher.find()) labels.add(matcher.group(1));
            assertThat(labels).containsExactlyInAnyOrderElementsOf(result.getResultMappings().stream().map(m -> m.getColumn()).toList());
            for (var mapping : result.getResultMappings()) {
                String property = mapping.getProperty();
                Class<?> type = meta.getSetterType(property);
                Object value = type == LocalDate.class ? today : type == Instant.class
                    ? Instant.parse("2026-11-15T00:00:00Z") : type == int.class ? 17 : "value:" + property;
                assertThat(c.getTypeHandlerRegistry().getTypeHandler(type)).isNotNull();
                meta.setValue(property, value);
                assertThat(meta.getValue(property)).isEqualTo(value);
            }
            assertThat(labels).doesNotContain("contractAmount", "budgetAmount");
        }
    }

    @Test void optionalPredicatesBindOnlyPresentValuesAndKeepStrictOverdueRule() throws Exception {
        var statement = config().getMappedStatement(namespace + "selectIssues");
        var defaults = statement.getBoundSql(new Query(new Filter(null, false, null, null), today));
        assertThat(defaults.getSql()).contains("i.ISSUE_STAT_CD IN ('OPEN','ACT')").doesNotContain("IS NULL OR", "OFFSET", "LIMIT");
        assertThat(defaults.getParameterMappings()).extracting(m -> m.getProperty()).containsOnly("today");
        var selected = statement.getBoundSql(new Query(new Filter("CLOSE", true, "HIGH", "P-selected"), today));
        assertThat(selected.getSql()).contains("i.DDLN_DT <", "i.ISSUE_STAT_CD!='CLOSE'", "c.SORT_ORD NULLS LAST, i.DDLN_DT, i.ISSUE_ID");
        assertThat(selected.getParameterMappings()).extracting(m -> m.getProperty())
            .contains("filter.statusCode", "filter.importanceCode", "filter.projectId", "today");
    }

    @Test void updateNeverMutatesOriginalIdentityAndUsesBothKeys() throws Exception {
        var c = config();
        var write = new Write(3L, new Input("P-selected", "정정", today, "MID", "CLOSE", null), "editor");
        String sql = c.getMappedStatement(namespace + "updateIssue").getBoundSql(write).getSql();
        assertThat(sql).contains("MOD_USER_ID=", "MOD_DTTM=clock_timestamp()", "WHERE ISSUE_ID=", "AND PROJ_ID=")
            .doesNotContain("REG_USER_ID=", "REG_DTTM=", "SET PROJ_ID", "DELETE", "TB_SY_CHG_HIST");
        assertThat(c.hasStatement(namespace + "insertIssue!selectKey")).isTrue();
    }
}
