package com.company.common.projectcostanalyses;

import com.company.common.calculation.mapper.*;
import com.company.common.projectcostanalyses.mapper.ProjectCostAnalysisRows.*;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

final class ProjectCostAnalysisTestSources {
    private ProjectCostAnalysisTestSources() {}
    static BigDecimal n(String value) { return new BigDecimal(value); }
    static void definitions(CalculationRuleMapper mapper) throws Exception {
        var seed = new ClassPathResource("db/migration/V8__calculation_rule_seed.sql")
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for (String id : List.of("00","02","09","10","11","13","15","16","18","19","20","21","22","23",
            "24","25","26","27","28","29","31","53","55","56","57","75","87","88","90","91","94")) {
            var matcher = Pattern.compile("\\('식-" + id + "', (?:NULL|'[^']*'), '[^']*', '((?:[^']++|'')*+)', '((?:[^']++|'')*+)',").matcher(seed);
            assertThat(matcher.find()).as("DB formula %s", id).isTrue();
            var row = new CalculationRuleRow();
            row.calcId = "식-" + id;
            row.formula = matcher.group(1).replace("''", "'");
            row.details = matcher.group(2).replace("''", "'");
            when(mapper.selectDefinition(row.calcId)).thenReturn(row);
        }
        // 운영 기준값: 목표 마진율 20%, 원천세 3.3%, 단가 대비 원가 기준 80%(식-91)
        for (String key : List.of("TGT_MRGN_RT", "WTHLD_RT", "COST_OVER_UPRC_RT")) {
            var standard = new CalculationStandardRow();
            standard.standardCode = key; standard.effectiveMonth = "202501";
            standard.value = n(key.equals("TGT_MRGN_RT") ? "20" : key.equals("WTHLD_RT") ? "3.3" : "80");
            when(mapper.selectEffectiveStandard(eq(key), anyString())).thenReturn(standard);
        }
    }
    static Project project(String id, String type) {
        return new Project(id, "프로젝트 " + id, type, type, "ING", LocalDate.of(2025,12,1),
            LocalDate.of(2026,12,31), n("1250"), n("1250"), n("1000"), n("10"), n("20"), n("12"), null);
    }
    static Snapshot cost(String project, String month, String revenue, String labor) {
        return new Snapshot(month, "A", project, project + "-paid", n("1"), n("0"), n(revenue), n(labor), n("10"), n("20"), "MO");
    }
    static Input input(String project, String month, String developer, String id, String type) {
        return new Input(month, developer, project, id, "개발", type, n("0.5"), n("500"), n("1000"));
    }
    static Person person(String id) {
        return new Person(id, "개발자 " + id, null, "자사", null, "사원", "CT", "계약직",
            LocalDate.of(2025,1,1), null, "DEV", "개발", null, "", "PART", "DRCT", id, "파트");
    }
    static List<Code> codes() {
        return List.of(new Code("CTRT_TYPE","TK","턴키",null), new Code("CTRT_TYPE","MO","월단위",null),
            new Code("CTRT_TYPE","DP","파견",null), new Code("CTRT_TYPE","IN","내부",null),
            new Code("ALWNC","ALLOW_TEAM","팀장",n("200")), new Code("ALWNC","ALLOW_GROUP","그룹장",n("400")),
            new Code("ALWNC","SEV_RATE","퇴직",n("8.33")),
            new Code("INPUT_TYPE","NEW","신규",null), new Code("INPUT_TYPE","FREE","무상지원",null));
    }
}
