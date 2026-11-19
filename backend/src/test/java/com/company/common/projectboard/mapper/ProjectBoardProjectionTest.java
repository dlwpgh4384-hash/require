package com.company.common.projectboard.mapper;

import com.company.common.projectboard.dto.CommonProjectBoardContracts.Filter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class ProjectBoardProjectionTest {
    Configuration config() throws Exception {
        var c = new Configuration();
        c.setMapUnderscoreToCamelCase(false);
        c.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/projectboard/ProjectBoardMapper.xml";
        try (var in = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(in, c, resource, c.getSqlFragments()).parse();
        }
        return c;
    }
    Map<String, Object> params(Filter filter) {
        return Map.of("filter", filter, "today", LocalDate.of(2026, 11, 15), "month", "202611");
    }
    @Test void allStatementsAreRegistered() throws Exception {
        var c = config();
        for (var method : ProjectBoardMapper.class.getDeclaredMethods())
            assertThat(c.hasStatement(ProjectBoardMapper.class.getName() + "." + method.getName())).isTrue();
    }
    @Test void everySourceLabelHasAnExplicitMappingAndMaterializesANonDefaultValue() throws Exception {
        var c = config();
        var statement = c.getMappedStatement(ProjectBoardMapper.class.getName() + ".sources");
        var result = statement.getResultMaps().get(0);
        var value = new ProjectBoardSource();
        var meta = c.newMetaObject(value);
        String sql = statement.getBoundSql(params(new Filter(null, null, null))).getSql();
        for (var mapping : result.getResultMappings()) {
            String label = mapping.getColumn();
            assertThat(sql).contains("AS \"" + label + "\"");
            var type = meta.getSetterType(mapping.getProperty());
            Object sample = type == BigDecimal.class ? new BigDecimal("123.45")
                : type == LocalDate.class ? LocalDate.of(2026, 11, 15) : "value:" + label;
            assertThat(c.getTypeHandlerRegistry().getTypeHandler(type)).isNotNull();
            meta.setValue(mapping.getProperty(), sample);
            assertThat(meta.getValue(mapping.getProperty())).isEqualTo(sample);
        }
        assertThat(result.getResultMappings()).hasSize(ProjectBoardSource.class.getFields().length);
    }
    @Test void issueConstructorLabelsTypesAndOrderMatchTheFoundationRecord() throws Exception {
        var c = config();
        var mapping = c.getResultMap(ProjectBoardMapper.class.getName() + ".issue");
        var components = mapping.getType().getRecordComponents();
        var args = mapping.getConstructorResultMappings();
        assertThat(args).hasSize(components.length);
        for (int i = 0; i < components.length; i++) {
            assertThat(args.get(i).getColumn()).isEqualTo(components[i].getName());
            assertThat(args.get(i).getJavaType()).isEqualTo(components[i].getType());
        }
        var issue = mapping.getType().getDeclaredConstructor(String.class, String.class,
            String.class, LocalDate.class).newInstance("71", "HIGH", "OPEN", LocalDate.of(2026, 11, 14));
        assertThat(c.newMetaObject(issue).getValue("issueId")).isEqualTo("71");
    }
    @Test void optionalPredicatesBindOnlyWhenPresent() throws Exception {
        var statement = config().getMappedStatement(ProjectBoardMapper.class.getName() + ".sources");
        var none = statement.getBoundSql(params(new Filter(null, null, null)));
        var chosen = statement.getBoundSql(params(new Filter("위험", "TK", "sales")));
        assertThat(none.getParameterMappings()).extracting(p -> p.getProperty())
            .doesNotContain("filter.contractTypeCode", "filter.salesPartId");
        assertThat(chosen.getParameterMappings()).extracting(p -> p.getProperty())
            .contains("filter.contractTypeCode", "filter.salesPartId").doesNotContain("filter.signal");
        assertThat(chosen.getSql()).doesNotContain("IS NULL OR", "OFFSET", "MOD_DTTM");
    }
    @Test void sourceUsesWrittenDateSavedBudgetExistingCostsAndDeterministicPmPair() throws Exception {
        String sql = config().getMappedStatement(ProjectBoardMapper.class.getName() + ".sources")
            .getBoundSql(params(new Filter(null, null, null))).getSql();
        assertThat(sql).contains("MAX(m.WRT_DT)", "m.WRT_DT DESC, m.MEMO_SN DESC", "p.BDGT_AMT",
            "SUM(c.LABOR_ALLOC_AMT+c.DRCT_EXPS_AMT)", "SUM(e.EXPS_AMT)",
            "pm.USER_ID AS \"pmUserId\"", "pm.USER_NM AS \"pmName\"",
            "sc.SCOPE_TYPE_CD='PROJ'", "r.ROLE_ID='PM'", "ORDER BY u.USER_ID ASC LIMIT 1")
            .doesNotContain("TEAM_EXPS_AMT", "PROJ_EXPS_AMT", "TB_PJ_ASGN", "MOD_DTTM");
    }
}
