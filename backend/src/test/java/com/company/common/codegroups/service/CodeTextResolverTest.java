package com.company.common.codegroups.service;

import com.company.common.support.service.CodeFailure;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.codegroups.mapper.CodeTextRow;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CodeTextResolverTest {
    private final CalculationRuleMapper mapper = mock(CalculationRuleMapper.class);
    private final CodeTextResolver resolver = new CodeTextResolver(mapper);

    private CodeTextRow code(String id) {
        var row = new CodeTextRow();
        row.groupId = "EMP_TYPE"; row.code = id; row.codeName = "정규직";
        return row;
    }

    @Test void validCodeWinsEvenWhenAnotherCodesNameMatchesIt() {
        when(mapper.selectNormalText("EMP_TYPE", "FULL")).thenReturn(List.of(code("OTHER"), code("FULL")));
        var value = resolver.resolve("EMP_TYPE", "FULL");
        assertThat(value.code()).isEqualTo("FULL");
        assertThat(value.warning()).isNull();
        verify(mapper, never()).selectMappedText(anyString(), anyString());
    }

    @Test void validNameNeedsNoMappingOrWarning() {
        when(mapper.selectNormalText("EMP_TYPE", "정규직")).thenReturn(List.of(code("FULL")));
        assertThat(resolver.resolve("EMP_TYPE", "정규직").warning()).isNull();
        verify(mapper, never()).selectMappedText(anyString(), anyString());
    }

    @Test void exactMappedTextReturnsOriginalCodeAndConversionWarning() {
        when(mapper.selectNormalText("EMP_TYPE", "정직원")).thenReturn(List.of());
        when(mapper.selectMappedText("EMP_TYPE", "정직원")).thenReturn(code("FULL"));
        var value = resolver.resolve("EMP_TYPE", "정직원");
        assertThat(value.groupId()).isEqualTo("EMP_TYPE");
        assertThat(value.code()).isEqualTo("FULL");
        assertThat(value.warning()).isEqualTo("‘정직원’을 ‘정규직’으로 바꿈");
    }

    @Test void unknownLabelCannotBecomeAnArbitraryCode() {
        when(mapper.selectNormalText("EMP_TYPE", "미등록")).thenReturn(List.of());
        assertThatThrownBy(() -> resolver.resolve("EMP_TYPE", "미등록"))
            .isInstanceOf(CodeFailure.class).hasMessageContaining("미등록")
            .satisfies(error -> assertThat(((CodeFailure) error).getField()).isEqualTo("excelText"));
    }

    @Test void matchingUsesTheInputGroupAndDoesNotTrimOrFoldText() {
        when(mapper.selectNormalText("OTHER", " 정직원 ")).thenReturn(List.of());
        assertThatThrownBy(() -> resolver.resolve("OTHER", " 정직원 ")).isInstanceOf(CodeFailure.class);
        verify(mapper).selectMappedText("OTHER", " 정직원 ");
    }

    @Test void duplicateNamesRequireExplicitCodeRatherThanGuessing() {
        when(mapper.selectNormalText("EMP_TYPE", "정규직")).thenReturn(List.of(code("A"), code("B")));
        assertThatThrownBy(() -> resolver.resolve("EMP_TYPE", "정규직")).isInstanceOf(CodeFailure.class);
        verify(mapper, never()).selectMappedText(anyString(), anyString());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t"})
    void missingLabelFailsBeforeDatabaseRead(String text) {
        assertThatThrownBy(() -> resolver.resolve("EMP_TYPE", text)).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper);
    }

    @Test void oversizedLabelFailsWithoutTruncation() {
        assertThatThrownBy(() -> resolver.resolve("EMP_TYPE", "가".repeat(101))).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"emp", "EMP TYPE", "A123456789012345678901234567890"})
    void invalidGroupFailsBeforeRead(String group) {
        assertThatThrownBy(() -> resolver.resolve(group, "정규직")).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper);
    }
}
