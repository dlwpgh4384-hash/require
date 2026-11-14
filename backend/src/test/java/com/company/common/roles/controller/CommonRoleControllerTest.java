package com.company.common.roles.controller;

import com.company.common.roles.service.CommonRoleService;
import com.company.common.roles.mapper.RoleMapper;

import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.users.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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

@WebMvcTest(CommonRoleController.class)
@ContextConfiguration(classes = {CommonRoleController.class, CommonRoleService.class,
    RoleExceptionHandler.class, ApiExceptionHandler.class})
class CommonRoleControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean RoleMapper mapper;
    @MockBean UserMapper management;
    RoleTestData data;
    @BeforeEach void setup() throws Exception {
        data = new RoleTestData(mapper, management);
        String contract = new ClassPathResource("contracts/openapi.yaml")
            .getContentAsString(StandardCharsets.UTF_8);
        assertThat(contract).contains("/api/common/roles:",
            "/api/common/roles/{roleId}:", "/api/common/roles/{roleId}/change-impact:",
            "/api/common/roles/{roleId}/stop:", "operationId: getCommonRoles",
            "operationId: postCommonRoleCopy", "operationId: getCommonRole",
            "operationId: putCommonRole", "operationId: getCommonRoleChangeImpact",
            "operationId: postCommonRoleStop");
    }
    private String copy(String name) throws Exception {
        return json.writeValueAsString(Map.of("sourceRoleId","ADMIN","roleName",name,"dataScopeCd","PROJ"));
    }
    private ObjectNode update(String id, String scope, String sensitive) {
        var body=json.createObjectNode().put("dataScopeCd",scope).put("snstvViewYn",sensitive);
        body.set("menuAuths",json.valueToTree(data.menus.get(id).stream().map(row -> row.value().input()).toList()));
        body.putArray("confirmedAffectedUserIds");
        return body;
    }
    @Test void activeTabsUsePersistedNamesAndBasicIdentity() throws Exception {
        data.roles.get("R-001").useYn="N";
        mvc.perform(get("/api/common/roles")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.items.length()").value(5))
            .andExpect(jsonPath("$.data.items[0].basicRole").value(true));
        verify(mapper,never()).insertHistory(any());
        verifyNoInteractions(management);
    }
    @Test void readsEmptyRealMatrixWithoutSynthesizingRows() throws Exception {
        data.menus.get("ADMIN").clear();
        mvc.perform(get("/api/common/roles/ADMIN")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menuAuths").isEmpty()).andExpect(jsonPath("$.data.dataScopes.length()").value(3));
        verify(mapper,never()).copyMenus(anyString(),anyString());
    }
    @Test void anonymousCopyUsesChosenScopeAndPersistedSourceFlags() throws Exception {
        mvc.perform(post("/api/common/roles").contentType("application/json").content(copy("합성 복사")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.role.roleId").value("R-002"))
            .andExpect(jsonPath("$.data.role.dataScopeCd").value("PROJ"))
            .andExpect(jsonPath("$.data.role.snstvViewYn").value("Y"))
            .andExpect(jsonPath("$.data.role.basicRole").value(false));
        mvc.perform(get("/api/common/roles/R-002")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menuAuths[0].updateYn").value("Y"));
        assertThat(data.menus.get("R-002").stream().map(row -> row.value().input()).toList())
            .isEqualTo(data.menus.get("ADMIN").stream().map(row -> row.value().input()).toList());
        assertThat(data.histories).singleElement().satisfies(h -> {
            assertThat(h.table()).isEqualTo("TB_SY_ROLE"); assertThat(h.type()).isEqualTo("NEW");
        });
        var order=inOrder(management,mapper);
        order.verify(management).lockManagement();
        order.verify(mapper).selectDataScopes();
        order.verify(mapper).selectRole("ADMIN");
        verify(mapper).insertRole(eq("R-002"),any(),any(),anyInt());
    }
    @Test void missingNameIsFieldErrorWithoutWrites() throws Exception {
        mvc.perform(post("/api/common/roles").contentType("application/json")
            .content("{\"sourceRoleId\":\"ADMIN\",\"dataScopeCd\":\"ALL\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("roleName"));
        verify(mapper,never()).insertRole(anyString(),any(),any(),anyInt());
    }
    @ParameterizedTest @ValueSource(strings={"", "   ", "\u00a0"})
    void whitespaceNamesAreRejected(String name) throws Exception {
        mvc.perform(post("/api/common/roles").contentType("application/json").content(copy(name)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("roleName"));
    }
    @Test void unicodeNameLimitCountsCodePointsAndPreservesAcceptedSpaces() throws Exception {
        mvc.perform(post("/api/common/roles").contentType("application/json").content(copy("😀".repeat(51))))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/common/roles").contentType("application/json").content(copy("😀".repeat(50))))
            .andExpect(status().isOk());
        mvc.perform(post("/api/common/roles").contentType("application/json").content(copy(" 합성 ")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.role.roleName").value(" 합성 "));
    }
    @Test void businessDuplicateIncludesInactiveNamesWithoutChangingPermissions() throws Exception {
        var before = List.copyOf(data.menus.get("R-001"));
        data.roles.get("R-001").useYn="N";
        mvc.perform(post("/api/common/roles").contentType("application/json").content(copy("합성 추가 역할")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("roleName"));
        verify(mapper,never()).insertRole(anyString(),any(),any(),anyInt());
        assertThat(data.roles).hasSize(6);
        assertThat(data.roles.get("R-001").useYn).isEqualTo("N");
        assertThat(data.menus.get("R-001")).isEqualTo(before);
        verify(mapper, never()).copyMenus(anyString(), anyString());
        assertThat(data.histories).isEmpty();
    }
    @Test void concurrentUniqueViolationIsAFieldError() throws Exception {
        doThrow(new org.springframework.dao.DuplicateKeyException("internal SQL"))
            .when(mapper).insertRole(anyString(),any(),any(),anyInt());
        mvc.perform(post("/api/common/roles").contentType("application/json").content(copy("합성 복사")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("roleName"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal SQL"))));
        verify(mapper,never()).copyMenus(anyString(),anyString());
    }
    @Test void missingSourceIs404() throws Exception {
        var input=(ObjectNode)json.readTree(copy("합성 복사")); input.put("sourceRoleId","missing");
        mvc.perform(post("/api/common/roles").contentType("application/json").content(input.toString()))
            .andExpect(status().isNotFound());
    }
    @Test void inactiveSourceIsRejected() throws Exception {
        data.roles.get("ADMIN").useYn="N";
        mvc.perform(post("/api/common/roles").contentType("application/json").content(copy("합성 복사")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("sourceRoleId"));
    }
    @Test void issuanceSurvivesStopAndPhysicalRemovalThroughHistory() throws Exception {
        mvc.perform(post("/api/common/roles").contentType("application/json").content(copy("합성 복사"))).andExpect(status().isOk());
        mvc.perform(post("/api/common/roles/R-002/stop")).andExpect(status().isOk());
        data.roles.remove("R-002");
        mvc.perform(post("/api/common/roles").contentType("application/json").content(copy("새 합성 복사")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.role.roleId").value("R-003"));
    }
    @Test void numberAbove999IsNotTruncated() throws Exception {
        data.add("R-999","이전 합성 역할","ALL","N");
        mvc.perform(post("/api/common/roles").contentType("application/json").content(copy("합성 복사")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.role.roleId").value("R-1000"));
    }
    @Test void authSideEffectStopTransitionsUseYnAndPreservesMenuRowsAndIdentity() throws Exception {
        var before=List.copyOf(data.menus.get("R-001"));
        assertThat(data.roles.get("R-001").useYn).isEqualTo("Y");
        mvc.perform(post("/api/common/roles/R-001/stop")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleId").value("R-001"))
            .andExpect(jsonPath("$.data.useYn").value("N"));
        mvc.perform(post("/api/common/roles/R-001/stop")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.useYn").value("N"));
        assertThat(data.roles.get("R-001").useYn).isEqualTo("N");
        verify(mapper,times(1)).stopRole("R-001");
        assertThat(data.roles).containsKey("R-001"); assertThat(data.menus.get("R-001")).isEqualTo(before);
        assertThat(data.histories).isEmpty();
        mvc.perform(get("/api/common/roles")).andExpect(jsonPath("$.data.items.length()").value(5));
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","EXEC","FIN","SALES_LDR","PM"})
    void basicStopIs400WithoutMutation(String id) throws Exception {
        mvc.perform(post("/api/common/roles/"+id+"/stop")).andExpect(status().isBadRequest());
        assertThat(data.roles.get(id).useYn).isEqualTo("Y");
        verify(mapper,never()).stopRole(anyString());
    }
    @Test void businessAssignedStopReportsDistinctUserCountAndRetainsActiveRole() throws Exception {
        data.assignments.put("R-001",2L);
        mvc.perform(post("/api/common/roles/R-001/stop")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("2명이 이 역할을 쓰고 있습니다. 시스템 › 사용자에서 먼저 빼 주세요."));
        assertThat(data.roles.get("R-001").useYn).isEqualTo("Y");
        verify(mapper, never()).stopRole(anyString());
    }
    @Test void missingRoleReadUpdateImpactAndStopReturn404() throws Exception {
        mvc.perform(get("/api/common/roles/missing")).andExpect(status().isNotFound());
        mvc.perform(get("/api/common/roles/missing/change-impact").param("dataScopeCd","ALL")).andExpect(status().isNotFound());
        mvc.perform(post("/api/common/roles/missing/stop")).andExpect(status().isNotFound());
        mvc.perform(put("/api/common/roles/missing").contentType("application/json").content(update("ADMIN","ALL","Y").toString()))
            .andExpect(status().isNotFound());
        verify(mapper,never()).updateRole(anyString(),any());
    }
    @Test void anonymousUpdatePersistsSettingsAndEveryItemHistoryWithRoleAccess() throws Exception {
        var body=update("R-001","ALL","Y");
        ((ObjectNode)body.withArray("menuAuths").get(0)).put("downloadYn","Y");
        mvc.perform(put("/api/common/roles/R-001").contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.role.dataScopeCd").value("ALL"))
            .andExpect(jsonPath("$.data.role.snstvViewYn").value("Y"));
        mvc.perform(get("/api/common/roles/R-001")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menuAuths[0].downloadYn").value("Y"));
        assertThat(data.histories).extracting(h -> h.item()).containsExactly("DATA_SCOPE_CD","SNSTV_VIEW_YN","DNLD_YN");
        assertThat(data.histories.get(0).before()).isEqualTo("PART");
        assertThat(data.histories.get(0).after()).isEqualTo("ALL");
        assertThat(data.accesses).containsExactly("R-001");
        verify(management).lockManagement();
    }
    @ParameterizedTest @ValueSource(strings={"SELF","unknown",""})
    void invalidDataScopeDoesNotWrite(String scope) throws Exception {
        mvc.perform(put("/api/common/roles/ADMIN").contentType("application/json").content(update("ADMIN",scope,"Y").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("dataScopeCd"));
        verify(mapper,never()).updateRole(anyString(),any());
    }
    @Test void businessAdminRecoveryFlagCannotBeRemoved() throws Exception {
        var body=update("ADMIN","PART","N"); ((ObjectNode)body.withArray("menuAuths").get(0)).put("updateYn","N");
        mvc.perform(put("/api/common/roles/ADMIN").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        verify(mapper,never()).updateRole(anyString(),any());
        assertThat(data.roles.get("ADMIN").dataScopeCd).isEqualTo("ALL");
        assertThat(data.menus.get("ADMIN").get(0).updateYn).isEqualTo("Y");
        verify(mapper, never()).insertRoleAccess(anyString());
    }
    @ParameterizedTest @ValueSource(strings={"omitted","duplicate","unknown","invalidFlag"})
    void fullMatrixRejectsEveryBypass(String kind) throws Exception {
        var body=update("ADMIN","ALL","Y"); var rows=body.withArray("menuAuths");
        switch(kind) {
            case "omitted" -> rows.remove(0);
            case "duplicate" -> rows.add(rows.get(0).deepCopy());
            case "unknown" -> ((ObjectNode)rows.get(0)).put("menuId","unknown");
            default -> ((ObjectNode)rows.get(0)).put("readYn","X");
        }
        mvc.perform(put("/api/common/roles/ADMIN").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("menuAuths"));
        verify(mapper,never()).updateRole(anyString(),any());
    }
    @Test void labelsAndRenameInputsAreNotAccepted() throws Exception {
        var body=update("ADMIN","ALL","Y"); body.put("roleName","rename");
        mvc.perform(put("/api/common/roles/ADMIN").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        body.remove("roleName"); ((ObjectNode)body.withArray("menuAuths").get(0)).put("menuName","label");
        mvc.perform(put("/api/common/roles/ADMIN").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
    }
    @Test void leaderMayReadCloseButNeverConfirmIt() throws Exception {
        var body=update("SALES_LDR","PART","N"); var close=(ObjectNode)body.withArray("menuAuths").get(1); close.put("readYn","Y");
        mvc.perform(put("/api/common/roles/SALES_LDR").contentType("application/json").content(body.toString())).andExpect(status().isOk());
        close.put("cnfmYn","Y");
        mvc.perform(put("/api/common/roles/SALES_LDR").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        assertThat(data.menus.get("SALES_LDR").get(1).readYn).isEqualTo("Y");
        assertThat(data.menus.get("SALES_LDR").get(1).cnfmYn).isEqualTo("N");
    }
    @Test void impactIsReadOnlyAndConfirmationMustMatchFinalDistinctTargets() throws Exception {
        data.removable("single","PART"); data.removable("single","PART");
        mvc.perform(get("/api/common/roles/R-001/change-impact").param("dataScopeCd","ALL"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.headquartersUserCount").value(1));
        var body=update("R-001","ALL","N");
        mvc.perform(put("/api/common/roles/R-001").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("confirmedAffectedUserIds"));
        verify(mapper,never()).updateRole(anyString(),any());
        assertThat(data.scopes).hasSize(2);
        body.withArray("confirmedAffectedUserIds").add("single");
        mvc.perform(put("/api/common/roles/R-001").contentType("application/json").content(body.toString())).andExpect(status().isOk());
        assertThat(data.scopes).isEmpty();
        assertThat(data.histories.stream().filter(h -> h.table().equals("TB_SY_USER_SCOPE"))).hasSize(2);
    }
    @Test void extraConfirmationTargetIsRejectedBeforeMutation() throws Exception {
        var body=update("R-001","ALL","Y"); body.withArray("confirmedAffectedUserIds").add("stale");
        mvc.perform(put("/api/common/roles/R-001").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("confirmedAffectedUserIds"));
        verify(mapper,never()).updateRole(anyString(),any());
    }
    @Test void impactRequiresValidScopeQuery() throws Exception {
        mvc.perform(get("/api/common/roles/ADMIN/change-impact")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/common/roles/ADMIN/change-impact").param("dataScopeCd","SELF"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("dataScopeCd"));
    }
    @Test void invalidJsonAndWrongMethodsUseSharedEnvelope() throws Exception {
        mvc.perform(post("/api/common/roles").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(delete("/api/common/roles/ADMIN")).andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.success").value(false));
        mvc.perform(put("/api/common/roles/R-001/stop")).andExpect(status().isMethodNotAllowed());
    }
}
