package com.cost.shell;

import com.cost.shell.menu.MenuRow;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.util.*;
import java.lang.reflect.RecordComponent;
import static org.assertj.core.api.Assertions.assertThat;

class MenuMappingTest {
    @Test void actualConstructorDeclarationAndSelectedLabelsMatchEveryRecordFieldWithoutDatabase() throws Exception {
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(false);
        configuration.setAutoMappingBehavior(AutoMappingBehavior.NONE);
        try (var input = new ClassPathResource("mappers/MenuMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, configuration, "mappers/MenuMapper.xml", configuration.getSqlFragments()).parse();
        }
        var statement = configuration.getMappedStatement("com.cost.shell.menu.MenuMapper.findAll");
        var mappings = statement.getResultMaps().get(0).getConstructorResultMappings();
        var components = MenuRow.class.getRecordComponents();
        assertThat(mappings).hasSize(components.length);
        var labels = new LinkedHashMap<String, Object>();
        labels.put("menuId", "mapping-id");
        labels.put("upperMenuId", "mapping-parent");
        labels.put("menuNm", "매핑 검증");
        labels.put("scrnId", "mapping-ui");
        labels.put("menuLvl", 3);
        labels.put("sortOrd", 73);
        labels.put("pcOnlyYn", "Y");
        labels.put("iconNm", "grid");
        labels.put("useYn", "Y");
        String sql = statement.getBoundSql(null).getSql();
        Object[] arguments = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            assertThat(mappings.get(index).getColumn()).isEqualTo(components[index].getName());
            assertThat(mappings.get(index).getJavaType()).isEqualTo(components[index].getType());
            assertThat(sql).contains("AS \"" + components[index].getName() + "\"");
            arguments[index] = labels.get(mappings.get(index).getColumn());
        }
        var constructor = MenuRow.class.getDeclaredConstructor(Arrays.stream(components)
            .map(RecordComponent::getType).toArray(Class<?>[]::new));
        MenuRow row = constructor.newInstance(arguments);
        for (var component : components) {
            assertThat(component.getAccessor().invoke(row)).isEqualTo(labels.get(component.getName()));
        }
    }
}
