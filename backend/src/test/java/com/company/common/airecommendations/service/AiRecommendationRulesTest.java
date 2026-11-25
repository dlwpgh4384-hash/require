package com.company.common.airecommendations.service;

import com.company.common.airecommendations.mapper.AiRecommendationRows.*;
import com.company.common.codegroups.dto.CodeContracts.Item;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AiRecommendationRulesTest {
    Item code(String code, String name) { var item = new Item(); item.code=code; item.codeName=name; return item; }
    Map<String,BigDecimal> maxima() {
        var values = new LinkedHashMap<String,BigDecimal>();
        var maxima = List.of(25,20,15,10,5,5,10,10);
        for (int i=0;i<8;i++) values.put(AiRecommendationRules.DIMENSIONS.get(i),BigDecimal.valueOf(maxima.get(i)));
        return values;
    }
    @Test void canonicalExampleTotals87AndKeepsDetailedSkillRounding() {
        Item job=code("BE","백엔드"); job.relatedJobs="FE";
        var skills=new ArrayList<Item>();
        for (String name:List.of("Java","Spring","SQL","Docker","Kotlin")) { var s=code(name,name); s.requiredJobs="BE"; skills.add(s); }
        var context=new AiRecommendationRules.Context(new Target("project","p","프로젝트","202612","202712","ING","MO","c","IT","seoul-dong","Java","p","가나은행"),
            "BE","SR",YearMonth.of(2026,12),List.of(job),List.of(code("JR","초급"),code("MD","중급"),code("SR","고급")),skills,
            parents(),maxima());
        var candidate=new AiRecommendationRules.Candidate(new Person("d","인력","M002","BE",null,"백엔드","MD","중급","FT","정규","seoul"),
            new Evidence(4,3,0,true,true,LocalDate.of(2026,11,30),false),List.of("Java","Spring","SQL"),new BigDecimal("700"),new BigDecimal("1000"));
        var result=AiRecommendationRules.score(context,candidate);
        assertThat(result.get("skill").score()).isEqualByComparingTo("14");
        assertThat(result.get("grade").score()).isEqualByComparingTo("5");
        assertThat(result.values().stream().map(s->s.score()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("87");
        // 시안 .why 문장: 주 직무 → 같은 고객사 → 철수 후 바로 투입 → 보유 스킬, 「. 」로 잇고 마침표.
        assertThat(AiRecommendationRules.reason(context,candidate)).isEqualTo(
            "요청 직무와 주 직무가 같습니다. 가나은행 프로젝트 경험이 있습니다. 2026-11-30 철수 후 바로 투입할 수 있습니다. 보유 스킬: Java, Spring, SQL.");
    }
    @Test void reasonSentenceCoversWaitingAndBusyWithoutOptionalParts() {
        var context=new AiRecommendationRules.Context(new Target("project","p","프로젝트","202612","202712","ING","MO",null,null,null,null,null,null),
            "BE","SR",YearMonth.of(2026,12),List.of(),List.of(),List.of(),parents(),maxima());
        var sub=new Person("d","인력","M002","FE","BE","프론트엔드","MD","중급","FT","정규",null);
        assertThat(AiRecommendationRules.reason(context,new AiRecommendationRules.Candidate(sub,
            new Evidence(0,0,0,false,false,null,false),List.of(),null,null))).isEqualTo("지금 대기 중입니다.");
        assertThat(AiRecommendationRules.reason(context,new AiRecommendationRules.Candidate(sub,
            new Evidence(1,0,0,true,false,LocalDate.of(2026,12,15),false),List.of("React"),null,null)))
            .isEqualTo("시작월에 다른 프로젝트에 투입 중입니다. 보유 스킬: React.");
    }
    Map<String,String> parents() { var p=new HashMap<String,String>(); p.put("seoul",null); p.put("seoul-dong","seoul"); p.put("busan",null); return p; }
    @Test void locationUsesOnlyAreaAncestry() {
        assertThat(AiRecommendationRules.workplace(null,"seoul",parents())).isEqualTo(5);
        assertThat(AiRecommendationRules.workplace("seoul-dong","seoul",parents())).isEqualTo(10);
        assertThat(AiRecommendationRules.workplace("seoul","busan",parents())).isEqualTo(6);
        assertThat(AiRecommendationRules.workplace("seoul",null,parents())).isEqualTo(6);
        assertThat(AiRecommendationRules.workplace("unknown","unknown",parents())).isEqualTo(6);
    }
    @Test void performanceMatchesBothFormulaExamplesAndNoHistoryDefault() {
        assertThat(AiRecommendationRules.performance(new Evidence(4,3,0,false,false,null,false))).isEqualTo(4);
        assertThat(AiRecommendationRules.performance(new Evidence(4,3,1,false,false,null,false))).isEqualTo(2);
        assertThat(AiRecommendationRules.performance(new Evidence(0,0,0,false,false,null,false))).isEqualTo(3);
    }
    @Test void costUsesRealEvidenceOrThreeForMissingEvidence() {
        assertThat(AiRecommendationRules.cost(new BigDecimal("700"),new BigDecimal("1000"))).isEqualTo(4);
        assertThat(AiRecommendationRules.cost(null,new BigDecimal("1000"))).isEqualTo(3);
        assertThat(AiRecommendationRules.cost(BigDecimal.TEN,null)).isEqualTo(3);
    }
    @Test void skillMentionsAreCaseInsensitiveAndTokenBounded() {
        assertThat(AiRecommendationRules.mentions("JAVA / Spring","Java")).isTrue();
        assertThat(AiRecommendationRules.mentions("JavaScript","Java")).isFalse();
        assertThat(AiRecommendationRules.linked("BE,FE","BE")).isTrue();
        assertThat(AiRecommendationRules.linked("BE2","BE")).isFalse();
    }
    @Test void actualCalendarMonthValidationRejectsImpossibleMonth() {
        assertThatThrownBy(()->CommonAiRecommendationService.month("2026-13")).hasMessage("올바른 시작월을 입력해 주세요.");
        assertThatThrownBy(()->CommonAiRecommendationService.month("0000-01")).hasMessage("올바른 시작월을 입력해 주세요.");
    }
    @Test void emptyRequiredSkillSetDoesNotManufactureFullMatchAndNullGradeGetsTwo() {
        var c=new AiRecommendationRules.Context(new Target("project","p","프로젝트","202612","202712","ING","MO",null,null,null,"Java",null,null),
            "BE","SR",YearMonth.of(2026,12),List.of(code("BE","백엔드")),List.of(code("SR","고급")),List.of(),Map.of(),maxima());
        var candidate=new AiRecommendationRules.Candidate(new Person("d","인력","D","BE",null,"백엔드",null,"–","FT","정규",null),new Evidence(0,0,0,false,false,null,false),List.of("Java"),null,null);
        var score=AiRecommendationRules.score(c,candidate);
        assertThat(score.get("skill").score()).isEqualByComparingTo("4");
        assertThat(score.get("grade").score()).isEqualByComparingTo("2");
        var changed=new LinkedHashMap<>(maxima()); changed.put("job",new BigDecimal("30")); changed.put("skill",new BigDecimal("15"));
        var scaled=AiRecommendationRules.score(new AiRecommendationRules.Context(c.target(),c.jobCode(),c.gradeCode(),c.month(),c.jobs(),c.grades(),c.skills(),c.parents(),changed),candidate);
        assertThat(scaled.get("job").score()).isEqualByComparingTo("30");
        assertThat(scaled.get("skill").score()).isEqualByComparingTo("3");
    }
}
