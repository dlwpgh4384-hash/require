package com.company.common.codemaps.dto;

import com.company.common.support.service.CodeFailure;
import com.company.common.codemaps.service.CodeMapRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class CodeMapContractsTest {
    final ObjectMapper json = new ObjectMapper();

    @Test void exactTextIsPreservedAndInternalMapIdNeverAppears() throws Exception {
        var value = new CodeMapContracts.Item(" 정직원 ", "EMP_TYPE", "FULL");
        assertThat(CodeMapRules.parseItem(json.valueToTree(value))).isEqualTo(value);
        assertThat(json.readTree(json.writeValueAsString(value)).size()).isEqualTo(3);
    }
    @ParameterizedTest @ValueSource(strings = {"excelText", "groupId", "code"})
    void numericFieldsAreNotCoercedToString(String field) {
        var input = json.createObjectNode().put("excelText", "표기").put("groupId", "GROUP").put("code", "CODE");
        input.put(field, 123);
        assertThatThrownBy(() -> CodeMapRules.parseItem(input)).isInstanceOf(CodeFailure.class)
            .satisfies(error -> assertThat(((CodeFailure) error).getField()).isEqualTo(field));
    }
    @Test void maximumSqlCodePointLengthsAreAcceptedWithoutTruncation() {
        var value = new CodeMapContracts.Item("😀".repeat(100), "G".repeat(30), "C".repeat(20));
        assertThat(CodeMapRules.validate(value)).isSameAs(value);
    }
    @ParameterizedTest @ValueSource(strings = {"null", "[]", "123", "\"text\""})
    void nonObjectRequestsFail(String body) throws Exception {
        var node = json.readTree(body);
        assertThatThrownBy(() -> CodeMapRules.parseItem(node)).isInstanceOf(CodeFailure.class);
    }
}
