package com.company.common.manpowerforecasts;

import com.company.common.calculation.dto.CalculationRuleContracts.*;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.salespipeline.service.SalesPipelineCalculationRules;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import static org.mockito.Mockito.*;

final class ManpowerForecastTestData {
    private ManpowerForecastTestData() {}

    static void definitions(CalculationRuleService metadata, SalesPipelineCalculationRules calculations) throws Exception {
        String seed = new ClassPathResource("db/migration/V8__calculation_rule_seed.sql").getContentAsString(StandardCharsets.UTF_8);
        when(metadata.definition(anyString())).thenAnswer(call -> definition(seed, call.getArgument(0)));
        when(metadata.effectiveStandard(anyString(), any())).thenAnswer(call -> new Standard(call.getArgument(0),
            YearMonth.of(2026, 1), new BigDecimal(switch ((String) call.getArgument(0)) {
                case "SHORT_PERSON" -> "0.4";
                case "SURPLUS_PERSON" -> "1";
                case "EXT_PRBL_H" -> "80";
                case "EXT_PRBL_NULL" -> "50";
                default -> throw new AssertionError(call.getArgument(0).toString());
            })));
        when(calculations.definitions()).thenReturn(List.of("식-60", "식-61", "식-92", "식-93").stream()
            .map(id -> definition(seed, id)).toList());
        when(calculations.standard("SHORT_PERSON")).thenReturn(new BigDecimal("0.4"));
        when(calculations.standard("HIRE_LEAD_MON")).thenReturn(new BigDecimal("2"));
    }

    static Definition definition(String seed, String id) {
        var match = Pattern.compile("\\('" + Pattern.quote(id) + "', (?:NULL|'[^']*'), '[^']*', '((?:[^']|'')*)', '((?:[^']|'')*)'", Pattern.DOTALL).matcher(seed);
        if (!match.find()) throw new AssertionError("Missing definition " + id);
        return new Definition(id, match.group(1).replace("''", "'"), match.group(2).replace("''", "'"));
    }
}
