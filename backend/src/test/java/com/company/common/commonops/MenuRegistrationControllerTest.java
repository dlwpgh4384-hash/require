package com.company.common.controller;

import com.company.common.controller.ApiExceptionHandler;
import com.company.common.controller.MenuRegistrationController;
import com.company.common.controller.MenuRegistrationExceptionHandler;
import com.company.common.dto.MenuRegistrationContracts.Item;
import com.company.common.mapper.MenuRegistrationMapper;
import com.company.common.mapper.MenuRegistrationRow;
import com.company.common.mapper.UserMapper;
import com.company.common.service.DevelopedScreenRegistry;
import com.company.common.service.MenuRegistrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MenuRegistrationController.class)
@ContextConfiguration(classes = {MenuRegistrationController.class, MenuRegistrationService.class,
    DevelopedScreenRegistry.class, MenuRegistrationExceptionHandler.class, ApiExceptionHandler.class})
class MenuRegistrationControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean MenuRegistrationMapper mapper;
    @MockBean UserMapper management;

    @BeforeEach void setup() throws Exception {
        var contract = new ClassPathResource("contracts/openapi.yaml")
            .getContentAsString(StandardCharsets.UTF_8);
        assertThat(contract).contains("/api/common/menus:", "operationId: postCommonMenu");
        var parent = new MenuRegistrationRow();
        parent.menuId = "G007";
        parent.menuNm = "합성 부모";
        parent.menuLvl = 1;
        parent.useYn = "N";
        when(mapper.selectParents()).thenReturn(List.of(parent));
        when(mapper.selectMenu("G007")).thenReturn(parent);
        when(mapper.selectIssuedNumber(anyString())).thenReturn(BigInteger.valueOf(999));
        when(mapper.selectNextSortOrder(any())).thenReturn(8);
    }

    private ObjectNode child() {
        return json.createObjectNode().put("menuLvl", 2).put("menuNm", "합성 메뉴")
            .put("upperMenuId", "G007").put("scrnId", "UI-25").put("pcOnlyYn", "N");
    }
    private org.springframework.test.web.servlet.ResultActions create(ObjectNode body) throws Exception {
        return mvc.perform(post("/api/common/menus").contentType("application/json").content(body.toString()));
    }
    private void noWrites() {
        verify(mapper, never()).insertMenu(any());
        verify(mapper, never()).insertPermissions(anyString());
        verify(management, never()).insertHistory(any());
    }

    @Test
    @DisplayName("happy auth side-effect: Sidebar menu registration initializes TB_SY_ROLE_MENU_AUTH before one history")
    void childIsAnonymousAppendedAndInitializedBeforeOneHistory() throws Exception {
        mvc.perform(post("/api/common/menus").contentType("application/json").content(child().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.menuId").value("M1000"))
            .andExpect(jsonPath("$.data.sortOrd").value(8)).andExpect(jsonPath("$.data.iconNm").isEmpty());
        var item = new Item("M1000", "G007", "합성 메뉴", "UI-25", 2, 8, "Y", "N", "N", null);
        var order = inOrder(management, mapper);
        order.verify(management).lockManagement();
        order.verify(mapper).selectMenu("G007");
        order.verify(mapper).screenExists("UI-25");
        order.verify(mapper).selectIssuedNumber("M");
        order.verify(mapper).selectNextSortOrder("G007");
        order.verify(mapper).insertMenu(item);
        order.verify(mapper).insertPermissions("M1000");
        order.verify(management).insertHistory(new com.company.common.dto.UserContracts.History(
            "TB_SY_MENU", "M1000", "NEW", "MENU_NM", null, "합성 메뉴"));
        verify(management, times(1)).insertHistory(any());
    }

    @Test void anonymousGroupHasFolderAndNoScreenOrParent() throws Exception {
        when(mapper.selectIssuedNumber("G")).thenReturn(BigInteger.ZERO);
        create(json.createObjectNode().put("menuLvl", 1).put("menuNm", " 합성 묶음 "))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.menuId").value("G001"))
            .andExpect(jsonPath("$.data.menuNm").value(" 합성 묶음 "))
            .andExpect(jsonPath("$.data.scrnId").isEmpty()).andExpect(jsonPath("$.data.upperMenuId").isEmpty())
            .andExpect(jsonPath("$.data.iconNm").value("folder"));
        verify(mapper).insertPermissions("G001");
        verify(mapper, never()).screenExists(anyString());
    }

    @Test void levelOneOptionsNeedNoDatabaseWritesOrReads() throws Exception {
        mvc.perform(get("/api/common/menu-options").param("menuLvl", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.upperMenus").isEmpty())
            .andExpect(jsonPath("$.data.screens").isEmpty());
        verifyNoInteractions(mapper, management);
    }
    @Test void childOptionsIncludeStoppedParentsAndOnlyDevelopedScreens() throws Exception {
        mvc.perform(get("/api/common/menu-options").param("menuLvl", "2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.upperMenus[0].menuId").value("G007"))
            .andExpect(jsonPath("$.data.screens[*].scrnId")
                .value(org.hamcrest.Matchers.contains("UI-23", "UI-24", "UI-25", "UI-27", "UI-15")));
        noWrites();
        verifyNoInteractions(management);
    }
    @Test void missingOptionLevelIsFieldError() throws Exception {
        mvc.perform(get("/api/common/menu-options")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("menuLvl"));
        noWrites();
    }
    @ParameterizedTest @ValueSource(strings = {"0", "3", "no", "1.5"})
    void invalidOptionLevelIsFieldError(String value) throws Exception {
        mvc.perform(get("/api/common/menu-options").param("menuLvl", value))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("menuLvl"));
        noWrites();
    }
    @Test void missingParentHasNoWrites() throws Exception {
        var body = child(); body.remove("upperMenuId");
        create(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("upperMenuId"));
        noWrites();
    }
    @Test void missingScreenHasNoWrites() throws Exception {
        var body = child(); body.remove("scrnId");
        create(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("scrnId"));
        noWrites();
    }
    @Test void missingPcFlagHasNoWrites() throws Exception {
        var body = child(); body.remove("pcOnlyYn");
        create(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("pcOnlyYn"));
        noWrites();
    }
    @Test void missingLevelHasNoWrites() throws Exception {
        var body = child(); body.remove("menuLvl");
        create(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("menuLvl"));
        noWrites();
    }
    @Test void missingNameHasNoWrites() throws Exception {
        var body = child(); body.remove("menuNm");
        create(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("menuNm"));
        noWrites();
    }
    @ParameterizedTest @ValueSource(strings = {"", "   ", "\u00a0", "\u3000"})
    void whitespaceNameRejected(String value) throws Exception {
        create(child().put("menuNm", value)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("menuNm"));
        noWrites();
    }
    @Test void fiftyOneCodePointsRejected() throws Exception {
        create(child().put("menuNm", "😀".repeat(51))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("menuNm"));
        noWrites();
    }
    @Test void fiftyCodePointsAcceptedWithoutNormalizing() throws Exception {
        create(child().put("menuNm", "😀".repeat(50))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menuNm").value("😀".repeat(50)));
    }
    @Test void unknownParentIs400() throws Exception {
        create(child().put("upperMenuId", "missing")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("upperMenuId"));
        noWrites();
    }
    @Test void childCannotBeParent() throws Exception {
        var parent = new MenuRegistrationRow(); parent.menuLvl = 2;
        when(mapper.selectMenu("G007")).thenReturn(parent);
        create(child()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("upperMenuId"));
        noWrites();
    }
    @Test void commonCodeScreenIsSelectableForAnonymousMenuRegistration() throws Exception {
        create(child().put("scrnId", "UI-27"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.scrnId").value("UI-27"));
        verify(mapper).screenExists("UI-27");
        verify(mapper).insertMenu(org.mockito.ArgumentMatchers.argThat(item -> "UI-27".equals(item.scrnId())));
    }
    @ParameterizedTest @ValueSource(strings = {"UI-26", "UI-16", "UI-01", "ui-25", " UI-25"})
    void placeholderAndNormalizedScreenIdsAreNotDeveloped(String id) throws Exception {
        create(child().put("scrnId", id)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("scrnId"));
        noWrites();
    }
    @Test
    @DisplayName("business: duplicate SCRN_ID preserves TB_SY_MENU and TB_SY_ROLE_MENU_AUTH")
    void duplicateEvenWhenStoppedHasExactMessageAndNoWrites() throws Exception {
        when(mapper.screenExists("UI-25")).thenReturn(true);
        mvc.perform(post("/api/common/menus").contentType("application/json").content(child().toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("이미 다른 메뉴에 등록된 화면입니다."))
            .andExpect(jsonPath("$.errors[0].field").value("scrnId"));
        noWrites();
    }
    @ParameterizedTest @ValueSource(strings = {"menuId", "iconNm", "sortOrd", "lockYn", "useYn"})
    void generatedFieldsCannotBeSubmitted(String field) throws Exception {
        create(child().put(field, "untrusted")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value(field));
        noWrites();
    }
    @ParameterizedTest @ValueSource(strings = {"upperMenuId", "scrnId", "pcOnlyYn"})
    void groupDoesNotAcceptHiddenChildFields(String field) throws Exception {
        create(json.createObjectNode().put("menuLvl", 1).put("menuNm", "묶음").putNull(field))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
        noWrites();
    }
    @Test void idCapacityIsAtomicBusinessFailure() throws Exception {
        when(mapper.selectIssuedNumber("M")).thenReturn(new BigInteger("9999999999999999999"));
        create(child()).andExpect(status().isBadRequest());
        noWrites();
    }
    @Test void sortCapacityIsAtomicBusinessFailure() throws Exception {
        when(mapper.selectNextSortOrder("G007")).thenReturn(100000);
        create(child()).andExpect(status().isBadRequest());
        noWrites();
    }
    @Test void invalidJsonUsesErrorsNotLegacyMenuCatchAll() throws Exception {
        mvc.perform(post("/api/common/menus").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("body"));
        noWrites();
    }
    @Test void databaseErrorIsSanitized() throws Exception {
        doThrow(new IllegalStateException("private SQL detail")).when(mapper).insertPermissions(anyString());
        create(child()).andExpect(status().isInternalServerError())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL detail"))));
    }
}
