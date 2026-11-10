package com.cost.shell;

import com.cost.shell.menu.MenuController;
import com.cost.shell.menu.MenuMapper;
import com.cost.shell.menu.MenuRow;
import com.cost.shell.menu.MenuService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({MenuController.class, HealthController.class})
@Import({MenuService.class, ApiErrors.class})
@ExtendWith(OutputCaptureExtension.class)
class MenuHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean MenuMapper mapper;

    private MenuRow row(String id, String parent, String screen, String active, int order) {
        return json.convertValue(Map.of(
            "menuId", id, "upperMenuId", parent, "menuNm", "검증 " + id,
            "scrnId", screen, "menuLvl", parent.isEmpty() ? 1 : 2,
            "sortOrd", order, "pcOnlyYn", "N", "iconNm", "grid", "useYn", active
        ), MenuRow.class);
    }

    @Test void anonymousMenuUsesDoubleEnvelopeAndEightPublicFields() throws Exception {
        when(mapper.findAll()).thenReturn(List.of(
            row("root", "", "", "Y", 1), row("b", "root", "screen-b", "Y", 2),
            row("a", "root", "screen-a", "Y", 2)));
        var result = mvc.perform(get("/api/common/menus"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.success").value(true))
            .andExpect(jsonPath("$.data.data[1].menuId").value("a"))
            .andExpect(jsonPath("$.data.data[2].menuId").value("b"))
            .andExpect(jsonPath("$.meta.screenStates.screen-a.available").value(true))
            .andReturn();
        var item = json.readTree(result.getResponse().getContentAsString()).at("/data/data/0");
        assertThat(item.size()).isEqualTo(8);
        assertThat(item.has("useYn")).isFalse();
        assertThat(item.has("regUserId")).isFalse();
        verify(mapper).findAll();
        verifyNoMoreInteractions(mapper);
    }

    @Test void invalidAndInactiveBranchesAreExcludedAndWarned(CapturedOutput output) throws Exception {
        when(mapper.findAll()).thenReturn(List.of(
            row("good", "", "valid", "Y", 1),
            row("off", "", "disabled", "N", 2), row("child", "off", "child-screen", "Y", 1),
            row("orphan", "missing", "lost", "Y", 1), row("self", "self", "self-screen", "Y", 1),
            row("cycle-a", "cycle-b", "", "Y", 1), row("cycle-b", "cycle-a", "", "Y", 1),
            row("cycle-child", "cycle-a", "cyclic", "Y", 1)));
        mvc.perform(get("/api/common/menus"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data.length()").value(1))
            .andExpect(jsonPath("$.data.data[0].menuId").value("good"))
            .andExpect(jsonPath("$.meta.screenStates.disabled.available").value(false))
            .andExpect(jsonPath("$.meta.screenStates.child-screen.available").value(false));
        assertThat(output.getAll()).contains("INVALID_HIERARCHY", "orphan", "self", "cycle-a");
        verify(mapper).findAll();
        verifyNoMoreInteractions(mapper);
    }

    @Test void mixedNodeRetainsGroupMeaningEvenWhenChildIsHidden(CapturedOutput output) throws Exception {
        when(mapper.findAll()).thenReturn(List.of(
            row("mixed", "", "mixed-screen", "Y", 1), row("off", "mixed", "off-screen", "N", 1)));
        mvc.perform(get("/api/common/menus"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.parentMenuIds[0]").value("mixed"))
            .andExpect(jsonPath("$.meta.screenStates.mixed-screen.available").value(false));
        assertThat(output.getAll()).contains("MIXED_NODE", "mixed");
        verifyNoMoreInteractionsAfterRead();
    }

    private void verifyNoMoreInteractionsAfterRead() {
        verify(mapper).findAll();
        verifyNoMoreInteractions(mapper);
    }

    @Test void emptyDoesNotInitializeAndFailureDoesNotLeakConnectionDetails() throws Exception {
        when(mapper.findAll()).thenReturn(List.of());
        mvc.perform(get("/api/common/menus"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.data").isEmpty());
        when(mapper.findAll()).thenThrow(new DataAccessResourceFailureException("private-jdbc-details"));
        var response = mvc.perform(get("/api/common/menus"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false)).andReturn();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("private-jdbc-details", "stackTrace");
        // Re-stub without invoking the previously throwing mock method.
        doReturn(List.of(row("recovered", "", "screen", "Y", 1))).when(mapper).findAll();
        mvc.perform(get("/api/common/menus"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.data[0].menuId").value("recovered"));
        verify(mapper, times(3)).findAll();
        verifyNoMoreInteractions(mapper);
    }

    @Test void healthIsProcessLivenessAndUnknownOrWriteRoutesAreNotImplemented() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/unknown")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
        mvc.perform(post("/api/common/menus")).andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(mapper);
    }

    @Test void durableOpenApiIsLoadedThroughClasspath() throws Exception {
        try (var input = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            var contract = new org.yaml.snakeyaml.Yaml().loadAs(input, Map.class);
            assertThat(((Map<?, ?>) contract.get("paths")).containsKey("/api/common/menus")).isTrue();
        }
    }
}
