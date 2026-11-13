package com.company.common.controller;

import com.company.common.dto.CodeMapContracts.Item;
import com.company.common.dto.UserContracts.History;
import com.company.common.mapper.CodeMapRow;
import com.company.common.mapper.CommonCodeMapMapper;
import com.company.common.mapper.UserMapper;
import com.company.common.service.CommonCodeMapService;
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

@WebMvcTest(CommonCodeMapController.class)
@ContextConfiguration(classes = {CommonCodeMapController.class, CommonCodeMapService.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class})
class CommonCodeMapControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean CommonCodeMapMapper mapper;
    @MockBean UserMapper management;
    final LinkedHashMap<Long, CodeMapRow> rows = new LinkedHashMap<>();
    final List<History> histories = new ArrayList<>();
    long sequence;

    @BeforeEach void setup() throws Exception {
        String contract = new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8);
        assertThat(contract).contains("/api/common/code-maps:", "/api/common/code-maps/delete:",
            "operationId: getCommonCodeMaps", "operationId: postCommonCodeMap", "operationId: postCommonCodeMapDelete");
        rows.clear();
        histories.clear();
        sequence = 0;
        when(mapper.selectMaps()).thenAnswer(call -> new ArrayList<>(rows.values()));
        when(mapper.selectIdentity(any())).thenAnswer(call -> {
            Item item = call.getArgument(0);
            return rows.values().stream().filter(row -> row.excelText.equals(item.excelText())
                && row.groupId.equals(item.groupId())).findFirst().orElse(null);
        });
        when(mapper.selectableCode(any())).thenReturn(true);
        when(mapper.insertMap(any())).thenAnswer(call -> {
            CodeMapRow row = call.getArgument(0);
            row.mapId = ++sequence;
            rows.put(row.mapId, row);
            return 1;
        });
        when(mapper.deleteMap(anyLong())).thenAnswer(call -> rows.remove(call.getArgument(0)) == null ? 0 : 1);
        doAnswer(call -> { histories.add(call.getArgument(0)); return null; }).when(management).insertHistory(any());
    }

    private ObjectNode item() {
        return json.createObjectNode().put("excelText", "기타").put("groupId", "EMP_TYPE").put("code", "FULL");
    }
    private ObjectNode deletion(ObjectNode... items) {
        var input = json.createObjectNode();
        var array = input.putArray("items");
        for (var value : items) array.add(value);
        return input;
    }
    private org.springframework.test.web.servlet.ResultActions create(ObjectNode input) throws Exception {
        return mvc.perform(post("/api/common/code-maps").contentType("application/json").content(input.toString()));
    }
    private org.springframework.test.web.servlet.ResultActions remove(ObjectNode input) throws Exception {
        return mvc.perform(post("/api/common/code-maps/delete").contentType("application/json").content(input.toString()));
    }
    private void noWrites() {
        verify(mapper, never()).insertMap(any());
        verify(mapper, never()).deleteMap(anyLong());
        assertThat(histories).isEmpty();
    }

    @Test void initialListIsEmpty() throws Exception {
        mvc.perform(get("/api/common/code-maps")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty());
        verifyNoInteractions(management);
    }
    @Test void anonymousCreateReturnsExactlyThreeFieldsAndPersistsHistory() throws Exception {
        mvc.perform(post("/api/common/code-maps").contentType("application/json").content(item().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data.excelText").value("기타"));
        mvc.perform(get("/api/common/code-maps")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].excelText").value("기타"))
            .andExpect(jsonPath("$.data.items[0].groupId").value("EMP_TYPE"))
            .andExpect(jsonPath("$.data.items[0].code").value("FULL"));
        assertThat(histories).containsExactly(new History("TB_SY_CODE_MAP", "1", "NEW",
            "엑셀 표기 대응", null, "기타 | EMP_TYPE | FULL"));
        var order = inOrder(management, mapper);
        order.verify(management).lockManagement();
        order.verify(mapper).selectIdentity(any());
    }
    @ParameterizedTest @ValueSource(strings = {"excelText", "groupId", "code"})
    void missingFieldIs400AndCreatesNothing(String field) throws Exception {
        var input = item(); input.remove(field);
        create(input).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
        noWrites();
    }
    @ParameterizedTest @ValueSource(strings = {"excelText", "groupId", "code"})
    void nullFieldIs400(String field) throws Exception {
        create(item().putNull(field)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value(field));
        noWrites();
    }
    @ParameterizedTest @ValueSource(strings = {"excelText", "groupId", "code"})
    void whitespaceIsRejected(String field) throws Exception {
        create(item().put(field, "\u3000\u00a0")).andExpect(status().isBadRequest());
        noWrites();
    }
    @ParameterizedTest @ValueSource(strings = {"excelText", "groupId", "code"})
    void overflowIsRejectedBeforeSql(String field) throws Exception {
        int limit = field.equals("excelText") ? 100 : field.equals("groupId") ? 30 : 20;
        create(item().put(field, "😀".repeat(limit + 1))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value(field));
        noWrites();
    }
    @Test void sameTextAndGroupRejectsAnotherCodeAndKeepsOriginal() throws Exception {
        create(item()).andExpect(status().isOk());
        mvc.perform(post("/api/common/code-maps").contentType("application/json")
            .content(item().put("code", "OTHER").toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("excelText"));
        assertThat(rows.values()).extracting(row -> row.code).containsExactly("FULL");
        assertThat(histories).hasSize(1);
    }
    @Test void sameTextInDifferentGroupIsAllowed() throws Exception {
        create(item()).andExpect(status().isOk());
        create(item().put("groupId", "OTHER")).andExpect(status().isOk());
        assertThat(rows).hasSize(2);
    }
    @Test void unavailableTargetCodeIsAFieldError() throws Exception {
        when(mapper.selectableCode(any())).thenReturn(false);
        create(item()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("code"));
        noWrites();
    }
    @Test void anonymousDeletionPhysicallyRemovesEvenUsedMappingAndKeepsPreviousHistory() throws Exception {
        create(item()).andExpect(status().isOk());
        mvc.perform(post("/api/common/code-maps/delete").contentType("application/json")
            .content(deletion(item()).toString())).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.deleted[0].code").value("FULL"));
        mvc.perform(get("/api/common/code-maps")).andExpect(jsonPath("$.data.items").isEmpty());
        assertThat(rows).isEmpty();
        assertThat(histories.get(1)).isEqualTo(new History("TB_SY_CODE_MAP", "1", "DEL",
            "엑셀 표기 대응", "기타 | EMP_TYPE | FULL", null));
        verify(mapper).deleteMap(1L);
        verify(mapper, times(1)).selectableCode(any());
    }
    @Test void emptyDeleteItemsIs400() throws Exception {
        mvc.perform(post("/api/common/code-maps/delete").contentType("application/json")
            .content("{\"items\":[]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("items"));
        noWrites();
    }
    @ParameterizedTest @ValueSource(strings = {"excelText", "groupId", "code"})
    void invalidDeleteItemPreservesExistingMap(String field) throws Exception {
        create(item()).andExpect(status().isOk());
        var invalid = item(); invalid.remove(field);
        remove(deletion(invalid)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value(field));
        assertThat(rows).hasSize(1);
        assertThat(histories).hasSize(1);
        verify(mapper, never()).deleteMap(anyLong());
    }
    @Test void staleCodeDoesNotDeleteReplacement() throws Exception {
        create(item()).andExpect(status().isOk());
        mvc.perform(post("/api/common/code-maps/delete").contentType("application/json")
            .content(deletion(item().put("code", "OLD")).toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("items"));
        verify(mapper, never()).deleteMap(anyLong());
        assertThat(rows).hasSize(1);
    }
    @Test void allDeleteSelectionsAreCheckedBeforeAnyWrite() throws Exception {
        create(item()).andExpect(status().isOk());
        remove(deletion(item(), item().put("excelText", "없는 대응"))).andExpect(status().isBadRequest());
        verify(mapper, never()).deleteMap(anyLong());
        assertThat(histories).hasSize(1);
    }
    @Test void duplicateSelectionIsRejected() throws Exception {
        remove(deletion(item(), item())).andExpect(status().isBadRequest());
        noWrites();
    }
    @Test void publicMapIdIsNotAccepted() throws Exception {
        create(item().put("mapId", 9)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("mapId"));
        noWrites();
    }
    @Test void malformedJsonUsesCommonEnvelope() throws Exception {
        mvc.perform(post("/api/common/code-maps").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        noWrites();
    }
    @Test void databaseReadFailureIsNotAnEmptySuccess() throws Exception {
        when(mapper.selectMaps()).thenThrow(new IllegalStateException("private detail"));
        mvc.perform(get("/api/common/code-maps")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false));
    }
}
