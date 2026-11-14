package com.company.common.parts.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class PartProjectionTest {
    private Configuration configuration() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/PartMapper.xml";
        try (var stream = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
        }
        return config;
    }
    private Object materialize(Configuration config, String statement, Map<String, Object> labels) throws Exception {
        var mapping = config.getMappedStatement(PartMapper.class.getName() + "." + statement).getResultMaps().get(0);
        assertThat(mapping.getAutoMapping()).isFalse();
        assertThat(mapping.getResultMappings()).extracting(field -> field.getColumn())
            .containsExactlyElementsOf(labels.keySet());
        Object row = mapping.getType().getDeclaredConstructor().newInstance();
        var meta = config.newMetaObject(row);
        for (var field : mapping.getResultMappings()) {
            assertThat(field.getJavaType()).isEqualTo(meta.getSetterType(field.getProperty()));
            assertThat(field.getTypeHandler()).isNotNull();
            meta.setValue(field.getProperty(), labels.get(field.getColumn()));
        }
        return row;
    }
    @Test void masterPeriodLeaderAndHistoryUseExplicitMappingsWithNullableValues() throws Exception {
        var config = configuration();
        Instant time = Instant.parse("2026-09-01T00:00:00.123456Z");
        var masterLabels = new LinkedHashMap<String, Object>();
        masterLabels.put("part_id", "owned-part"); masterLabels.put("part_nm", "매핑 파트");
        masterLabels.put("use_yn", "N"); masterLabels.put("mod_dttm", time);
        masterLabels.put("person_count", 7L); masterLabels.put("project_count", 9L);
        var master = (PartRows.Master) materialize(config, "selectParts", masterLabels);
        assertThat(master.partId).isEqualTo("owned-part"); assertThat(master.partName).isEqualTo("매핑 파트");
        assertThat(master.useYn).isEqualTo("N"); assertThat(master.modifiedAt).isEqualTo(time);
        assertThat(master.personCount).isEqualTo(7L); assertThat(master.projectCount).isEqualTo(9L);
        var periodLabels = new LinkedHashMap<String, Object>();
        periodLabels.put("part_id", "owned-part"); periodLabels.put("aply_bgn_ym", "202610");
        periodLabels.put("aply_end_ym", null); periodLabels.put("part_ldr_user_id", "owned-leader");
        periodLabels.put("leader_name", "파트장 표시명"); periodLabels.put("prft_dstrb_rt", new BigDecimal("37.25"));
        periodLabels.put("sales_exps_amt", new BigDecimal("999999999999999"));
        var period = (PartRows.Period) materialize(config, "selectPeriods", periodLabels);
        assertThat(period.partId).isEqualTo("owned-part"); assertThat(period.month).isEqualTo("202610");
        assertThat(period.endMonth).isNull(); assertThat(period.leaderUserId).isEqualTo("owned-leader");
        assertThat(period.leaderName).isEqualTo("파트장 표시명");
        assertThat(period.distributionRate).isEqualTo(new BigDecimal("37.25"));
        assertThat(period.salesExpense).isEqualTo(new BigDecimal("999999999999999"));
        var leaderLabels = new LinkedHashMap<String, Object>();
        leaderLabels.put("user_id", "owned-leader"); leaderLabels.put("user_nm", "선택 파트장");
        var leader = (PartRows.Leader) materialize(config, "selectLeaders", leaderLabels);
        assertThat(leader.userId).isEqualTo("owned-leader"); assertThat(leader.userName).isEqualTo("선택 파트장");
        var historyLabels = new LinkedHashMap<String, Object>();
        historyLabels.put("reg_dttm", time); historyLabels.put("chg_item_nm", "배분율");
        historyLabels.put("bfr_val", null); historyLabels.put("aft_val", "37.25"); historyLabels.put("changed_by", "admin");
        var history = (com.company.common.users.mapper.UserRows.Change) materialize(config, "selectHistory", historyLabels);
        assertThat(history.changedAt).isEqualTo(time); assertThat(history.item).isEqualTo("배분율");
        assertThat(history.before).isNull(); assertThat(history.after).isEqualTo("37.25");
        assertThat(history.changedBy).isEqualTo("admin");
    }
    @Test void nullableVersionIsBoundEvenWhenNullAndOptionalNameExclusionIsDynamic() throws Exception {
        var config = configuration();
        var parameters = new java.util.HashMap<String, Object>();
        parameters.put("id", "owned"); parameters.put("token", null);
        String sql = config.getMappedStatement(PartMapper.class.getName() + ".touch")
            .getBoundSql(parameters).getSql();
        assertThat(sql).contains("IS NOT DISTINCT FROM", "clock_timestamp()");
        parameters.put("name", "owned-name"); parameters.put("exceptId", null);
        assertThat(config.getMappedStatement(PartMapper.class.getName() + ".nameExists")
            .getBoundSql(parameters).getSql()).doesNotContain("part_id !=");
    }
}
