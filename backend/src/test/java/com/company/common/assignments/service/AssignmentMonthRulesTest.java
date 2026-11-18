package com.company.common.assignments.service;

import com.company.common.assignments.mapper.AssignmentRows.HistoryRow;
import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.mapper.CalculationRuleRow;
import com.company.common.developers.service.DeveloperManMonthRules;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.projects.service.ProjectAmountRules;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentMonthRulesTest {
    CalculationRuleService definitions;AssignmentMonthRules rules;Map<String,Definition> source;
    CalculationRuleMapper mapper;
    @BeforeEach void setup() throws Exception {
        mapper=mock(CalculationRuleMapper.class);
        definitions=new CalculationRuleService(mapper,mock(UserPreferencePeriodSourceMapper.class));
        source=new HashMap<>();
        String seed=new ClassPathResource("db/migration/V8__calculation_rule_seed.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // Consume SQL string runs without recursive backtracking over long seed details.
        var pattern=Pattern.compile("\\('([^']+)', (?:NULL|'[^']*'), '[^']*', '((?:[^']++|'')*+)', '((?:[^']++|'')*+)'",Pattern.DOTALL);
        var matcher=pattern.matcher(seed);while(matcher.find())source.put(matcher.group(1),new Definition(matcher.group(1),matcher.group(2).replace("''","'"),matcher.group(3).replace("''","'")));
        when(mapper.selectDefinition(anyString())).thenAnswer(c -> {
            var definition=Objects.requireNonNull(source.get(c.getArgument(0)),"missing seed definition");
            var row=new CalculationRuleRow();
            row.calcId=definition.calcId();
            row.formula=definition.formula();
            row.details=definition.details();
            return row;
        });
        rules=new AssignmentMonthRules(definitions,new DeveloperManMonthRules(definitions));
    }
    @Test void actualSeedSupportsInclusiveCalendarDaysAndFractionalContractMm() {
        YearMonth m=YearMonth.of(2026,11);
        assertThat(rules.mm(BigDecimal.ONE,m.atDay(1),m.atEndOfMonth(),m)).isEqualByComparingTo("1.00");
        assertThat(rules.mm(new BigDecimal("0.6"),m.atDay(1),m.atEndOfMonth(),m)).isEqualByComparingTo("0.60");
        assertThat(rules.mm(new BigDecimal("0.25"),m.atDay(1),m.atEndOfMonth(),m)).isEqualByComparingTo("0.25");
        assertThat(rules.mm(BigDecimal.ONE,LocalDate.of(2026,10,16),LocalDate.of(2026,10,31),YearMonth.of(2026,10))).isEqualByComparingTo("0.52");
        assertThat(rules.mm(new BigDecimal("0.5"),LocalDate.of(2026,10,16),LocalDate.of(2026,10,31),YearMonth.of(2026,10))).isEqualByComparingTo("0.26");
    }
    @Test void leapFebruaryAndEmptyOverlapUseRealCalendar() {var feb=YearMonth.of(2024,2);assertThat(rules.days(feb.atDay(1),feb.atEndOfMonth(),feb)).isEqualTo(29);assertThat(rules.mm(BigDecimal.ONE,feb.plusMonths(1).atDay(1),feb.plusMonths(1).atEndOfMonth(),feb)).isZero();}
    @Test void amountsTruncateEachRowAndFreeSupportIsZero() {assertThat(rules.sales(new BigDecimal("0.52"),new BigDecimal("7000000"),true)).isEqualByComparingTo("3640000");assertThat(rules.sales(new BigDecimal("0.25"),new BigDecimal("123"),true)).isEqualByComparingTo("30");assertThat(rules.sales(BigDecimal.ONE,new BigDecimal("8000000"),false)).isZero();}
    @Test void thresholdIsStrictAndUsesChangedSupportedDatabaseDefinition() {assertThat(rules.over(BigDecimal.ONE)).isFalse();assertThat(rules.over(new BigDecimal("1.01"))).isTrue();var old=source.get("식-95");source.put("식-95",new Definition("식-95",old.formula().replace("1.0","1.2"),old.details()));assertThat(rules.over(new BigDecimal("1.1"))).isFalse();}
    @Test void supportedRoundingDefinitionChangesActuallyAffectArithmetic() {var old=source.get("식-58");source.put("식-58",new Definition("식-58",old.formula().replace("2자리","3자리"),old.details()));assertThat(rules.mm(BigDecimal.ONE,LocalDate.of(2026,10,16),LocalDate.of(2026,10,31),YearMonth.of(2026,10))).isEqualByComparingTo("0.516");}
    @Test void changedRoundingIsReadAgainWithoutReplacingTheDatabaseDefinition() {
        var month=YearMonth.of(2026,10);
        assertThat(rules.mm(BigDecimal.ONE,month.atDay(16),month.atEndOfMonth(),month))
            .isEqualByComparingTo("0.52");
        var old=source.get("식-58");
        var changed=new Definition("식-58",old.formula().replace("2자리","1자리"),old.details());
        source.put("식-58",changed);
        var snapshot=Map.copyOf(source);
        assertThat(rules.mm(BigDecimal.ONE,month.atDay(16),month.atEndOfMonth(),month))
            .isEqualByComparingTo("0.5");
        assertThat(source).isEqualTo(snapshot);
        verify(mapper,times(2)).selectDefinition("식-01");
        verify(mapper,times(2)).selectDefinition("식-58");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={
        "MM = round(계산값 * 2, 소수 2자리). 금액 = 반올림한 MM × 단가",
        "MM = round(계산값, 소수 2자리). 금액 = 반올림한 MM × 단가 * 2",
        "MM = round(계산값, 소수 4자리). 금액 = 반올림한 MM × 단가",
        "MM = round(계산값, 소수 2자리). 금액 = 반올림한 MM × 단가; execute()"
    })
    void unsupportedRoundingDefinitionFailsInsteadOfIgnoringItsChangedOperations(String formula) {
        source.put("식-58",new Definition("식-58",formula,"독립 계산식"));
        var month=YearMonth.of(2026,10);
        assertThatThrownBy(() -> rules.mm(BigDecimal.ONE,month.atDay(16),month.atEndOfMonth(),month))
            .isInstanceOf(com.company.common.support.service.CodeFailure.class)
            .hasMessageContaining("식-58");
    }
    @Test void nullFormulaHasAnExplicitCalculationError() {
        source.put("식-58",new Definition("식-58",null,"독립 계산식"));
        var month=YearMonth.of(2026,10);
        assertThatThrownBy(() -> rules.mm(BigDecimal.ONE,month.atDay(1),month.atEndOfMonth(),month))
            .isInstanceOf(com.company.common.support.service.CodeFailure.class)
            .hasMessageContaining("식-58");
    }
    @Test void nullSalesFormulaHasAnExplicitCalculationError() {
        source.put("식-02",new Definition("식-02",null,"독립 계산식"));
        assertThatThrownBy(() -> rules.sales(BigDecimal.ONE,BigDecimal.TEN,true))
            .isInstanceOf(com.company.common.support.service.CodeFailure.class)
            .hasMessageContaining("식-02");
    }
    /** 계산에 쓰지 않는 식(식-07·10·12·16·59·70, 식-00 원칙)의 글을 DB에서 고쳐도 저장 계산은 막히지 않는다. */
    @Test void changedTextOfFormulasNotUsedForCalculationDoesNotBlockSaving() {
        for(String id:List.of("식-00","식-07","식-10","식-12","식-16","식-59","식-70"))
            source.put(id,new Definition(id,"사람이 고친 설명 "+id,"독립 계산식"));
        var month=YearMonth.of(2026,10);
        assertThat(rules.mm(BigDecimal.ONE,month.atDay(16),month.atEndOfMonth(),month)).isEqualByComparingTo("0.52");
        assertThat(rules.sales(new BigDecimal("0.52"),new BigDecimal("7000000"),true)).isEqualByComparingTo("3640000");
        for(String id:List.of("식-07","식-10","식-12","식-16","식-59","식-70")) verify(mapper,never()).selectDefinition(id);
    }
    @Test void assignmentAndDependentCostDefinitionsAllComeThroughTheExistingService() {
        var snapshot=Map.copyOf(source);
        var month=YearMonth.of(2026,10);
        rules.mm(BigDecimal.ONE,month.atDay(1),month.atEndOfMonth(),month);
        rules.sales(BigDecimal.ONE,new BigDecimal("1234567"),true);
        rules.over(BigDecimal.ONE);
        new ProjectAmountRules(definitions).personnelDirectCost(
            "FT",new BigDecimal("50000"),new BigDecimal("40000"),BigDecimal.ONE);
        for(String id:List.of("식-00","식-01","식-02","식-11","식-58","식-95")) {
            verify(mapper,atLeastOnce()).selectDefinition(id);
        }
        assertThat(source).isEqualTo(snapshot);
    }
    @Test void unsupportedFormulaIsNotExecutedAndProducesCalculationDiagnostic() {source.put("식-01",new Definition("식-01","execute arbitrary sql",""));assertThatThrownBy(() -> rules.mm(BigDecimal.ONE,LocalDate.of(2026,1,1),LocalDate.of(2026,1,31),YearMonth.of(2026,1))).isInstanceOf(com.company.common.support.service.CodeFailure.class).hasMessageContaining("식-01");}
    HistoryRow h(int n,String type,String date,String price,String mm,String corrected) {return new HistoryRow(n,"A-0001",type,LocalDate.parse(date),"이전","이후",null,null,price==null?null:new BigDecimal(price),mm==null?null:new BigDecimal(mm),"N","N",corrected,null,"admin",Instant.EPOCH);}
    @Test void effectiveHistoryKeepsPriorMonthsAndLaterSameMonthWins() {var h=List.of(h(1,"INPUT","2026-10-16","8000000","0.8","N"),h(2,"UPRC","2026-12-01","8500000",null,"N"),h(3,"UPRC","2026-12-01","9000000",null,"N"),h(4,"RATE","2026-11-01",null,"1.00","N"));assertThat(rules.effective(h,"UPRC",YearMonth.of(2026,11),BigDecimal.ZERO)).isEqualByComparingTo("8000000");assertThat(rules.effective(h,"UPRC",YearMonth.of(2026,12),BigDecimal.ZERO)).isEqualByComparingTo("9000000");assertThat(rules.effective(h,"RATE",YearMonth.of(2026,10),BigDecimal.ZERO)).isEqualByComparingTo("0.8");assertThat(rules.effective(h,"RATE",YearMonth.of(2026,11),BigDecimal.ZERO)).isEqualByComparingTo("1.00");}
    @Test void startMonthChangesOverrideMidMonthRegistrationAndLaterSameMonthWins() {
        var history=List.of(
            h(1,"INPUT","2026-10-16","8000000","1","N"),
            h(2,"UPRC","2026-10-01","8500000",null,"N"),
            h(3,"RATE","2026-10-01",null,"0.8","N"),
            h(4,"UPRC","2026-10-01","9000000",null,"N"),
            h(5,"RATE","2026-10-01",null,"0.6","N"),
            h(6,"UPRC","2026-12-01","9500000",null,"N"),
            h(7,"RATE","2026-12-01",null,"0.5","N")
        );
        for(var month:List.of(YearMonth.of(2026,10),YearMonth.of(2026,11))) {
            assertThat(rules.effective(history,"UPRC",month,BigDecimal.ZERO))
                .isEqualByComparingTo("9000000");
            assertThat(rules.effective(history,"RATE",month,BigDecimal.ZERO))
                .isEqualByComparingTo("0.6");
        }
        assertThat(rules.effective(history,"UPRC",YearMonth.of(2026,12),BigDecimal.ZERO))
            .isEqualByComparingTo("9500000");
        assertThat(rules.effective(history,"RATE",YearMonth.of(2026,12),BigDecimal.ZERO))
            .isEqualByComparingTo("0.5");
    }
    @Test void correctedOriginalRetainsNumericEffectAndCorrectionNeverActsAsCommand() {var h=List.of(h(1,"INPUT","2026-10-16","8000000","1","N"),h(2,"UPRC","2026-12-01","8500000",null,"Y"),h(3,"CRCT","2026-12-01","9999999","0.5","N"));assertThat(rules.effective(h,"UPRC",YearMonth.of(2026,12),BigDecimal.ZERO)).isEqualByComparingTo("8500000");}
    @Test void pmNeverUsesArbitraryInputAndPaidMissingDefaultIsRejected() {assertThat(AssignmentPricePolicy.direct(Set.of("PM"))).isFalse();assertThat(AssignmentPricePolicy.registration(Set.of("PM"),"MO","NEW",new BigDecimal("1"),new BigDecimal("8000000"))).isEqualByComparingTo("8000000");assertThatThrownBy(() -> AssignmentPricePolicy.registration(Set.of("PM"),"MO","NEW",new BigDecimal("1"),null)).hasMessageContaining("단가");}
    @Test void financeAndSalesCanEditAndFreeSupportAlwaysStoresZero() {for(String role:List.of("FIN","SALES_LDR")){assertThat(AssignmentPricePolicy.direct(Set.of(role))).isTrue();assertThat(AssignmentPricePolicy.registration(Set.of(role),"MO","NEW",new BigDecimal("8500000"),null)).isEqualByComparingTo("8500000");}assertThat(AssignmentPricePolicy.registration(Set.of("PM"),"MO","FREE",null,null)).isZero();assertThat(AssignmentPricePolicy.registration(Set.of("PM"),"TK","NEW",null,null)).isZero();}
}
