package com.company.common.profitstructures;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.mapper.CalculationRuleRow;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import com.company.common.profitstructures.mapper.ProfitStructureRows.*;
import com.company.common.profitstructures.service.ProfitStructureDefinitions;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

final class ProfitStructureTestSources {
    private ProfitStructureTestSources() {}
    static BigDecimal n(String value) { return new BigDecimal(value); }
    static void definitions(CalculationRuleMapper mapper) throws Exception {
        var seed = new ClassPathResource("db/migration/V8__calculation_rule_seed.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for (var id : ProfitStructureDefinitions.IDS) {
            var matcher = Pattern.compile("\\('" + id + "', (?:NULL|'[^']*'), '[^']*', '((?:[^']++|'')*+)', '((?:[^']++|'')*+)',").matcher(seed);
            assertThat(matcher.find()).as("DB formula %s", id).isTrue();
            var row = new CalculationRuleRow(); row.calcId = id; row.formula = matcher.group(1).replace("''", "'"); row.details = matcher.group(2).replace("''", "'");
            when(mapper.selectDefinition(id)).thenReturn(row);
        }
    }
    static Snapshot fixed(String month) {
        return new Snapshot(month,n("1000"),n("500"),n("150"),n("50"),n("700"),n("300"),n("100"),n("10"),n("210"),n("20"),n("5"),n("10"),n("215"),"N","Y",Instant.parse("2026-01-31T12:00:00Z"));
    }
    static Snapshot migrated(String month) {
        return new Snapshot(month,n("1000"),null,null,null,n("700"),null,n("100"),null,n("200"),null,null,null,null,"Y","Y",null);
    }
    static ProjectMonth project(String month, String id, String contract, String client) {
        return new ProjectMonth(month,id,contract,contract,client,client,"ACQUIRED","프로젝트 영업 파트",n("10000"),n("8000"),null,n("50"),null);
    }
    static Person person() {
        return new Person("A","김개발",null,"자사",null,"사원","CT","계약직",LocalDate.of(2026,1,1),null,"BE","백엔드","MD","중급","EMPLOYEE_PART","DRCT","001","사람 소속 파트");
    }
    static Pay pay() { return new Pay("202602","A","CT",n("200"),n("0"),n("0"),n("0"),n("0"),n("0"),n("0"),n("200"),n("0")); }
    static Assignment assignment() { return new Assignment("202602","A","P","A1","프로젝트","백엔드","MO","PAID",n("1"),n("1000"),n("10"),n("20"),n("10000"),n("8000"),null); }
    static List<Code> codes() { return List.of(new Code("ALWNC","ALLOW_TEAM","팀장",n("200")),new Code("ALWNC","ALLOW_GROUP","그룹장",n("400")),new Code("ALWNC","SEV_RATE","퇴직",n("8.33"))); }
}
