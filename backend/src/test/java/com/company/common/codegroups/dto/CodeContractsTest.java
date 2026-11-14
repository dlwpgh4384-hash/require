package com.company.common.dto;

import com.company.common.mapper.CodeRows;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class CodeContractsTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void allowanceSerializesExactDecimalAndExcludesStorageAndUnrelatedAdditionalFields() throws Exception {
        var row = new CodeRows.Code(); row.groupId="ALWNC"; row.code="TEST"; row.codeName="시험";
        row.useYn="Y"; row.delYn="N"; row.sortOrd=5; row.codeVal=new BigDecimal("12345678901.1234"); row.unitNm="원";
        row.addVal1="내부 값";
        String value=json.writeValueAsString(row.value());
        assertThat(value).contains("12345678901.1234").doesNotContain("delYn","groupId","addVal","relatedJobs","effectiveMonth");
        assertThat(json.readTree(value).path("code").textValue()).isEqualTo("TEST");
    }
    @ParameterizedTest @ValueSource(strings={"JOB","SKILL","PROJ_STEP","BANK","RSM_LABEL","RSM_JOB_KW"})
    void eachGroupExposesOnlyItsAdditionalValues(String group) throws Exception {
        var row = new CodeRows.Code(); row.groupId=group; row.code="TEST"; row.codeName="시험";
        row.useYn="Y"; row.sortOrd=1; row.addVal1=group.equals("PROJ_STEP")?"12.1250":"VALUE";
        row.addVal2=group.equals("JOB")?"1234.1250":"VALUE";
        var value=json.readTree(json.writeValueAsString(row.value()));
        assertThat(value.has("codeVal")).isFalse();
        String field=switch(group) { case "JOB"->"relatedJobs"; case "SKILL"->"requiredJobs";
            case "PROJ_STEP"->"weight"; case "BANK"->"bankCode"; case "RSM_LABEL"->"targetField"; default->"job"; };
        assertThat(value.has(field)).isTrue();
        assertThat(value.has("addVal1")).isFalse();
    }
}
