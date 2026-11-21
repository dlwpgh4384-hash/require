package com.company.common.operatingprofitstatements;

import com.company.common.profitstructures.mapper.ProfitStructureRows.Snapshot;
import com.company.common.profitstructures.mapper.ProfitStructureRows.Query;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class OperatingProfitStatementMappingTest {
    @Test void expandedSnapshotUsesExplicitCanonicalConstructorAndQuotedLabelsUnderProductionSettings() throws Exception {
        var config=new Configuration(); config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        // ProfitStructureMapper reuses PersonalCostProfitMapper's constructor result maps.
        for(String path:List.of("mapper/personalcostprofits/PersonalCostProfitMapper.xml",
            "mapper/profitstructures/ProfitStructureMapper.xml")) {
            var resource=new ClassPathResource(path);
            try(var input=resource.getInputStream()) {
                new XMLMapperBuilder(input,config,resource.getPath(),config.getSqlFragments()).parse();
            }
        }
        var statement=config.getMappedStatement("com.company.common.profitstructures.mapper.ProfitStructureMapper.snapshots");
        var mappings=statement.getResultMaps().get(0).getConstructorResultMappings();
        var components=Snapshot.class.getRecordComponents();
        assertThat(mappings).hasSize(components.length);
        var types=new ArrayList<Class<?>>(); var values=new ArrayList<Object>();
        String sql=statement.getBoundSql(new Query(2026,2)).getSql();
        for(int i=0;i<components.length;i++) {
            var mapping=mappings.get(i); var component=components[i];
            assertThat(mapping.getColumn()).isEqualTo(component.getName());
            assertThat(mapping.getJavaType()).isEqualTo(component.getType());
            assertThat(sql).contains("\""+component.getName()+"\"");
            types.add(mapping.getJavaType());
            values.add(component.getType()==BigDecimal.class ? BigDecimal.valueOf(i+1)
                : component.getType()==Instant.class ? Instant.parse("2026-01-01T01:02:03Z") : component.getName());
        }
        var row=config.getObjectFactory().create(Snapshot.class,types,values);
        for(int i=0;i<components.length;i++) assertThat(components[i].getAccessor().invoke(row)).isEqualTo(values.get(i));
    }
}
