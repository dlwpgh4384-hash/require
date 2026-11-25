package com.company.common.dashboard;

import com.company.common.dashboard.mapper.DashboardMapper;
import java.time.LocalDate;
import java.util.Map;
import java.util.HashSet;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class DashboardMappingTest {
    @Test void orderedConstructorAndQuotedResultLabelsMapWithoutDatabase() throws Exception {
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(false);
        String path = "mapper/dashboard/DashboardMapper.xml";
        try (var stream = new ClassPathResource(path).getInputStream()) {
            new XMLMapperBuilder(stream, configuration, path, configuration.getSqlFragments()).parse();
        }
        var statement = configuration.getMappedStatement(DashboardMapper.class.getName() + ".utilization");
        var map = statement.getResultMaps().get(0);
        var components = DashboardMapper.Utilization.class.getRecordComponents();
        var mappings = map.getConstructorResultMappings();
        var labels = new HashSet<String>();
        var matcher = Pattern.compile("(?i)AS\\s+\"([a-zA-Z]+)\"").matcher(statement.getBoundSql(Map.of(
            "month", "202611", "first", LocalDate.of(2026, 11, 1), "last", LocalDate.of(2026, 11, 30))).getSql());
        while (matcher.find()) labels.add(matcher.group(1));
        assertThat(mappings).hasSize(components.length);
        for (int i = 0; i < components.length; i++) {
            assertThat(mappings.get(i).getColumn()).isEqualTo(components[i].getName());
            assertThat(mappings.get(i).getJavaType()).isEqualTo(components[i].getType());
            assertThat(labels).contains(components[i].getName());
        }
        var row = (DashboardMapper.Utilization) map.getType().getDeclaredConstructors()[0].newInstance(17, 13);
        assertThat(row.totalPeople()).isEqualTo(17);
        assertThat(row.assignedPeople()).isEqualTo(13);
    }
}
