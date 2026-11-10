package com.company.common.controller;

import com.company.common.mapper.MenuMapper;
import com.company.common.service.CommonMenuService;
import com.company.common.service.MenuRules;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.company.common.service.MenuFixtures.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonMenuController.class)
@ContextConfiguration(classes = {CommonMenuController.class, CommonMenuService.class, MenuExceptionHandler.class})
class CommonMenuControllerTest {
    @Autowired MockMvc mvc;
    @MockBean MenuMapper mapper;

    @Test
    void anonymousReadReturnsExactlyEightFieldsIncludingNulls() throws Exception {
        assertThat(new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8))
            .contains("/api/common/menus:", "operationId: getCommonMenus");
        when(mapper.selectCandidates()).thenReturn(List.of(row("owned", null, "ADDED", 9)));
        mvc.perform(get("/api/common/menus"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(content().json("""
                {"success":true,"meta":{"screenAccess":[{"scrnId":"ADDED","available":true,"pcOnlyYn":"N"}]},
                 "data":[{"menuId":"owned","upperMenuId":null,"menuNm":"메뉴 owned","scrnId":"ADDED",
                 "menuLvl":7,"sortOrd":9,"pcOnlyYn":"N","iconNm":null}]}
                """, true));
        verify(mapper).selectCandidates();
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void emptyIsSuccessfulAndHasAnEmptyAccessList() throws Exception {
        when(mapper.selectCandidates()).thenReturn(List.of());
        mvc.perform(get("/api/common/menus")).andExpect(status().isOk())
            .andExpect(content().json("{\"success\":true,\"meta\":{\"screenAccess\":[]},\"data\":[]}", true));
    }

    @Test
    void unknownQueryDoesNotFilterOrReflectRequestMetadata() throws Exception {
        when(mapper.selectCandidates()).thenReturn(List.of(row("owned", null, "ADDED", 1)));
        mvc.perform(get("/api/common/menus").queryParam("scrnId", "OTHER").header("X-Request-Id", "untrusted"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].scrnId").value("ADDED"))
            .andExpect(header().doesNotExist("X-Request-Id"));
        verify(mapper).selectCandidates();
    }

    @Test
    void databaseFailureNeverBecomesEmptySuccessAndDoesNotLeakDetails() throws Exception {
        when(mapper.selectCandidates()).thenThrow(new DataAccessResourceFailureException("private connection SQL"));
        mvc.perform(get("/api/common/menus")).andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(content().json("{\"success\":false,\"meta\":{}}", true));
    }

    @Test
    void changedDatabaseValuesAndTiesAreReturnedInSourceOrder() throws Exception {
        var b = row("b", null, "B", 4);
        var a = row("a", null, "A", 4);
        when(mapper.selectCandidates()).thenReturn(List.of(b, a));
        mvc.perform(get("/api/common/menus")).andExpect(jsonPath("$.data[0].menuId").value("a"));
        b.setMenuNm("변경된 이름");
        b.setSortOrd(2);
        mvc.perform(get("/api/common/menus")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].menuNm").value("변경된 이름"));
        verify(mapper, times(2)).selectCandidates();
    }
}
