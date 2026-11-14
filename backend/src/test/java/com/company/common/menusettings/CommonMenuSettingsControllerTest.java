package com.company.common.controller;

import com.company.common.service.MenuSettingsTestData;

import com.company.common.controller.ApiExceptionHandler;
import com.company.common.controller.CommonMenuSettingsController;
import com.company.common.controller.MenuSettingsExceptionHandler;
import com.company.common.dto.UserContracts.History;
import com.company.common.mapper.MenuSettingsMapper;
import com.company.common.mapper.UserMapper;
import com.company.common.service.CommonMenuSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
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

@WebMvcTest(CommonMenuSettingsController.class)
@ContextConfiguration(classes = {CommonMenuSettingsController.class, CommonMenuSettingsService.class,
    MenuSettingsExceptionHandler.class, ApiExceptionHandler.class})
class CommonMenuSettingsControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean MenuSettingsMapper mapper;
    @MockBean UserMapper management;
    MenuSettingsTestData data;

    @BeforeEach void setup() throws Exception {
        data = new MenuSettingsTestData(mapper, management);
        var contract = new ClassPathResource("contracts/openapi.yaml")
            .getContentAsString(StandardCharsets.UTF_8);
        assertThat(contract).contains("/api/common/menu-settings:",
            "operationId: getCommonMenuSettings", "operationId: putCommonMenuSettings");
    }

    private ObjectNode item(String id) {
        var row = data.rows.get(id);
        var result = json.createObjectNode().put("menuId", id).put("menuNm", row.menuNm()).put("sortOrd", row.sortOrd());
        if (row.upperMenuId() == null) result.putNull("upperMenuId");
        else result.put("upperMenuId", row.upperMenuId());
        return result;
    }
    private ObjectNode body(ObjectNode... items) {
        var body = json.createObjectNode();
        var rows = body.putArray("items");
        for (var item : items) rows.add(item);
        body.putArray("movedMenuIds");
        return body;
    }
    private org.springframework.test.web.servlet.ResultActions save(ObjectNode body) throws Exception {
        return mvc.perform(put("/api/common/menu-settings").contentType("application/json").content(body.toString()));
    }
    private void unchanged() {
        verify(mapper, never()).updateSetting(any());
        assertThat(data.histories).isEmpty();
    }

    @Test void fullManagementTreeContainsStoppedAndLockedRowsAndTenPublicFields() throws Exception {
        mvc.perform(get("/api/common/menu-settings")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(6))
            .andExpect(jsonPath("$.data.items[2].useYn").value("N"))
            .andExpect(jsonPath("$.data.items[5].lockYn").value("Y"))
            .andExpect(jsonPath("$.data.items[0].length()").value(10));
        verifyNoInteractions(management);
    }
    @ParameterizedTest @ValueSource(ints = {1, 2})
    void levelFilter(int level) throws Exception {
        mvc.perform(get("/api/common/menu-settings").param("menuLvl", String.valueOf(level)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(level == 1 ? 2 : 4));
    }
    @ParameterizedTest @ValueSource(strings = {"", " ", "0", "3", "no", "1.5", "2147483648"})
    void invalidLevelIs400(String level) throws Exception {
        mvc.perform(get("/api/common/menu-settings").param("menuLvl", level)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("menuLvl"));
        unchanged();
    }
    @Test
    @DisplayName("happy auth side-effect: anonymous menu save persists history; MOD_USER_ID=admin is assigned by MyBatis")
    void anonymousLockedNameAndDirectThirdToFirstPersistAndReadBack() throws Exception {
        var request = body(item("M004").put("menuNm", " 새 잠금 이름 "), item("M003").put("sortOrd", 1),
            item("M001").put("sortOrd", 2), item("M002").put("sortOrd", 3));
        request.withArray("movedMenuIds").add("M003");
        mvc.perform(put("/api/common/menu-settings").contentType("application/json").content(request.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[1].menuId").value("M003"));
        mvc.perform(get("/api/common/menu-settings")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[1].menuId").value("M003"))
            .andExpect(jsonPath("$.data.items[1].sortOrd").value(1))
            .andExpect(jsonPath("$.data.items[5].menuNm").value(" 새 잠금 이름 "));
        assertThat(data.histories).containsExactlyInAnyOrder(
            new History("TB_SY_MENU", "M004", "UPD", "MENU_NM", "잠금 메뉴", " 새 잠금 이름 "),
            new History("TB_SY_MENU", "M003", "UPD", "SORT_ORD", "3", "1"));
        var ordered = inOrder(management, mapper);
        ordered.verify(management).lockManagement();
        ordered.verify(mapper).selectSettings();
        var sql = new ClassPathResource("mapper/MenuSettingsMapper.xml")
            .getContentAsString(StandardCharsets.UTF_8);
        assertThat(sql).contains("mod_user_id='admin'", "mod_dttm=clock_timestamp()");
    }
    @Test void partialAnonymousUpdatePreservesUnsubmittedRows() throws Exception {
        var untouched = data.rows.get("M001");
        save(body(item("M003").put("menuNm", "이름 변경"))).andExpect(status().isOk());
        assertThat(data.rows.get("M001")).isEqualTo(untouched);
        assertThat(data.rows).hasSize(6);
    }
    @ParameterizedTest @ValueSource(strings = {"", " ", "\u00a0", "\u3000"})
    void emptyNamesLeaveRowsAndHistoryUnchanged(String name) throws Exception {
        save(body(item("M003").put("menuNm", name))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("menuNm"));
        unchanged();
    }
    @Test void fiftyOneCodePointsRejectedBeforeAnyWrites() throws Exception {
        save(body(item("M003").put("menuNm", "😀".repeat(51)))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("menuNm"));
        unchanged();
    }
    @Test void fiftyUnicodeCodePointsAccepted() throws Exception {
        save(body(item("M003").put("menuNm", "😀".repeat(50)))).andExpect(status().isOk());
        assertThat(data.rows.get("M003").menuNm()).isEqualTo("😀".repeat(50));
    }
    @Test
    @DisplayName("business: locked USE_YN cannot change to N; all menu writes and history remain unchanged")
    void lockedDisableRejectedEvenWhenOtherRowsAreValid() throws Exception {
        var request = body(item("M003").put("menuNm", "유효 변경"), item("M004").put("useYn", "N"));
        mvc.perform(put("/api/common/menu-settings").contentType("application/json").content(request.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("useYn"));
        unchanged();
        assertThat(data.rows.get("M004").useYn()).isEqualTo("Y");
    }
    @Test void lockedParentChangeRejected() throws Exception {
        save(body(item("M004").put("upperMenuId", "G001").put("sortOrd", 4)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("upperMenuId"));
        unchanged();
    }
    @Test void lockedSameGroupReorderAllowed() throws Exception {
        save(body(item("M004").put("sortOrd", 2))).andExpect(status().isOk());
        assertThat(data.rows.get("M004").sortOrd()).isEqualTo(2);
        assertThat(data.histories).isEmpty();
    }
    @Test
    @DisplayName("side-effect: USE_YN Y to N and PC_ONLY_YN N to Y persist with itemized TB_SY_CHG_HIST")
    void generalMoveRecordsEachChangedFieldButNotDisplacedSibling() throws Exception {
        var request = body(item("M003").put("upperMenuId", "G002").put("sortOrd", 1)
            .put("menuNm", "변경 메뉴").put("useYn", "N").put("pcOnlyYn", "Y"), item("M004").put("sortOrd", 2));
        request.withArray("movedMenuIds").add("M003");
        mvc.perform(put("/api/common/menu-settings").contentType("application/json").content(request.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertThat(data.histories).extracting(History::item)
            .containsExactlyInAnyOrder("MENU_NM", "UPPER_MENU_ID", "USE_YN", "PC_ONLY_YN", "SORT_ORD");
        assertThat(data.histories).allMatch(row -> row.key().equals("M003") && row.before() != null && row.after() != null);
        assertThat(data.rows.get("M003").upperMenuId()).isEqualTo("G002");
        assertThat(data.rows.get("M003").useYn()).isEqualTo("N");
        assertThat(data.rows.get("M003").pcOnlyYn()).isEqualTo("Y");
    }
    @Test void groupPreservesFlagsEvenIfSubmittedAndNoOpCreatesNoHistory() throws Exception {
        save(body(item("G002").put("useYn", "Y").put("pcOnlyYn", "N"))).andExpect(status().isOk());
        assertThat(data.rows.get("G002").useYn()).isEqualTo("N");
        assertThat(data.rows.get("G002").pcOnlyYn()).isEqualTo("Y");
        unchanged();
    }
    @Test void groupCannotBeNested() throws Exception {
        save(body(item("G001").put("upperMenuId", "G002"))).andExpect(status().isBadRequest());
        unchanged();
    }
    @Test void unknownExistingIdIsNotUpsert() throws Exception {
        save(body(item("M003").put("menuId", "unknown"))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("menuId"));
        unchanged();
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "M001", ""})
    void invalidParentRejected(String parent) throws Exception {
        save(body(item("M003").put("upperMenuId", parent))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("upperMenuId"));
        unchanged();
    }
    @Test void siblingOrderOverlayIncludesOmittedRows() throws Exception {
        save(body(item("M003").put("sortOrd", 1))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("sortOrd"));
        unchanged();
    }
    @ParameterizedTest @ValueSource(ints = {0, -1, 100000})
    void sortBounds(int order) throws Exception {
        save(body(item("M003").put("sortOrd", order))).andExpect(status().isBadRequest());
        unchanged();
    }
    @ParameterizedTest @ValueSource(strings = {"menuLvl", "scrnId", "iconNm", "lockYn"})
    void immutableFieldsRejected(String field) throws Exception {
        save(body(item("M003").put(field, "untrusted"))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value(field));
        unchanged();
    }
    @ParameterizedTest @ValueSource(strings = {"menuId", "menuNm", "upperMenuId", "sortOrd"})
    void requiredFieldsCannotBeOmitted(String field) throws Exception {
        var row = item("M003"); row.remove(field);
        save(body(row)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
        unchanged();
    }
    @Test void duplicateItemRejected() throws Exception {
        save(body(item("M003"), item("M003"))).andExpect(status().isBadRequest());
        unchanged();
    }
    @Test void movedMustBeUniqueSubsetOfItems() throws Exception {
        var request = body(item("M003")); request.withArray("movedMenuIds").add("M001");
        save(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("movedMenuIds"));
        unchanged();
    }
    @Test void duplicateMovedRejected() throws Exception {
        var request = body(item("M003")); request.withArray("movedMenuIds").add("M003").add("M003");
        save(request).andExpect(status().isBadRequest()); unchanged();
    }
    @ParameterizedTest @ValueSource(strings = {"useYn", "pcOnlyYn"})
    void flagsMustBeYnAndCannotBeNull(String field) throws Exception {
        save(body(item("M003").put(field, "invalid"))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value(field));
        save(body(item("M003").putNull(field))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value(field));
        unchanged();
    }
    @Test void nullNameRejected() throws Exception {
        save(body(item("M003").putNull("menuNm"))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("menuNm"));
        unchanged();
    }
    @Test void missingMovedArrayRejected() throws Exception {
        var request = body(item("M003")); request.remove("movedMenuIds");
        save(request).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("movedMenuIds"));
        unchanged();
    }
    @Test void fractionalOrderRejected() throws Exception {
        save(body(item("M003").put("sortOrd", 1.5))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("sortOrd"));
        unchanged();
    }
    @Test void databaseFailureDoesNotLeakDetails() throws Exception {
        when(mapper.selectSettings()).thenThrow(new IllegalStateException("private SQL detail"));
        mvc.perform(get("/api/common/menu-settings")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL detail"))));
    }
    @Test void emptyItemsRejected() throws Exception { save(body()).andExpect(status().isBadRequest()); unchanged(); }
    @Test void malformedJsonUsesApiErrorNotSidebarAdvice() throws Exception {
        mvc.perform(put("/api/common/menu-settings").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("body"));
        unchanged();
    }
    @Test void disablingKeepsRowInManagement() throws Exception {
        save(body(item("M003").put("useYn", "N"))).andExpect(status().isOk());
        assertThat(data.rows).containsKey("M003");
        mvc.perform(get("/api/common/menu-settings")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[3].useYn").value("N"));
    }
}
