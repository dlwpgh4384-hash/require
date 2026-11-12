package com.company.common.controller;

import com.company.common.service.CommonUserService;
import com.company.common.service.UserFailure;
import com.company.common.service.TemporaryUserPassword;
import com.company.common.mapper.UserMapper;
import com.company.common.service.UserTestData;

import com.company.common.mapper.UserOptionsMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.company.common.service.UserTestData.TOKEN;

@WebMvcTest(CommonUserController.class)
@ContextConfiguration(classes = {CommonUserController.class, CommonUserService.class,
    TemporaryUserPassword.class, UserExceptionHandler.class, com.company.common.service.ExcelExportService.class, ApiExceptionHandler.class})
class CommonUserControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean UserMapper mapper;
    @MockBean UserOptionsMapper options;
    @SpyBean TemporaryUserPassword passwords;
    UserTestData data;
    @BeforeEach void setup() throws Exception {
        data = new UserTestData(mapper, options);
        String contract = new ClassPathResource("contracts/openapi.yaml")
            .getContentAsString(StandardCharsets.UTF_8);
        assertThat(contract).contains("/api/common/users:", "operationId: getCommonUsers",
            "operationId: putCommonUsers", "operationId: putCommonUser",
            "operationId: getCommonUserAccessLogs", "operationId: getCommonUserChangeImpact",
            "operationId: putCommonUserStop", "operationId: putCommonUserResume",
            "operationId: putCommonUserUnlock", "operationId: putCommonUserPasswordReset");
    }
    private String create(String id, List<String> roles) throws Exception {
        return json.writeValueAsString(Map.of("userId", id, "name", "합성 계정", "email", id + "@example.invalid",
            "roleCds", roles, "headquarters", false, "projectIds", List.of()));
    }
    private String update(List<String> roles, List<String> projects, List<String> confirmed) throws Exception {
        return json.writeValueAsString(Map.of("name", "합성 계정", "email", "test@example.invalid", "roleCds", roles,
            "headquarters", false, "projectIds", projects,
            "confirmedProjectIds", confirmed, "modDttm", "2026-01-02T03:04:05.123456Z"));
    }
    private String version() { return "{\"modDttm\":\"2026-01-02T03:04:05.123456Z\"}"; }
    @Test void registrationAnonymousPersistsAccountRolesTemporaryHashAndTargetAccess() throws Exception {
        mvc.perform(post("/api/common/users").contentType("application/json").content(create("test", List.of("EXEC"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.account.userId").value("test"))
            .andExpect(jsonPath("$.data.account.roleCds[0]").value("EXEC"))
            .andExpect(jsonPath("$.data.account.password").doesNotExist())
            .andExpect(jsonPath("$.data.account.pwdHash").doesNotExist());
        mvc.perform(get("/api/common/users/test")).andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value("test@example.invalid"));
        assertThat(data.roles.get("test")).containsExactly("EXEC");
        assertThat(data.hashes.get("test")).matches("\\$2[aby]\\$10\\$.{53}");
        assertThat(data.temporary).contains("test"); assertThat(data.accesses).containsExactly("test:USER_REG");
    }
    @ParameterizedTest @ValueSource(strings = {"ABCd", "abc", "abcdefghijklmnopqrstu"})
    void invalidIdIsFieldErrorWithoutInsert(String id) throws Exception {
        mvc.perform(post("/api/common/users").contentType("application/json").content(create(id, List.of("EXEC"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("userId"));
        verify(mapper, never()).insertUser(any(), anyString());
    }
    @ParameterizedTest @ValueSource(strings = {"abcd", "abcdefghijklmnopqrst"})
    void idBoundariesAccepted(String id) throws Exception {
        mvc.perform(post("/api/common/users").contentType("application/json").content(create(id, List.of("EXEC"))))
            .andExpect(status().isOk());
    }
    @Test void emptyRoleIs400AndNoInsert() throws Exception {
        mvc.perform(post("/api/common/users").contentType("application/json").content(create("test", List.of())))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("roleCds"));
        verify(mapper, never()).insertUser(any(), anyString());
    }
    @Test void businessDuplicateCreatePreservesAssignmentsAndDoesNotInvokeSmtpDeliveryBoundary() throws Exception {
        data.add("test", "USE", List.of("PM")); data.projects.get("test").add("project-a");
        mvc.perform(post("/api/common/users").contentType("application/json").content(create("test", List.of("EXEC"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("userId"));
        assertThat(data.roles.get("test")).containsExactly("PM"); assertThat(data.projects.get("test")).containsExactly("project-a");
        verify(mapper, never()).insertUser(any(), anyString());
        verifyNoInteractions(passwords);
    }
    @ParameterizedTest @ValueSource(strings = {"name", "email"})
    void blankAndOverlongRegistrationFieldsAreRejected(String field) throws Exception {
        var body = json.readTree(create("test", List.of("EXEC")));
        for (String value : List.of("", "a".repeat(field.equals("name") ? 51 : 101))) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) body).put(field, value);
            mvc.perform(post("/api/common/users").contentType("application/json").content(body.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
        }
    }
    @Test void invalidEmailIsFieldError() throws Exception {
        String body = create("test", List.of("EXEC")).replace("test@example.invalid", "not-an-email");
        mvc.perform(post("/api/common/users").contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("email"));
    }
    @Test void sideEffectRoleScopeUpdateAnonymousPersistsModDttmAndTbSyAccsLog() throws Exception {
        data.add("test", "USE", List.of("PM"));
        mvc.perform(put("/api/common/users/test").contentType("application/json")
            .content(update(List.of("PM"), List.of("project-a", "project-b"), List.of())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.account.projectIds.length()").value(2));
        mvc.perform(get("/api/common/users/test")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projectIds.length()").value(2))
            .andExpect(jsonPath("$.data.modDttm").value("2026-01-02T03:04:05.123457Z"));
        assertThat(data.projects.get("test")).containsExactly("project-a", "project-b");
        assertThat(data.accesses).containsExactly("test:AUTH_CHG");
        verify(mapper).touch("test", TOKEN);
        verify(mapper).insertAccess(eq("test"), eq("AUTH_CHG"), contains("test"));
    }
    @Test void roleChangeEmptyArrayDoesNotDeleteRoles() throws Exception {
        data.add("test", "USE", List.of("PM"));
        mvc.perform(put("/api/common/users/test").contentType("application/json").content(update(List.of(), List.of(), List.of())))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("roleCds"));
        assertThat(data.roles.get("test")).containsExactly("PM");
    }
    @Test void lockedAdminCannotReplaceLastUseAdmin() throws Exception {
        data.add("lockadmin", "LOCK", List.of("ADMIN"));
        mvc.perform(put("/api/common/users/admin").contentType("application/json").content(update(List.of("EXEC"), List.of(), List.of())))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("관리자가 0명이 되므로 이 계정의 시스템 관리자 역할을 뺄 수 없습니다."));
        assertThat(data.roles.get("admin")).containsExactly("ADMIN");
    }
    @Test void currentOrFutureLeaderRoleRemovalIncludesPartName() throws Exception {
        data.add("lead", "USE", List.of("SALES_LDR")); data.leaders.put("lead", List.of("합성 영업 파트"));
        mvc.perform(put("/api/common/users/lead").contentType("application/json").content(update(List.of("EXEC"), List.of(), List.of())))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("합성 영업 파트")));
        assertThat(data.roles.get("lead")).containsExactly("SALES_LDR");
    }
    @Test void staleUpdateDoesNotAlterLatestRolesOrScopes() throws Exception {
        data.add("test", "USE", List.of("PM")).modDttm = TOKEN.plusNanos(1000);
        data.projects.get("test").add("project-a");
        mvc.perform(put("/api/common/users/test").contentType("application/json").content(update(List.of("EXEC"), List.of(), List.of("project-a"))))
            .andExpect(status().isConflict());
        assertThat(data.projects.get("test")).containsExactly("project-a"); assertThat(data.roles.get("test")).containsExactly("PM");
        assertThat(data.accounts.get("test").modDttm).isEqualTo(TOKEN.plusNanos(1000));
    }
    /** 2026-11-12 사용자 결정: 담당 프로젝트 줄은 PM 역할과 「담당 프로젝트」 범위 역할이 둘 다 없어질 때만 지운다. */
    @Test void projectRowsStayWhileAnotherProjectRoleRemainsAndGoOnlyWhenBothAreRemoved() throws Exception {
        data.add("test", "USE", List.of("PM", "PROJECT_READER")); data.projects.get("test").add("project-a");
        mvc.perform(get("/api/common/users/change-impact").param("userIds", "test").param("action", "ROLE_CHANGE").param("roleCds", "PROJECT_READER"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.projectCount").value(0));
        mvc.perform(get("/api/common/users/change-impact").param("userIds", "test").param("action", "ROLE_CHANGE").param("roleCds", "EXEC"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.projectCount").value(1));
        mvc.perform(put("/api/common/users/test").contentType("application/json").content(update(List.of("EXEC"), List.of(), List.of())))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("confirmedProjectIds"));
        assertThat(data.projects.get("test")).containsExactly("project-a");
        mvc.perform(put("/api/common/users/test").contentType("application/json").content(update(List.of("EXEC"), List.of(), List.of("project-a"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.account.projectIds").isEmpty());
        assertThat(data.history).anyMatch(item -> item.item().equals("담당 프로젝트") && item.after().equals("–"));
    }
    @ParameterizedTest @ValueSource(strings = {"password-reset", "resume", "unlock"})
    void existingMutationsRequireVersionPropertyButAllowNull(String operation) throws Exception {
        String state = operation.equals("resume") ? "STOP" : "LOCK";
        data.add("test", state, List.of("EXEC")).modDttm = null;
        mvc.perform(post("/api/common/users/test/" + operation).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("modDttm"));
        assertThat(data.accounts.get("test").userStatCd).isEqualTo(state);
        mvc.perform(post("/api/common/users/test/" + operation).contentType("application/json").content("{\"modDttm\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.modDttm").value("2026-01-02T03:04:05.123456Z"));
    }
    @ParameterizedTest @ValueSource(strings = {"password-reset", "resume", "unlock"})
    void staleVersionWinsBeforeStateValidationAndHasNoSideEffects(String operation) throws Exception {
        data.add("test", "USE", List.of("EXEC")).modDttm = TOKEN.plusNanos(1000);
        mvc.perform(post("/api/common/users/test/" + operation).contentType("application/json").content(version()))
            .andExpect(status().isConflict());
        assertThat(data.accounts.get("test").modDttm).isEqualTo(TOKEN.plusNanos(1000));
        verify(mapper, never()).changeState(anyString(), any(), anyString(), anyBoolean());
        verify(mapper, never()).resetPassword(anyString(), any(), anyString());
        assertThat(data.accesses).isEmpty(); assertThat(data.history).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {"USE", "LOCK", "STOP"})
    void resetPreservesStateAndFailureCountAndDoesNotLogAccess(String state) throws Exception {
        data.add("test", state, List.of("EXEC")); String oldHash = new TemporaryUserPassword().hashAndDeliver("test@example.invalid");
        data.hashes.put("test", oldHash); int count = data.failures.get("test");
        mvc.perform(post("/api/common/users/test/password-reset").contentType("application/json").content(version()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.tempPwdYn").value("Y"))
            .andExpect(jsonPath("$.data.password").doesNotExist());
        assertThat(data.hashes.get("test")).isNotEqualTo(oldHash).hasSize(60);
        assertThat(data.accounts.get("test").userStatCd).isEqualTo(state);
        assertThat(data.failures.get("test")).isEqualTo(count); assertThat(data.accesses).isEmpty();
        mvc.perform(get("/api/common/users/test")).andExpect(jsonPath("$.data.modDttm").value("2026-01-02T03:04:05.123457Z"));
    }
    @Test void authSideEffectUnlockAnonymousResetsFailuresAndModDttmWithoutAccessLog() throws Exception {
        data.add("test", "LOCK", List.of("EXEC"));
        mvc.perform(post("/api/common/users/test/unlock").contentType("application/json").content(version()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.pwdErrCnt").value(0))
            .andExpect(jsonPath("$.data.userStatCd").value("USE"))
            .andExpect(jsonPath("$.data.modDttm").value("2026-01-02T03:04:05.123457Z"));
        assertThat(data.accounts.get("test").userStatCd).isEqualTo("USE"); assertThat(data.failures.get("test")).isZero();
        assertThat(data.accesses).isEmpty();
        verify(mapper).changeState("test", TOKEN, "USE", true);
    }
    @ParameterizedTest @ValueSource(strings = {"resume", "unlock"})
    void wrongStateIs400Unchanged(String operation) throws Exception {
        data.add("test", "USE", List.of("EXEC"));
        mvc.perform(post("/api/common/users/test/" + operation).contentType("application/json").content(version()))
            .andExpect(status().isBadRequest());
        assertThat(data.accounts.get("test").modDttm).isEqualTo(TOKEN);
    }
    @Test void authSideEffectResumeAnonymousRecordsUserStatCdAndModDttmButNeverRestoresProjects() throws Exception {
        data.add("test", "STOP", List.of("PM"));
        mvc.perform(post("/api/common/users/test/resume").contentType("application/json").content(version()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.userStatCd").value("USE"))
            .andExpect(jsonPath("$.data.projectIds").isEmpty())
            .andExpect(jsonPath("$.data.modDttm").value("2026-01-02T03:04:05.123457Z"));
        assertThat(data.history).anyMatch(item -> item.type().equals("RESUME") && item.key().equals("test"));
        verify(mapper).changeState("test", TOKEN, "USE", false);
        verify(mapper, never()).insertScope(anyString(), anyString(), anyString());
    }
    @Test void authBusinessSideEffectBulkStopPartialSuccessPreservesProtectedUserStatCd() throws Exception {
        data.add("good", "USE", List.of("EXEC")); data.add("lead", "USE", List.of("SALES_LDR"));
        data.leaders.put("lead", List.of("예약 파트")); data.add("stale", "USE", List.of("EXEC")).modDttm = TOKEN.plusNanos(1000);
        String body = "{\"users\":[{\"userId\":\"good\",\"modDttm\":null},{\"userId\":\"lead\",\"modDttm\":null},{\"userId\":\"admin\",\"modDttm\":null},{\"userId\":\"stale\",\"modDttm\":null}],\"confirmedProjectIds\":[]}";
        body = body.replace("\"modDttm\":null", "\"modDttm\":\"2026-01-02T03:04:05.123456Z\"");
        mvc.perform(post("/api/common/users/stop").contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.processedUserIds[0]").value("good"))
            .andExpect(jsonPath("$.data.unprocessedCount").value(3));
        assertThat(data.accounts.get("good").userStatCd).isEqualTo("STOP");
        assertThat(data.accounts.get("lead").userStatCd).isEqualTo("USE");
        assertThat(data.accounts.get("admin").userStatCd).isEqualTo("USE");
        assertThat(data.history).anyMatch(item -> item.type().equals("STOP") && item.key().equals("good"));
        verify(mapper).changeState("good", TOKEN, "STOP", false);
        verify(mapper, never()).changeState(eq("lead"), any(), anyString(), anyBoolean());
        verify(mapper, never()).changeState(eq("admin"), any(), anyString(), anyBoolean());
    }
    @Test void validationEmptyStopIs400BeforeAnyWrites() throws Exception {
        mvc.perform(post("/api/common/users/stop").contentType("application/json").content("{\"users\":[],\"confirmedProjectIds\":[]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("users"));
        verify(mapper, never()).changeState(anyString(), any(), anyString(), anyBoolean());
    }
    @Test void unknownUpdatePropertyIs400RatherThanEditableIdentity() throws Exception {
        String body = update(List.of("EXEC"), List.of(), List.of());
        body = body.substring(0, body.length()-1) + ",\"userId\":\"changed\"}";
        mvc.perform(put("/api/common/users/test").contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("userId"));
        verify(mapper, never()).touch(anyString(), any());
    }
    @Test void listFilterAndOutOfRangeAreSuccessfulWithRealTotal() throws Exception {
        data.add("test", "LOCK", List.of("PM"));
        mvc.perform(get("/api/common/users").param("roleCd", "PM").param("userStatCd", "LOCK").param("page", "2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(1));
    }
    @Test void logsReadAndMissingAccountUseContractEnvelope() throws Exception {
        data.add("test", "USE", List.of("EXEC"));
        mvc.perform(get("/api/common/users/test/access-logs")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/common/users/test/change-history")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        mvc.perform(get("/api/common/users/none")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("userId"));
    }
    @Test void authSideEffectPasswordResetAnonymousChangesModDttmWithoutTbSyAccsLog() throws Exception {
        data.add("test", "USE", List.of("EXEC"));
        mvc.perform(post("/api/common/users/test/password-reset").contentType("application/json").content(version()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userId").value("test"))
            .andExpect(jsonPath("$.data.tempPwdYn").value("Y"))
            .andExpect(jsonPath("$.data.modDttm").value("2026-01-02T03:04:05.123457Z"))
            .andExpect(jsonPath("$.data.password").doesNotExist());
        assertThat(data.hashes.get("test")).matches("\\$2[aby]\\$10\\$.{53}");
        verify(mapper).resetPassword(eq("test"), eq(TOKEN), anyString());
        verify(mapper, never()).insertAccess(anyString(), anyString(), anyString());
    }
    @Test void validationPasswordResetRequiresModDttmAndPreservesHash() throws Exception {
        data.add("test", "USE", List.of("EXEC"));
        String before = passwords.hashAndDeliver("test@example.invalid");
        data.hashes.put("test", before);
        mvc.perform(post("/api/common/users/test/password-reset").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("modDttm"));
        assertThat(data.hashes.get("test")).isEqualTo(before);
        verify(mapper, never()).resetPassword(anyString(), any(), anyString());
        assertThat(data.accesses).isEmpty();
    }
    @Test void businessPasswordResetStaleModDttmPreservesHashAndTemporaryFlag() throws Exception {
        data.add("test", "USE", List.of("EXEC")).modDttm = TOKEN.plusNanos(1000);
        String before = passwords.hashAndDeliver("test@example.invalid");
        data.hashes.put("test", before);
        mvc.perform(post("/api/common/users/test/password-reset").contentType("application/json").content(version()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(UserFailure.conflict().getMessage()));
        assertThat(data.hashes.get("test")).isEqualTo(before);
        assertThat(data.temporary).doesNotContain("test");
        assertThat(data.accounts.get("test").modDttm).isEqualTo(TOKEN.plusNanos(1000));
        verify(mapper, never()).resetPassword(anyString(), any(), anyString());
    }
    @Test void validationUnlockRequiresModDttmAndPreservesFailures() throws Exception {
        data.add("test", "LOCK", List.of("EXEC"));
        mvc.perform(post("/api/common/users/test/unlock").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("modDttm"));
        assertThat(data.accounts.get("test").userStatCd).isEqualTo("LOCK");
        assertThat(data.failures.get("test")).isEqualTo(5);
        verify(mapper, never()).changeState(anyString(), any(), anyString(), anyBoolean());
    }
    @Test void validationResumeRequiresModDttmWithoutStateOrHistoryWrites() throws Exception {
        data.add("test", "STOP", List.of("PM"));
        mvc.perform(post("/api/common/users/test/resume").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("modDttm"));
        assertThat(data.accounts.get("test").userStatCd).isEqualTo("STOP");
        verify(mapper, never()).changeState(anyString(), any(), anyString(), anyBoolean());
        assertThat(data.history).isEmpty();
    }
    @Test void businessResumeStaleModDttmPreservesStoppedAccountAndScopes() throws Exception {
        data.add("test", "STOP", List.of("PM")).modDttm = TOKEN.plusNanos(1000);
        mvc.perform(post("/api/common/users/test/resume").contentType("application/json").content(version()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(UserFailure.conflict().getMessage()));
        assertThat(data.accounts.get("test").userStatCd).isEqualTo("STOP");
        assertThat(data.accounts.get("test").modDttm).isEqualTo(TOKEN.plusNanos(1000));
        assertThat(data.projects.get("test")).isEmpty();
        verify(mapper, never()).changeState(anyString(), any(), anyString(), anyBoolean());
        assertThat(data.history).isEmpty();
    }
    @Test void businessUnlockStaleModDttmPreservesLockAndFailureCount() throws Exception {
        data.add("test", "LOCK", List.of("EXEC")).modDttm = TOKEN.plusNanos(1000);
        mvc.perform(post("/api/common/users/test/unlock").contentType("application/json").content(version()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(UserFailure.conflict().getMessage()));
        assertThat(data.accounts.get("test").userStatCd).isEqualTo("LOCK");
        assertThat(data.failures.get("test")).isEqualTo(5);
        verify(mapper, never()).changeState(anyString(), any(), anyString(), anyBoolean());
        assertThat(data.accesses).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {"page=0", "pageSize=10", "userStatCd=OTHER", "page=bad"})
    void invalidListQueryIs400(String query) throws Exception {
        mvc.perform(get("/api/common/users?" + query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
    }
    @Test void excelDownloadReturnsXlsxWithKoreanFileNameAndVisibleColumns() throws Exception {
        var result = mvc.perform(get("/api/common/users/excel").param("columns", "name,state"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
            .andReturn();
        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body[0]).isEqualTo((byte) 'P'); assertThat(body[1]).isEqualTo((byte) 'K');
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(body))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("다운로드: admin");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("이름");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("상태");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("사용");
        }
        assertThat(data.accesses).anyMatch(item -> item.contains("DNLD"));
    }
    @Test void excelAndListRejectUnknownColumnsAndSortKeys() throws Exception {
        mvc.perform(get("/api/common/users/excel").param("columns", "password")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/common/users").param("sort", "pwd_hash")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/common/users").param("sort", "name").param("dir", "sideways")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/common/users").param("sort", "last").param("dir", "desc")).andExpect(status().isOk());
    }
    /** 화면 전용 처리기가 공통 처리기보다 먼저 받는다(2026-11-12 공통 오류 처리기). */
    @Test void screenSpecificHandlerWinsOverTheCommonOne() throws Exception {
        mvc.perform(get("/api/common/users").param("page", "abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("조회 조건 형식을 확인해 주세요."));
        mvc.perform(delete("/api/common/users"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.success").value(false));
    }
}
