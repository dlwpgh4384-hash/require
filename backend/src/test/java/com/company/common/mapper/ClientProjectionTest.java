package com.company.common.mapper;

import com.company.common.dto.ClientContracts.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class ClientProjectionTest {
    private Configuration config() throws Exception {
        var config = new Configuration(); config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/ClientMapper.xml";
        try (var stream = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
        }
        return config;
    }
    private String sql(Configuration config, String method, Object parameters) {
        return config.getMappedStatement(ClientMapper.class.getName() + "." + method).getBoundSql(parameters)
            .getSql().replaceAll("\\s+", " ").trim();
    }
    @Test void allRealResultMapsMaterializeEveryFieldWithoutDatabase() throws Exception {
        var config = config();
        for (String id : List.of("client", "assignment", "change")) {
            var mapping = config.getResultMap(ClientMapper.class.getName() + "." + id);
            assertThat(mapping.getAutoMapping()).isFalse();
            Object row = mapping.getType().getDeclaredConstructor().newInstance();
            var meta = config.newMetaObject(row);
            var labels = new LinkedHashMap<String, Object>();
            for (var field : mapping.getResultMappings()) {
                Class<?> type = field.getJavaType();
                Object value = type == long.class ? 7L : type == Instant.class ? Instant.parse("2030-01-01T00:00:00.123456Z") :
                    type == LocalDate.class ? LocalDate.of(2030, 1, 2) : "value:" + field.getProperty();
                labels.put(field.getColumn(), value);
            }
            assertThat(labels).hasSize(mapping.getType().getFields().length);
            for (var field : mapping.getResultMappings()) {
                assertThat(field.getJavaType()).isEqualTo(meta.getSetterType(field.getProperty()));
                assertThat(field.getTypeHandler()).isNotNull();
                meta.setValue(field.getProperty(), labels.get(field.getColumn()));
                assertThat(meta.getValue(field.getProperty())).isEqualTo(labels.get(field.getColumn()));
            }
        }
    }
    @Test void optionalPredicatesOnlyBindPresentFiltersAndBothCategoriesExpand() throws Exception {
        var config = config(); var params = new HashMap<String, Object>(); params.put("today", LocalDate.of(2030, 1, 1));
        params.put("filter", new Filter(null, "Y", null));
        String empty = sql(config, "selectClients", params);
        assertThat(empty).doesNotContain("IS NULL OR", "strpos", "LIMIT", "OFFSET");
        params.put("filter", new Filter("CUST", "Y", "거래처"));
        assertThat(sql(config, "selectClients", params)).contains("c.clnt_type_cd IN ('CUST','BOTH')", "strpos");
        params.put("filter", new Filter("VNDR", "N", null));
        assertThat(sql(config, "selectClients", params)).contains("c.clnt_type_cd IN ('VNDR','BOTH')");
    }
    @Test void aggregatesDeduplicatePeopleAndProjectsButAssignmentsPreserveLines() throws Exception {
        var config = config(); var params = Map.of("today", LocalDate.of(2030, 1, 1), "clientId", "client");
        String detail = sql(config, "selectClient", params);
        assertThat(detail).contains("count(DISTINCT p.proj_id)", "count(DISTINCT d.dev_id)", "a.asgn_stat_cd = 'ACT'",
            "a.input_bgn_dt <= ?", "a.input_end_dt >= ?", "p.proj_stat_cd = 'ING'", "d.blng_clnt_id = c.clnt_id");
        assertThat(sql(config, "selectAssignments", params)).doesNotContain("DISTINCT", "GROUP BY").contains("a.asgn_id");
    }
    @Test void nullableVersionIsBoundAndNeverUsedAsBypassAndSelectableClientsAreActive() throws Exception {
        var config = config(); var input = new Input("CUST", "거래처", null, null, null, null, null, "a@b.test", null, null, null);
        String update = sql(config, "updateClient", new Write("client", input, null, "admin", null));
        assertThat(update).contains("IS NOT DISTINCT FROM ?", "clock_timestamp()").doesNotContain("biz_reg_no=");
        assertThat(sql(config, "updateUseStatus", new StateWrite(new Version("client", null), "N", "admin")))
            .contains("IS NOT DISTINCT FROM ?");
        assertThat(sql(config, "selectSelectableClientIds", null)).contains("c.use_yn='Y'");
    }
}
