package com.company.common.commonops;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.mapper.CalculationRuleRow;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

final class ManpowerComparisonDefinitions {
    private ManpowerComparisonDefinitions() {}

    static void load(CalculationRuleMapper mapper) throws Exception {
        String seed = new ClassPathResource("db/migration/V8__calculation_rule_seed.sql")
            .getContentAsString(StandardCharsets.UTF_8);
        for (String id : List.of("00", "01", "62", "67")) {
            var tuple = Pattern.compile("\\('식-" + id + "', (?:NULL|'[^']*'), '[^']*', '((?:[^']|'')*)', '((?:[^']|'')*)',")
                .matcher(seed);
            assertThat(tuple.find()).isTrue();
            var row = new CalculationRuleRow();
            row.calcId = "식-" + id;
            row.formula = tuple.group(1).replace("''", "'");
            row.details = tuple.group(2).replace("''", "'");
            when(mapper.selectDefinition(row.calcId)).thenReturn(row);
        }
    }
}
