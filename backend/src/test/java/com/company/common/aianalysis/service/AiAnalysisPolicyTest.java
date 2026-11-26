package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.aianalysis.mapper.*;
import com.company.common.roles.mapper.*;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiAnalysisPolicyTest {
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final CurrentBusinessActor actor = mock(CurrentBusinessActor.class);
    final RoleMapper roles = mock(RoleMapper.class);
    final AiAnalysisProjection projection = new AiAnalysisProjection(roles, actor);
    /** 적자 인력 표 모양(시안): 이름·프로젝트 + 민감 표시된 단가·원가·영업이익. */
    Answer answer() {
        var table = new Table(null, List.of(Column.text("name", "이름(관리번호)"), Column.text("projectName", "프로젝트"),
            Column.money("unitPrice", "단가").hidden(), Column.money("totalCost", "원가").hidden(), Column.money("operatingProfit", "영업이익").hidden()),
            List.of(List.of("개발자이름(DEV-19)", "가온", new BigDecimal("500"), new BigDecimal("700"), new BigDecimal("-200"))),
            List.of(Arrays.asList(null, null, null, null, "bad")), List.of("합계", "", new BigDecimal("500"), new BigDecimal("700"), new BigDecimal("-200")));
        return new Answer("17", "OK", "2026-10 적자 인력은 **1명**입니다.", false, false, null, List.of(table), null,
            List.of("가정 한 줄"), "개인 원가·수익표 · 2026-10", "2026-10", List.of(), true, List.of());
    }
    void sensitive(boolean visible) {
        when(actor.roles()).thenReturn(Set.of("ADMIN")); var role = new RoleRows.Role(); role.roleId = "ADMIN"; role.useYn = "Y";
        role.snstvViewYn = visible ? "Y" : "N"; when(roles.selectRole("ADMIN")).thenReturn(role);
    }
    @Test void onePolicyProjectionIsIdenticalForResponseSnapshotExcelAndExternalBoundary() throws Exception {
        sensitive(false);
        var projected = projection.apply(answer());
        assertThat(projected.answerStatus()).isEqualTo("OK");
        var table = projected.tables().get(0);
        assertThat(table.columns()).extracting(Column::key).containsExactly("name", "projectName");
        assertThat(table.rows().get(0)).containsExactly("개발자이름(DEV-19)", "가온");
        assertThat(table.tones().get(0)).hasSize(2); assertThat(table.totalRow()).containsExactly("합계", "");
        assertThat(projected.assumptions().get(0)).isEqualTo("개인별 원가를 볼 권한이 없어 단가·원가·영업이익은 뺐습니다.");
        var serialized = json.writeValueAsString(projected); assertThat(serialized).doesNotContain("unitPrice", "totalCost", "operatingProfit", "-200", "700");
        assertThat(projection.apply(json.readValue(serialized, Answer.class))).isEqualTo(projected);
        var mapper = mock(AiAnalysisMapper.class); when(mapper.identities()).thenReturn(Arrays.asList(new AiAnalysisRows.Identity("개발자이름", "DEV-19"),
            new AiAnalysisRows.Identity("담당자이름", ""), new AiAnalysisRows.Identity(null, "X"), new AiAnalysisRows.Identity("빈번호", null), null));
        var external = mock(AiAnalysisExternalGateway.class); when(external.connected()).thenReturn(true);
        var pseudonyms = new AiAnalysisPseudonymizer(mapper, json);
        pseudonyms.send(external, "개발자이름 담당자이름 빈번호 매출", projected);
        var question = org.mockito.ArgumentCaptor.forClass(String.class); var result = org.mockito.ArgumentCaptor.forClass(Answer.class); verify(external).accept(question.capture(), result.capture());
        assertThat(question.getValue()).contains("DEV-19").doesNotContain("개발자이름", "담당자이름", "빈번호");
        assertThat(json.writeValueAsString(result.getValue())).contains("DEV-19").doesNotContain("개발자이름", "unitPrice", "operatingProfit");
    }
    @Test void pseudonymizerToleratesMissingIdentityRowsAndNullQuestion() {
        var mapper = mock(AiAnalysisMapper.class); when(mapper.identities()).thenReturn(null);
        var external = mock(AiAnalysisExternalGateway.class); when(external.connected()).thenReturn(true);
        new AiAnalysisPseudonymizer(mapper, json).send(external, null, answer());
        verify(external).accept(eq(""), any());
        assertThatCode(() -> new AiAnalysisPseudonymizer(mapper, json).send(external, "q", null)).doesNotThrowAnyException();
    }
    @Test void disconnectedGatewaySkipsReadingNamesAndSendsNothing() {
        var mapper = mock(AiAnalysisMapper.class);
        var disconnected = spy(new DisconnectedAiAnalysisGateway());
        new AiAnalysisPseudonymizer(mapper, json).send(disconnected, "개발자이름 매출", answer());
        verifyNoInteractions(mapper);
        verify(disconnected, never()).accept(any(), any());
    }
    @Test void visibleRoleKeepsEveryColumnWithoutNotice() {
        sensitive(true);
        assertThat(projection.apply(answer())).isEqualTo(answer());
    }
    @Test void unionOfAssignedStoredRolesEnablesSensitivityOnlyWhenAtLeastOnePermitsIt() {
        var no = new RoleRows.Role(); no.roleId = "A"; no.useYn = "Y"; no.snstvViewYn = "N"; var yes = new RoleRows.Role(); yes.roleId = "B"; yes.useYn = "Y"; yes.snstvViewYn = "Y";
        when(actor.roles()).thenReturn(Set.of("A", "B")); when(roles.selectRole("A")).thenReturn(no); when(roles.selectRole("B")).thenReturn(yes);
        assertThat(projection.salaryAllowed()).isTrue(); assertThat(projection.apply(answer()).tables()).isEqualTo(answer().tables());
        yes.useYn = "N"; assertThat(projection.salaryAllowed()).isFalse();
    }
    @Test void downloadReappliesPolicyAfterStoredSensitiveAccessHasBeenRemoved() throws Exception {
        var mapper = mock(AiAnalysisMapper.class); var calendar = mock(BusinessCalendar.class); when(calendar.now()).thenReturn(java.time.Instant.parse("2026-11-25T00:00:00Z"));
        when(actor.userId()).thenReturn("admin"); when(actor.roles()).thenReturn(Set.of()); when(mapper.saved("admin", "17")).thenReturn(json.writeValueAsString(answer()));
        when(mapper.download("admin", 1)).thenReturn(1);
        var archive = new AiAnalysisArchive(mapper, actor, projection, json, new ExcelExportService(), calendar);
        var file = archive.excel("17");
        assertThat(file.fileName()).startsWith("AI 분석 도우미 답_2026-10_");
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(file.bytes()))) {
            var sheet = workbook.getSheetAt(0); assertThat(sheet.getRow(2).getPhysicalNumberOfCells()).isEqualTo(2);
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("이름(관리번호)"); assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("프로젝트");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("가온");
        }
        verify(mapper).download("admin", 1);
    }
    @Test void excelWritesEveryTableAsItsOwnSheetWithNumericCells() throws Exception {
        sensitive(true);
        var mapper = mock(AiAnalysisMapper.class); var calendar = mock(BusinessCalendar.class); when(calendar.now()).thenReturn(java.time.Instant.parse("2026-11-25T00:00:00Z"));
        when(actor.userId()).thenReturn("admin");
        var second = new Table("직무별", List.of(Column.text("job", "직무"), Column.money("amount", "금액")),
            List.of(List.of("백엔드 개발", new BigDecimal("1234567"))), null, null);
        var base = answer();
        var two = new Answer("18", "OK", "c", false, false, null, List.of(base.tables().get(0), second), null, List.of(), "u", "2026-10", List.of(), true, List.of());
        when(mapper.saved("admin", "18")).thenReturn(json.writeValueAsString(two)); when(mapper.download("admin", 2)).thenReturn(1);
        var file = new AiAnalysisArchive(mapper, actor, projection, json, new ExcelExportService(), calendar).excel("18");
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(file.bytes()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetAt(0).getRow(3).getCell(4).getNumericCellValue()).isEqualTo(-200);
            assertThat(workbook.getSheetAt(1).getSheetName()).isEqualTo("직무별");
            assertThat(workbook.getSheetAt(1).getRow(3).getCell(1).getNumericCellValue()).isEqualTo(1234567);
        }
    }
    @Test void disconnectedGatewayRequiresNoSettingsAndNeverChangesAnswer() {
        var source = answer(); var gateway = new DisconnectedAiAnalysisGateway(); gateway.accept("DEV-19", source);
        assertThat(source).isEqualTo(answer()); assertThat(gateway.configured()).isFalse();
    }
}
