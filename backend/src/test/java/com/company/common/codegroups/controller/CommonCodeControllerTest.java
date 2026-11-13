package com.company.common.codegroups.controller;

import com.company.common.codegroups.mapper.*;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.controller.ApiExceptionHandler;
import com.company.common.controller.CodeExceptionHandler;
import com.company.common.dto.UserContracts.History;
import com.company.common.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonCodeController.class)
@ContextConfiguration(classes = {CommonCodeController.class, CommonCodeService.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class})
class CommonCodeControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean CodeGroupMapper mapper;
    @MockBean UserMapper management;
    final LinkedHashMap<String, CodeRows.Group> groups = new LinkedHashMap<>();
    final LinkedHashMap<String, CodeRows.Code> codes = new LinkedHashMap<>();
    final List<History> history = new ArrayList<>();
    @BeforeEach void setup() throws Exception {
        String contract = new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8);
        assertThat(contract).contains("operationId: getCommonCodeGroups", "operationId: postCommonCodeGroup",
            "operationId: getCommonCodes", "operationId: postCommonCode", "operationId: putCommonCodes",
            "operationId: postCommonCodeDelete");
        groups.clear(); codes.clear(); history.clear();
        addGroup("CUSTOM", false); addGroup("SYSTEM", true); addGroup("ALWNC", false);
        groups.get("ALWNC").valCodeYn = "Y";
        for (String id : List.of("JOB", "SKILL", "PROJ_STEP", "BANK", "RSM_LABEL", "RSM_JOB_KW")) addGroup(id, false);
        addCode("CUSTOM", "ONE"); addCode("CUSTOM", "TWO"); addCode("SYSTEM", "FIXED");
        when(mapper.selectGroups()).thenAnswer(call -> {
            groups.values().forEach(g -> g.codeCount = (int) codes.values().stream()
                .filter(c -> c.groupId.equals(g.groupId) && c.delYn.equals("N")).count());
            return new ArrayList<>(groups.values());
        });
        when(mapper.selectCodes(anyString())).thenAnswer(call -> codes.values().stream()
            .filter(c -> c.groupId.equals(call.getArgument(0)) && c.delYn.equals("N")).toList());
        when(mapper.selectCode(anyString(), anyString())).thenAnswer(call -> codes.get(call.getArgument(0) + ":" + call.getArgument(1)));
        when(mapper.nextGroupOrder()).thenReturn(90);
        when(mapper.nextCodeOrder(anyString())).thenReturn(9);
        when(mapper.insertGroup(any())).thenAnswer(call -> { var g = (CodeRows.Group) call.getArgument(0); groups.put(g.groupId,g); return 1; });
        when(mapper.insertCode(any())).thenAnswer(call -> { var c = (CodeRows.Code) call.getArgument(0); codes.put(c.groupId+":"+c.code,c); return 1; });
        when(mapper.updateCode(any())).thenAnswer(call -> { var c = (CodeRows.Code) call.getArgument(0); codes.put(c.groupId+":"+c.code,c); return 1; });
        when(mapper.deleteCode(anyString(),anyString())).thenAnswer(call -> { codes.get(call.getArgument(0)+":"+call.getArgument(1)).delYn="Y"; return 1; });
        when(mapper.relationExists(anyString())).thenReturn(true);
        doAnswer(call -> { history.add(call.getArgument(0)); return null; }).when(management).insertHistory(any());
    }
    private void addGroup(String id, boolean system) {
        var g = new CodeRows.Group(); g.groupId=id; g.groupName=id; g.sysCodeYn=system ? "Y":"N"; g.valCodeYn="N";
        groups.put(id,g);
    }
    private void addCode(String group, String code) {
        var c = new CodeRows.Code(); c.groupId=group; c.code=code; c.codeName=code; c.sortOrd=1; c.useYn="Y"; c.delYn="N";
        codes.put(group+":"+code,c);
    }
    private ObjectNode item(String code) {
        return json.createObjectNode().put("code",code).put("codeName","새 이름").put("sortOrd",1).put("useYn","Y");
    }
    private ObjectNode body(ObjectNode... rows) {
        var body=json.createObjectNode(); var items=body.putArray("items"); for(var row:rows) items.add(row); return body;
    }
    private org.springframework.test.web.servlet.ResultActions create(String group, ObjectNode body) throws Exception {
        return mvc.perform(post("/api/common/code-groups/{groupId}/codes",group).contentType("application/json").content(body.toString()));
    }
    private org.springframework.test.web.servlet.ResultActions save(String group, ObjectNode body) throws Exception {
        return mvc.perform(put("/api/common/code-groups/{groupId}/codes",group).contentType("application/json").content(body.toString()));
    }
    private org.springframework.test.web.servlet.ResultActions deleteCode(String group, String code) throws Exception {
        return mvc.perform(post("/api/common/code-groups/{groupId}/codes/{code}/delete",group,code));
    }
    private void unchanged() {
        verify(mapper,never()).insertCode(any()); verify(mapper,never()).updateCode(any());
        verify(mapper,never()).deleteCode(anyString(),anyString()); assertThat(history).isEmpty();
    }
    @Test void anonymousGroupCreateReadbackAndHistory() throws Exception {
        mvc.perform(post("/api/common/code-groups").contentType("application/json")
            .content(json.createObjectNode().put("groupId","NEW_GROUP").put("groupName","새 그룹").toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.sysCodeYn").value("N"))
            .andExpect(jsonPath("$.data.codeCount").value(0));
        mvc.perform(get("/api/common/code-groups")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[?(@.groupId == 'NEW_GROUP')].groupName").value(org.hamcrest.Matchers.contains("새 그룹")));
        assertThat(history).containsExactly(new History("TB_SY_CODE_GRP","NEW_GROUP","NEW","CODE_GRP_NM",null,"새 그룹"));
        verify(management).lockManagement();
    }
    @ParameterizedTest @ValueSource(strings={"1BAD","ABCDEFGHIJKLMNOPQRSTUVWXYZABCDE","lower",""})
    void groupIdentityValidation(String id) throws Exception {
        mvc.perform(post("/api/common/code-groups").contentType("application/json")
            .content(json.createObjectNode().put("groupId",id).put("groupName","이름").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("groupId"));
        verify(mapper,never()).insertGroup(any()); assertThat(history).isEmpty();
    }
    @Test void thirtyCharacterGroupAndTwentyCharacterCodeAccepted() throws Exception {
        String id="A".repeat(30);
        mvc.perform(post("/api/common/code-groups").contentType("application/json")
            .content(json.createObjectNode().put("groupId",id).put("groupName","이름").toString())).andExpect(status().isOk());
        create(id,item("a".repeat(20))).andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value("A".repeat(20)));
    }
    @Test void duplicateGroupCannotRenameIt() throws Exception {
        mvc.perform(post("/api/common/code-groups").contentType("application/json")
            .content("{\"groupId\":\"CUSTOM\",\"groupName\":\"변조\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors[0].field").value("groupId"));
        assertThat(groups.get("CUSTOM").groupName).isEqualTo("CUSTOM"); verify(mapper,never()).insertGroup(any());
        verify(management, never()).insertHistory(any());
    }
    @Test void anonymousGroupSideEffectWritesChangeHistory() throws Exception {
        mvc.perform(post("/api/common/code-groups").contentType("application/json")
            .content("{\"groupId\":\"NEW_GROUP\",\"groupName\":\"새 그룹\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.groupId").value("NEW_GROUP"))
            .andExpect(jsonPath("$.data.codeCount").value(0));
        verify(mapper).insertGroup(argThat(row -> row.groupId.equals("NEW_GROUP") && row.sysCodeYn.equals("N")));
        verify(management).insertHistory(new History("TB_SY_CODE_GRP", "NEW_GROUP", "NEW", "CODE_GRP_NM", null, "새 그룹"));
    }
    @Test void codeCreateValidationRejectsMissingNameWithoutSideEffects() throws Exception {
        mvc.perform(post("/api/common/code-groups/CUSTOM/codes").contentType("application/json")
            .content("{\"code\":\"NEW\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("codeName"));
        unchanged();
    }
    @Test void codeCreateBusinessProtectsSystemGroup() throws Exception {
        mvc.perform(post("/api/common/code-groups/SYSTEM/codes").contentType("application/json")
            .content(item("NEW").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("code"))
            .andExpect(jsonPath("$.message").value("시스템 코드는 추가하거나 삭제할 수 없습니다."));
        unchanged();
    }
    @Test void anonymousDeleteHappySideEffectPreservesCodePrimaryKeyAndChangesDelYn() throws Exception {
        mvc.perform(post("/api/common/code-groups/CUSTOM/codes/ONE/delete"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.groupId").value("CUSTOM"))
            .andExpect(jsonPath("$.data.code").value("ONE"))
            .andExpect(jsonPath("$.data.delYn").value("Y"));
        assertThat(codes).containsKey("CUSTOM:ONE");
        assertThat(codes.get("CUSTOM:ONE").delYn).isEqualTo("Y");
        verify(mapper).deleteCode("CUSTOM", "ONE");
        verify(management).insertHistory(new History("TB_SY_CODE", "CUSTOM:ONE", "DEL", "DEL_YN", "N", "Y"));
        mvc.perform(get("/api/common/code-groups/CUSTOM/codes"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].code").value("TWO"));
    }
    @Test void deleteValidationRejectsMalformedCodeAndKeepsDelYn() throws Exception {
        mvc.perform(post("/api/common/code-groups/CUSTOM/codes/bad-code/delete"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("code"));
        assertThat(codes.get("CUSTOM:ONE").delYn).isEqualTo("N");
        unchanged();
    }
    @Test void deleteBusinessRejectsReferencedCodeAndKeepsDelYn() throws Exception {
        when(mapper.mappedReferenceExists(any())).thenReturn(true);
        mvc.perform(post("/api/common/code-groups/CUSTOM/codes/ONE/delete"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("사용된 코드라 삭제할 수 없습니다. 사용을 끄세요."));
        assertThat(codes.get("CUSTOM:ONE").delYn).isEqualTo("N");
        unchanged();
    }
    @Test void anonymousUpdateHappySideEffectKeepsDelYnAndAuditsName() throws Exception {
        mvc.perform(put("/api/common/code-groups/SYSTEM/codes").contentType("application/json")
            .content(body(item("FIXED")).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].codeName").value("새 이름"));
        assertThat(codes.get("SYSTEM:FIXED").delYn).isEqualTo("N");
        verify(mapper).updateCode(argThat(row -> row.code.equals("FIXED") && row.delYn.equals("N")));
        verify(management).insertHistory(new History("TB_SY_CODE", "SYSTEM:FIXED", "UPD", "CODE_NM", "FIXED", "새 이름"));
    }
    @Test void updateBusinessRejectsSystemUseChangeAndKeepsDelYn() throws Exception {
        mvc.perform(put("/api/common/code-groups/SYSTEM/codes").contentType("application/json")
            .content(body(item("FIXED").put("useYn", "N")).toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("useYn"));
        assertThat(codes.get("SYSTEM:FIXED").useYn).isEqualTo("Y");
        assertThat(codes.get("SYSTEM:FIXED").delYn).isEqualTo("N");
        unchanged();
    }
    @Test void anonymousCodeNormalizesAndAuditsThenCanBeDeletedDespiteOwnAudit() throws Exception {
        mvc.perform(post("/api/common/code-groups/CUSTOM/codes").contentType("application/json")
            .content(item("new_code").toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value("NEW_CODE"));
        mvc.perform(get("/api/common/code-groups/CUSTOM/codes")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[2].codeName").value("새 이름"));
        assertThat(history.get(0).type()).isEqualTo("NEW");
        deleteCode("CUSTOM","NEW_CODE").andExpect(status().isOk()).andExpect(jsonPath("$.data.delYn").value("Y"));
        assertThat(codes.get("CUSTOM:NEW_CODE").delYn).isEqualTo("Y");
        assertThat(history.get(1)).isEqualTo(new History("TB_SY_CODE","CUSTOM:NEW_CODE","DEL","DEL_YN","N","Y"));
        create("CUSTOM",item("new_code")).andExpect(status().isBadRequest());
        assertThat(codes).containsKey("CUSTOM:NEW_CODE");
        mvc.perform(get("/api/common/code-groups/CUSTOM/codes")).andExpect(jsonPath("$.data.items.length()").value(2));
    }
    @ParameterizedTest @ValueSource(strings={"AAAAAAAAAAAAAAAAAAAAA","bad-code",""})
    void invalidCodeCreatesNothing(String code) throws Exception {
        create("CUSTOM",item(code)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("code")); unchanged();
    }
    @Test void nameRequiredOnCreate() throws Exception {
        var input=item("NEW"); input.remove("codeName");
        create("CUSTOM",input).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("codeName")); unchanged();
    }
    @ParameterizedTest @ValueSource(strings={"JOB","SKILL","PROJ_STEP","BANK","RSM_LABEL","RSM_JOB_KW"})
    void additionalValuesRequired(String group) throws Exception {
        create(group,item("NEW")).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void targetFieldAllowList() throws Exception {
        create("RSM_LABEL",item("NEW").put("targetField","OTHER")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("targetField")); unchanged();
    }
    @Test void jobAdditionalValuesRoundTrip() throws Exception {
        create("JOB",item("new").put("relatedJobs","ONE,TWO").put("estimateUnitPrice",1234567.125))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.estimateUnitPrice").value(1234567.125));
        save("JOB",body(item("NEW").put("relatedJobs","TWO").put("estimateUnitPrice",4567.25))).andExpect(status().isOk());
        mvc.perform(get("/api/common/code-groups/JOB/codes")).andExpect(jsonPath("$.data.items[0].relatedJobs").value("TWO"));
    }
    @Test void allowanceMissingOrClearedValueIsZero() throws Exception {
        create("ALWNC",item("NEW")).andExpect(status().isOk()).andExpect(jsonPath("$.data.codeVal").value(0));
        save("ALWNC",body(item("NEW").put("codeVal",123.4567))).andExpect(status().isOk());
        save("ALWNC",body(item("NEW").putNull("codeVal"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].codeVal").value(0));
    }
    @Test void systemRegistrationRejected() throws Exception {
        create("SYSTEM",item("NEW")).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void systemDeletionRejected() throws Exception {
        deleteCode("SYSTEM","FIXED").andExpect(status().isBadRequest()); unchanged();
    }
    @Test void invalidDeleteIdentityRejectedBeforeMutation() throws Exception {
        deleteCode("CUSTOM","bad-code").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("code")); unchanged();
    }
    @ParameterizedTest @ValueSource(strings={"current","cancelled","deleted","closed","history","additional","map"})
    void anyHistoricalUsagePreventsDelete(String kind) throws Exception {
        addCode("JOB","TEST");
        if(kind.equals("history")) when(mapper.historicalReferenceExists(any(),any())).thenReturn(true);
        else if(kind.equals("additional")) when(mapper.additionalReferenceExists(any())).thenReturn(true);
        else if(kind.equals("map")) when(mapper.mappedReferenceExists(any())).thenReturn(true);
        else when(mapper.referenceExists(any(),any())).thenReturn(true);
        deleteCode("JOB","TEST").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("사용된 코드라 삭제할 수 없습니다. 사용을 끄세요."));
        assertThat(codes.get("JOB:TEST").delYn).isEqualTo("N"); unchanged();
    }
    @Test void absentOriginalRelationsContributeZeroNotFailure() throws Exception {
        addCode("JOB","TEST"); when(mapper.relationExists(anyString())).thenReturn(false);
        deleteCode("JOB","TEST").andExpect(status().isOk());
        verify(mapper,never()).referenceExists(any(),any());
    }
    @Test void batchNameAndUsageUpdatesAreOneSaveAndKeepInactiveInManagement() throws Exception {
        save("CUSTOM",body(item("ONE").put("useYn","N"),item("TWO").put("useYn","Y")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].useYn").value("N"));
        assertThat(history).contains(new History("TB_SY_CODE","CUSTOM:ONE","UPD","CODE_NM","ONE","새 이름"));
        assertThat(history).contains(new History("TB_SY_CODE","CUSTOM:ONE","UPD","USE_YN","Y","N"));
        verify(management).lockManagement();
    }
    @Test void anonymousSystemNameOnlyAccepted() throws Exception {
        save("SYSTEM",body(item("FIXED"))).andExpect(status().isOk());
        assertThat(history).containsExactly(new History("TB_SY_CODE","SYSTEM:FIXED","UPD","CODE_NM","FIXED","새 이름"));
    }
    @ParameterizedTest @ValueSource(strings={"useYn","sortOrd","codeVal","unitNm"})
    void systemOtherChangesRejected(String field) throws Exception {
        var row=item("FIXED");
        switch(field) { case "useYn" -> row.put(field,"N"); case "sortOrd" -> row.put(field,2);
            case "codeVal" -> row.put(field,3); default -> row.put(field,"원"); }
        save("SYSTEM",body(row)).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void batchValidatesAllRowsBeforeAnyWriteOrHistory() throws Exception {
        mvc.perform(put("/api/common/code-groups/CUSTOM/codes").contentType("application/json")
            .content(body(item("ONE"), item("TWO").put("codeName", " ")).toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("codeName")); unchanged();
    }
    @Test void putDoesNotUpsertMissingIdentity() throws Exception {
        save("CUSTOM",body(item("MISSING"))).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void duplicateBatchIdentityRejected() throws Exception {
        save("CUSTOM",body(item("ONE"),item("ONE"))).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void missingRequiredUpdateIdentityFieldsRejected() throws Exception {
        var row=item("ONE"); row.remove("sortOrd");
        save("CUSTOM",body(row)).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void unknownHiddenFieldsRejected() throws Exception {
        create("CUSTOM",item("NEW").put("delYn","Y")).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void decimalPrecisionRejected() throws Exception {
        create("ALWNC",item("NEW").put("codeVal",0.00001)).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void failuresDoNotExposePersistenceDetails() throws Exception {
        when(mapper.selectGroups()).thenThrow(new IllegalStateException("private SQL"));
        mvc.perform(get("/api/common/code-groups")).andExpect(status().isInternalServerError())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
    }
}
