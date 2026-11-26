package com.company.common.notifications;

import com.company.common.notifications.mapper.NotificationMapper;
import com.company.common.notifications.mapper.NotificationRows.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class NotificationProjectionTest {
    private Configuration configuration() throws Exception {
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        try (var input = new ClassPathResource("mapper/notifications/NotificationMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, config, "mapper/notifications/NotificationMapper.xml", config.getSqlFragments()).parse();
        }
        return config;
    }
    private void materialize(Configuration config, String name, Object row, Map<String, Object> labels) {
        var map = config.getResultMap(NotificationMapper.class.getName() + "." + name);
        assertThat(map.getAutoMapping()).isFalse();
        assertThat(map.getResultMappings()).extracting(mapping -> mapping.getColumn())
            .containsExactlyInAnyOrderElementsOf(labels.keySet());
        var meta = config.newMetaObject(row);
        for (var mapping : map.getResultMappings()) {
            assertThat(mapping.getJavaType()).isEqualTo(meta.getSetterType(mapping.getProperty()));
            assertThat(mapping.getTypeHandler()).isNotNull();
            meta.setValue(mapping.getProperty(), labels.get(mapping.getColumn()));
            assertThat(meta.getValue(mapping.getProperty())).isEqualTo(labels.get(mapping.getColumn()));
        }
    }
    @Test void notificationMaterializesEveryPhysicalColumnWithoutDatabase() throws Exception {
        var config = configuration();
        var row = new Notification();
        var labels = new LinkedHashMap<String, Object>();
        labels.put("noti_id", 782L); labels.put("rcvr_user_id", "reader");
        labels.put("noti_type_cd", "WTHDR"); labels.put("noti_cn", "철수 예정 알림");
        labels.put("link_scrn_id", "UI-05"); labels.put("link_key", "assignment-owned");
        labels.put("read_yn", "Y"); labels.put("noti_dttm", Instant.parse("2026-11-25T01:00:00Z"));
        labels.put("read_dttm", Instant.parse("2026-11-26T01:00:00Z"));
        materialize(config, "notification", row, labels);
        assertThat(row.notificationId).isEqualTo(782L);
        assertThat(row.readAt).isAfter(row.notifiedAt);
        String query = config.getMappedStatement(NotificationMapper.class.getName() + ".selectNotifications")
            .getBoundSql(new Window("reader", NotificationTestData.NOW)).getSql();
        for (String label : labels.keySet()) assertThat(query).contains("n." + label);
    }
    @Test void accessBooleansUseExplicitAliasesWithoutDatabase() throws Exception {
        var config = configuration();
        var row = new Access();
        materialize(config, "access", row, Map.of("read_allowed", true, "in_scope", true,
            "target_exists", true, "pc_only", true));
        assertThat(row.readAllowed && row.inScope && row.targetExists && row.pcOnly).isTrue();
        var notification = new Notification(); notification.type = "DUN";
        notification.screenId = "UI-13"; notification.targetKey = "owned-bill";
        String query = config.getMappedStatement(NotificationMapper.class.getName() + ".selectAccess")
            .getBoundSql(Map.of("actor", "reader", "notification", notification)).getSql();
        assertThat(query).contains("AS read_allowed", "AS in_scope", "AS target_exists", "AS pc_only",
            "r.use_yn='Y'", "a.inq_yn='Y'", "r.data_scope_cd='ALL'", "r.data_scope_cd='PART'", "r.data_scope_cd='PROJ'");
    }
}
