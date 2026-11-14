package com.company.common.controller;

import com.company.common.dto.ClientContracts.*;
import com.company.common.dto.UserContracts.History;
import com.company.common.mapper.*;
import com.company.common.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static com.company.common.contracts.OpenApiResponseContract.response;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonClientController.class)
@ContextConfiguration(classes = {CommonClientController.class, CommonClientService.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class, CurrentBusinessActor.class, BusinessCalendar.class})
class CommonClientControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ClientMapper mapper;
    @MockBean UserMapper management;
    @MockBean PaymentAccountCipher cipher;
    @MockBean PlatformTransactionManager transactions;
    Map<String, ClientRows.Client> rows;
    List<History> histories;
    Set<String> projects;
    Set<String> people;
    int sequence;

    @BeforeEach void setup() throws Exception {
        String contract = new org.springframework.core.io.ClassPathResource("contracts/openapi.yaml")
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(contract).contains("operationId: getCommonClients", "operationId: postCommonClient",
            "operationId: putCommonClient", "operationId: putCommonClientUseStatus",
            "operationId: getCommonClientAssignments", "operationId: getCommonClientChangeHistory",
            "operationId: getCommonClientPaymentAccount");
        rows = new LinkedHashMap<>(); histories = new ArrayList<>(); projects = new HashSet<>(); people = new HashSet<>(); sequence = 0;
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(mapper.codeExists(anyString(), anyString())).thenReturn(true);
        when(cipher.encrypt(nullable(String.class))).thenAnswer(call -> call.getArgument(0) == null ? null : "encrypted:" + call.getArgument(0));
        when(cipher.decrypt(nullable(String.class))).thenAnswer(call -> call.getArgument(0) == null ? null : ((String) call.getArgument(0)).substring(10));
        when(cipher.lastFour(anyString())).thenAnswer(call -> { String value = ((String)call.getArgument(0)).replace("-", ""); return value.substring(value.length() - 4); });
        when(mapper.selectClient(anyString(), any())).thenAnswer(call -> rows.get(call.getArgument(0)));
        when(mapper.lockClient(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        when(mapper.selectBusinessOwner(anyString())).thenAnswer(call -> rows.values().stream()
            .filter(row -> row.businessNumber.equals(call.getArgument(0))).map(row -> row.clientName).findFirst().orElse(null));
        when(mapper.selectSameNameNumbers(anyString(), nullable(String.class))).thenAnswer(call -> rows.values().stream()
            .filter(row -> row.clientName.equals(call.getArgument(0)) && !row.clientId.equals(call.getArgument(1)))
            .map(row -> row.businessNumber).toList());
        when(mapper.selectClients(any(), any())).thenAnswer(call -> {
            Filter filter = call.getArgument(0);
            return rows.values().stream().filter(row -> row.useYn.equals(filter.useYn()))
                .filter(row -> filter.clientTypeCode() == null || row.clientTypeCode.equals(filter.clientTypeCode()) || "BOTH".equals(row.clientTypeCode))
                .filter(row -> filter.keyword() == null || row.clientName.contains(filter.keyword()) || row.businessNumber.contains(filter.keyword())).toList();
        });
        when(mapper.insertClient(any())).thenAnswer(call -> { Write write = call.getArgument(0); store(write, "Y"); return 1; });
        when(mapper.updateClient(any())).thenAnswer(call -> { Write write = call.getArgument(0); store(write, rows.get(write.clientId()).useYn); return 1; });
        when(mapper.updateUseStatus(any())).thenAnswer(call -> {
            StateWrite write = call.getArgument(0); var row = rows.get(write.version().clientId());
            row.useYn = write.useYn(); row.modifiedAt = Instant.parse("2030-01-01T00:00:00Z").plusSeconds(++sequence); return 1;
        });
        when(mapper.hasOngoingProject(anyString())).thenAnswer(call -> projects.contains(call.getArgument(0)));
        when(mapper.hasAffiliatedPerson(anyString())).thenAnswer(call -> people.contains(call.getArgument(0)));
        doAnswer(call -> { histories.add(call.getArgument(0)); return null; }).when(management).insertHistory(any());
        when(mapper.selectChanges(anyString())).thenAnswer(call -> histories.stream().filter(history -> history.key().equals(call.getArgument(0)))
            .map(history -> { var row = new ClientRows.Change(); row.fieldName = history.item(); row.beforeValue = history.before(); row.afterValue = history.after();
                row.changedBy = "admin"; row.changedAt = Instant.parse("2030-01-01T00:00:00Z"); return row; }).toList());
    }
    private void store(Write write, String use) {
        var row = new ClientRows.Client(); var input = write.input(); var old = rows.get(write.clientId());
        row.clientId = write.clientId(); row.businessNumber = old == null ? input.businessNumber() : old.businessNumber;
        row.clientTypeCode = input.clientTypeCode(); row.clientName = input.clientName(); row.contactName = input.contactName();
        row.contactPhone = input.contactPhone(); row.taxEmail = input.taxEmail(); row.representativeName = input.representativeName();
        row.industryCode = input.industryCode(); row.bankCode = input.bankCode(); row.bankName = "은행";
        row.accountHolder = input.accountHolder(); row.encryptedAccount = write.encryptedAccount(); row.useYn = use;
        row.modifiedAt = old == null ? null : Instant.parse("2030-01-01T00:00:00Z").plusSeconds(++sequence);
        rows.put(row.clientId, row);
    }
    private ObjectNode input(String type) {
        return json.createObjectNode().put("clientTypeCode", type).put("clientName", "거래처")
            .put("businessNumber", "123-45-67890").put("taxEmail", "client@example.test")
            .put("bankCode", "004").put("accountNumber", "12345678").put("accountHolder", "예금주");
    }
    private String create(String type) throws Exception {
        var result = mvc.perform(post("/api/common/clients").contentType("application/json").content(input(type).toString()))
            .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).at("/data/client/clientId").asText();
    }
    private ObjectNode update(String type) {
        var body = input(type); body.remove("businessNumber"); body.putNull("modifiedAt"); return body;
    }
    private org.springframework.test.web.servlet.ResultActions putClient(String id, ObjectNode body) throws Exception {
        return mvc.perform(put("/api/common/clients/" + id).contentType("application/json").content(body.toString()));
    }
    private ObjectNode state(String use, String... ids) {
        var body = json.createObjectNode().put("useYn", use); var items = body.putArray("items");
        for (String id : ids) { var item = items.addObject().put("clientId", id); var row = rows.get(id);
            if (row == null || row.modifiedAt == null) item.putNull("modifiedAt"); else item.put("modifiedAt", row.modifiedAt.toString()); }
        return body;
    }
    private org.springframework.test.web.servlet.ResultActions stateRequest(ObjectNode body) throws Exception {
        return mvc.perform(put("/api/common/clients/use-status").contentType("application/json").content(body.toString()));
    }
    @ParameterizedTest @ValueSource(strings = {"CUST", "VNDR", "BOTH"})
    void anonymousCreateListsActiveClientAndCategoryFields(String type) throws Exception {
        String id = create(type); assertThat(id).matches("[0-9a-f]{20}");
        mvc.perform(get("/api/common/clients")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].clientId").value(id))
            .andExpect(jsonPath("$.data.items[0].useYn").value("Y"));
        var row = rows.get(id); assertThat(row.taxEmail).isEqualTo(type.equals("VNDR") ? null : "client@example.test");
        assertThat(row.encryptedAccount).isEqualTo(type.equals("CUST") ? null : "encrypted:12345678");
        assertThat(histories).containsExactly(new History("TB_BS_CLNT", id, "NEW", "신규 등록", null, "신규 등록"));
    }
    @Test
    @DisplayName("auth happy side-effect: 신규 거래처와 TB_SY_CHG_HIST 등록 이력을 함께 저장한다")
    void newHistoryHasAdminAndTimestampWithoutAuthentication() throws Exception {
        var response = mvc.perform(post("/api/common/clients").contentType("application/json")
            .content(input("CUST").toString())).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.client.useYn").value("Y")).andReturn();
        String id = json.readTree(response.getResponse().getContentAsString()).at("/data/client/clientId").asText();
        verify(mapper).insertClient(argThat(write -> write.clientId().equals(id) && write.actor().equals("admin")));
        verify(management).insertHistory(new History("TB_BS_CLNT", id, "NEW", "신규 등록", null, "신규 등록"));
        mvc.perform(get("/api/common/clients/{clientId}/change-history", id))
            .andExpect(response("GET", "/api/common/clients/{clientId}/change-history"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].fieldName").value("신규 등록"))
            .andExpect(jsonPath("$.data.items[0].changedBy").value("admin")).andExpect(jsonPath("$.data.items[0].changedAt").exists());
    }
    @Test void sevenDigitAccountWritesNothing() throws Exception {
        mvc.perform(post("/api/common/clients").contentType("application/json").content(input("VNDR").put("accountNumber", "1234567").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("accountNumber"));
        verify(mapper, never()).insertClient(any()); assertThat(histories).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {"clientName", "taxEmail"})
    void requiredCustomerFieldRejectsWithoutWrites(String field) throws Exception {
        var body = input("CUST"); body.remove(field);
        mvc.perform(post("/api/common/clients").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value(field)); assertThat(rows).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {"bankCode", "accountNumber", "accountHolder"})
    void requiredVendorFieldRejectsWithoutWrites(String field) throws Exception {
        var body = input("VNDR"); body.remove(field);
        mvc.perform(post("/api/common/clients").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value(field)); assertThat(rows).isEmpty();
    }
    @Test void duplicateBusinessNumberPreservesExistingClient() throws Exception {
        create("CUST"); mvc.perform(post("/api/common/clients").contentType("application/json").content(input("CUST").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("이미 등록된 사업자번호입니다 (거래처)"));
        assertThat(rows).hasSize(1); assertThat(histories).hasSize(1);
    }
    @Test void sameNameDifferentBusinessNumberWarnsButCreates() throws Exception {
        create("CUST"); mvc.perform(post("/api/common/clients").contentType("application/json").content(input("CUST").put("businessNumber", "987-65-43210").toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.warnings[0]").value("같은 상호의 거래처가 이미 있습니다(사업자번호 123-45-67890)")); assertThat(rows).hasSize(2);
    }
    @Test
    @DisplayName("auth happy side-effect: 담당자 수정은 TB_SY_CHG_HIST에 이전/이후 한 줄을 기록한다")
    void latestNullableVersionUpdatesAndAuditsOnlyChangedContact() throws Exception {
        String id = create("BOTH"); var body = update("BOTH").put("contactName", "새 담당자");
        mvc.perform(put("/api/common/clients/{clientId}", id).contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.client.contactName").value("새 담당자"));
        mvc.perform(get("/api/common/clients/" + id)).andExpect(jsonPath("$.data.modifiedAt").value(rows.get(id).modifiedAt.toString()));
        assertThat(histories.subList(1, histories.size())).containsExactly(new History("TB_BS_CLNT", id, "UPD", "담당자", null, "새 담당자"));
    }
    @Test void immutableNumberRejectsBeforeAnyWrite() throws Exception {
        String id = create("CUST"); putClient(id, update("CUST").put("businessNumber", "987-65-43210")).andExpect(status().isBadRequest());
        assertThat(rows.get(id).businessNumber).isEqualTo("123-45-67890"); assertThat(histories).hasSize(1);
    }
    @Test void fiftyOneCharacterNameRejectedAndFiftyAccepted() throws Exception {
        String id = create("CUST");
        mvc.perform(put("/api/common/clients/{clientId}", id).contentType("application/json")
            .content(update("CUST").put("clientName", "가".repeat(51)).toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("clientName")); assertThat(rows.get(id).clientName).isEqualTo("거래처");
        putClient(id, update("CUST").put("clientName", "가".repeat(50))).andExpect(status().isOk());
    }
    @Test void missingVersionIsInvalidButNullDoesNotBypassConflict() throws Exception {
        String id = create("CUST"); var body = update("CUST"); body.remove("modifiedAt"); putClient(id, body).andExpect(status().isBadRequest());
        rows.get(id).modifiedAt = Instant.parse("2030-01-01T00:00:00Z");
        putClient(id, update("CUST")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."));
        verify(mapper, never()).updateClient(any()); assertThat(histories).hasSize(1);
    }
    @Test void noOpDoesNotGenerateHistoryOrNewToken() throws Exception {
        String id = create("BOTH"); putClient(id, update("BOTH")).andExpect(status().isOk()); verify(mapper, never()).updateClient(any()); assertThat(histories).hasSize(1);
    }
    @Test
    @DisplayName("auth happy side-effect: 중지 거래처는 활성 선택 원천에서 제외하고 관계는 보존한다")
    void stopThenAnonymousResumeAndFilterReadback() throws Exception {
        String id = create("CUST");
        mvc.perform(put("/api/common/clients/use-status").contentType("application/json").content(state("N", id).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed[0]").value(id));
        mvc.perform(get("/api/common/clients")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        assertThat(projects).isEmpty(); assertThat(people).isEmpty();
        verify(mapper).updateUseStatus(argThat(write -> write.version().clientId().equals(id) && write.useYn().equals("N")));
        mvc.perform(get("/api/common/clients").param("useYn", "N")).andExpect(jsonPath("$.data.items[0].clientId").value(id));
        stateRequest(state("Y", id)).andExpect(status().isOk()); assertThat(rows.get(id).useYn).isEqualTo("Y");
    }
    @Test void emptyBatchRejectsWithoutStatusWrite() throws Exception {
        mvc.perform(put("/api/common/clients/use-status").contentType("application/json").content(state("N").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("items"));
        verify(mapper, never()).updateUseStatus(any()); verifyNoInteractions(management);
    }
    @Test void duplicateBatchIdentityRejects() throws Exception {
        String id = create("CUST"); stateRequest(state("N", id, id)).andExpect(status().isBadRequest()); verify(mapper, never()).updateUseStatus(any());
    }
    @Test void ongoingCustomerProjectProtectsClient() throws Exception {
        String id = create("CUST"); projects.add(id); stateRequest(state("N", id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.failed[0].reason").value("진행 프로젝트가 있어 중지할 수 없습니다."));
        assertThat(rows.get(id).useYn).isEqualTo("Y"); assertThat(projects).containsExactly(id); assertThat(histories).hasSize(1);
    }
    @Test void vendorAffiliationProtectsEvenWithoutTodayAssignment() throws Exception {
        String id = create("VNDR"); people.add(id); stateRequest(state("N", id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.failed.length()").value(1));
        assertThat(rows.get(id).useYn).isEqualTo("Y"); verify(mapper, never()).selectAssignments(anyString(), any());
    }
    @Test void stalePreflightPrecedesEveryWriteEvenWithBlockedAndAllowedRows() throws Exception {
        String id = create("CUST"); var other = input("CUST").put("businessNumber", "987-65-43210");
        var result = mvc.perform(post("/api/common/clients").contentType("application/json").content(other.toString())).andReturn();
        String second = json.readTree(result.getResponse().getContentAsString()).at("/data/client/clientId").asText();
        var body = state("N", id, second); projects.add(id); rows.get(second).modifiedAt = Instant.parse("2030-01-01T00:00:00Z");
        stateRequest(body).andExpect(status().isConflict()); verify(mapper, never()).updateUseStatus(any()); assertThat(histories).hasSize(2);
    }
    @Test void relationshipFailuresPermitOtherRows() throws Exception {
        String id = create("CUST"); var second = new ClientRows.Client(); second.clientId="second"; second.clientTypeCode="CUST"; second.useYn="Y"; rows.put("second", second); projects.add(id);
        stateRequest(state("N", id, "second")).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed[0]").value("second")).andExpect(jsonPath("$.data.failed.length()").value(1));
        assertThat(rows.get(id).useYn).isEqualTo("Y"); assertThat(second.useYn).isEqualTo("N");
    }
    @Test void fullAccountOnlyOnDedicatedReadAndHistoryIsMasked() throws Exception {
        String id = create("VNDR"); mvc.perform(get("/api/common/clients/" + id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accountNumber").doesNotExist()).andExpect(jsonPath("$.data.accountHolder").doesNotExist())
            .andExpect(jsonPath("$.data.paymentAccountSummary").value("은행 5678"));
        mvc.perform(get("/api/common/clients/{clientId}/payment-account", id)).andExpect(status().isOk())
            .andExpect(response("GET", "/api/common/clients/{clientId}/payment-account"))
            .andExpect(jsonPath("$.data.bankCode").value("004"))
            .andExpect(jsonPath("$.data.accountHolder").value("예금주"))
            .andExpect(jsonPath("$.data.accountNumber").value("12345678"));
        putClient(id, update("VNDR").put("accountNumber", "87654321")).andExpect(status().isOk());
        assertThat(histories.toString()).doesNotContain("12345678", "87654321", "예금주");
    }
    @Test void assignmentsRetainSeparateRowsAndEmptyResult() throws Exception {
        String id = create("CUST"); var a = new ClientRows.Assignment(); a.projectId="p"; a.projectName="프로젝트"; a.personName="사람"; a.companyName="소속"; a.jobName="개발";
        when(mapper.selectAssignments(eq(id), any())).thenReturn(List.of(a, a));
        mvc.perform(get("/api/common/clients/{clientId}/assignments", id)).andExpect(status().isOk())
            .andExpect(response("GET", "/api/common/clients/{clientId}/assignments"))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].projectId").value("p"))
            .andExpect(jsonPath("$.data.items[0].personName").value("사람"))
            .andExpect(jsonPath("$.data.items[1].companyName").value("소속"));
        verify(mapper).selectAssignments(eq(id), any());
    }
    @Test void missingClientAssignmentsReturnsFieldErrorWithoutQueryingAssignments() throws Exception {
        mvc.perform(get("/api/common/clients/missing/assignments")).andExpect(status().isBadRequest())
            .andExpect(response("GET", "/api/common/clients/{clientId}/assignments"))
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.errors[0].field").value("clientId"));
        verify(mapper, never()).selectAssignments(anyString(), any());
    }
    @Test void missingClientHistoryReturnsFieldErrorWithoutQueryingHistory() throws Exception {
        mvc.perform(get("/api/common/clients/missing/change-history")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("clientId"));
        verify(mapper, never()).selectChanges(anyString());
    }
    @Test void missingClientPaymentAccountDoesNotDecrypt() throws Exception {
        mvc.perform(get("/api/common/clients/missing/payment-account")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("clientId"));
        verify(cipher, never()).decrypt(any());
    }
    @ParameterizedTest @ValueSource(strings = {"", "/assignments", "/change-history", "/payment-account"})
    void missingClientUsesContractError(String suffix) throws Exception {
        mvc.perform(get("/api/common/clients/missing" + suffix)).andExpect(status().isBadRequest());
    }
}
