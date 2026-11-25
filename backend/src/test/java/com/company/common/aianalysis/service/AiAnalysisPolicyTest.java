package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.aianalysis.mapper.*;
import com.company.common.roles.mapper.*;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiAnalysisPolicyTest {
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final CurrentBusinessActor actor = mock(CurrentBusinessActor.class);
    final RoleMapper roles = mock(RoleMapper.class);
    final AiAnalysisProjection projection = new AiAnalysisProjection(roles, actor);
    Answer answer() {
        return new Answer("17", "OK", "원천 결과", new Table(List.of(new Column("developerName", "개발자"), new Column("revenue", "매출"), new Column("salary", "급여"), new Column("unitPrice", "지급단가"), new Column("personalCost", "개인원가")),
            List.of(List.of("개발자이름", 500, 200, 100, 300))), List.of(), "실제 원천", "2026-11", false, List.of(), List.of());
    }
    @Test void onePolicyProjectionIsIdenticalForResponseSnapshotExcelAndExternalBoundary() throws Exception {
        when(actor.roles()).thenReturn(Set.of("ADMIN")); var role = new RoleRows.Role(); role.roleId = "ADMIN"; role.useYn = "Y"; role.snstvViewYn = "N"; when(roles.selectRole("ADMIN")).thenReturn(role);
        var projected = projection.apply(answer());
        assertThat(projected.answerStatus()).isEqualTo("OK"); assertThat(projected.table().columns()).extracting(Column::key).containsExactly("developerName", "revenue");
        assertThat(projected.table().rows().get(0)).containsExactly("개발자이름", 500);
        var serialized = json.writeValueAsString(projected); assertThat(serialized).doesNotContain("salary", "unitPrice", "personalCost", "급여", "지급단가", "개인원가");
        assertThat(projection.apply(json.readValue(serialized, Answer.class))).isEqualTo(projected);
        var mapper = mock(AiAnalysisMapper.class); when(mapper.identities()).thenReturn(List.of(new AiAnalysisRows.Identity("개발자이름", "DEV-19"), new AiAnalysisRows.Identity("담당자이름", "")));
        var external = mock(AiAnalysisExternalGateway.class); var pseudonyms = new AiAnalysisPseudonymizer(mapper, json);
        pseudonyms.send(external, "개발자이름 담당자이름 매출", projected);
        var question = org.mockito.ArgumentCaptor.forClass(String.class); var result = org.mockito.ArgumentCaptor.forClass(Answer.class); verify(external).accept(question.capture(), result.capture());
        assertThat(question.getValue()).contains("DEV-19").doesNotContain("개발자이름", "담당자이름");
        assertThat(json.writeValueAsString(result.getValue())).contains("DEV-19").doesNotContain("개발자이름", "salary", "unitPrice", "personalCost");
    }
    @Test void unionOfAssignedStoredRolesEnablesSensitivityOnlyWhenAtLeastOnePermitsIt() {
        var no = new RoleRows.Role(); no.roleId = "A"; no.useYn = "Y"; no.snstvViewYn = "N"; var yes = new RoleRows.Role(); yes.roleId = "B"; yes.useYn = "Y"; yes.snstvViewYn = "Y";
        when(actor.roles()).thenReturn(Set.of("A", "B")); when(roles.selectRole("A")).thenReturn(no); when(roles.selectRole("B")).thenReturn(yes);
        assertThat(projection.salaryAllowed()).isTrue(); assertThat(projection.apply(answer()).table()).isEqualTo(answer().table());
        yes.useYn = "N"; assertThat(projection.salaryAllowed()).isFalse();
    }
    @Test void downloadReappliesPolicyAfterStoredSensitiveAccessHasBeenRemoved() throws Exception {
        var mapper = mock(AiAnalysisMapper.class); var calendar = mock(BusinessCalendar.class); when(calendar.now()).thenReturn(java.time.Instant.parse("2026-11-25T00:00:00Z"));
        when(actor.userId()).thenReturn("admin"); when(actor.roles()).thenReturn(Set.of()); when(mapper.saved("admin", "17")).thenReturn(json.writeValueAsString(answer()));
        when(mapper.download("admin", "17")).thenReturn(1);
        var archive = new AiAnalysisArchive(mapper, actor, projection, json, new ExcelExportService(), calendar);
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(archive.excel("17").bytes()))) {
            var sheet = workbook.getSheetAt(0); assertThat(sheet.getRow(2).getPhysicalNumberOfCells()).isEqualTo(2);
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("개발자"); assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("매출");
            assertThat(sheet.getRow(3).getCell(1).getNumericCellValue()).isEqualTo(500);
        }
        verify(mapper).download("admin", "17");
    }
    @Test void disconnectedGatewayRequiresNoSettingsAndNeverChangesAnswer() {
        var source = answer(); new DisconnectedAiAnalysisGateway().accept("DEV-19", source); assertThat(source).isEqualTo(answer());
    }
}
