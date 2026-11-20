package com.company.common.personalcostprofits;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.mapper.CalculationRuleRow;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import com.company.common.roles.dto.RoleContracts.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

final class PersonalCostProfitTestSources {
    private PersonalCostProfitTestSources() {}
    static BigDecimal n(String value) { return new BigDecimal(value); }
    static void definitions(CalculationRuleMapper mapper) throws Exception {
        var seed = new ClassPathResource("db/migration/V8__calculation_rule_seed.sql")
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for (String id : List.of("00","01","02","07","08","09","10","11","12","13","14","15","16","17","19","20","21","23","24","25","27","30","34","35","37","38","39","40","53","55","57","66","95","96")) {
            // Consume SQL string runs without recursive backtracking over long seed details.
            var matcher = Pattern.compile("\\('식-" + id + "', (?:NULL|'[^']*'), '[^']*', '((?:[^']++|'')*+)', '((?:[^']++|'')*+)',").matcher(seed);
            assertThat(matcher.find()).as("DB formula %s", id).isTrue();
            var row = new CalculationRuleRow();
            row.calcId = "식-" + id;
            row.formula = matcher.group(1).replace("''", "'");
            row.details = matcher.group(2).replace("''", "'");
            when(mapper.selectDefinition(row.calcId)).thenReturn(row);
        }

    }
    static Person person(String id) {
        return new Person(id, "김" + id, "C1", "소속", null, "사원", "CT", "계약직",
            LocalDate.of(2026,1,1), null, "BE", "백엔드", "MD", "중급", "PART", "DRCT", "K-" + id, "파트");
    }
    static Pay pay(String id, String month, String salary) {
        return new Pay(month, id, "CT", n(salary), n("0"), n("100"), n("0"), n("10"), n("33"), n("12"), n("945"), n("0"));
    }
    static Cost cost(String assignment, String mm, String revenue, String labor) {
        return new Cost("202601", "A", "P", assignment, "프로젝트", "백엔드", "MO",
            n(mm), n(revenue), n(labor), n("10"), n("20"), "FREE".equals(assignment) ? "FREE" : "NEW");
    }
    static Assignment assignment(String id, String month, String mm) {
        return new Assignment(month, "A", "P", id, "프로젝트", "백엔드", "MO", "PAID",
            n(mm), n("800"), n("10"), n("20"), n("10000"), n("8000"), null);
    }
    static List<Code> codes() {
        return List.of(new Code("ALWNC", "ALLOW_TEAM", "팀장", n("200")),
            new Code("ALWNC", "ALLOW_GROUP", "그룹장", n("400")), new Code("ALWNC", "SEV_RATE", "퇴직", n("8.33")),
            new Code("EMP_TYPE", "CT", "계약직", null), new Code("JOB", "BE", "백엔드", null),
            new Code("affiliationId", "C1", "소속", null), new Code("salesPartId", "PART", "파트", null));
    }
    static Role role(String id, String sensitive) { return new Role(id, id, "ALL", sensitive, "Y", true); }
}
