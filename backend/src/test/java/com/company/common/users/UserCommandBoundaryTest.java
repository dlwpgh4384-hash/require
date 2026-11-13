package com.company.common.users;

import com.company.common.controller.ApiExceptionHandler;
import com.company.common.controller.CommonUserController;
import com.company.common.controller.UserExceptionHandler;
import com.company.common.mapper.UserMapper;
import com.company.common.mapper.UserOptionsMapper;
import com.company.common.service.CommonUserService;
import com.company.common.service.ExcelExportService;
import com.company.common.service.TemporaryUserPassword;
import com.company.common.service.UserTestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
import static com.company.common.service.UserTestData.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonUserController.class)
@ContextConfiguration(classes = {CommonUserController.class,
    CommonUserService.class, TemporaryUserPassword.class,
    UserExceptionHandler.class, ExcelExportService.class, ApiExceptionHandler.class})
class UserCommandBoundaryTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean UserMapper mapper;
    @MockBean UserOptionsMapper options;
    UserTestData data;

    @BeforeEach void setup() throws Exception {
        data = new UserTestData(mapper, options);
        var contract = new ClassPathResource("contracts/openapi.yaml")
            .getContentAsString(StandardCharsets.UTF_8);
        assertThat(contract).contains("operationId: putCommonUsers", "operationId: putCommonUserStop",
            "operationId: putCommonUserPasswordReset", "operationId: putCommonUserResume",
            "operationId: putCommonUserUnlock");
    }

    private String userSql() throws Exception {
        return new ClassPathResource("mapper/UserMapper.xml").getContentAsString(StandardCharsets.UTF_8);
    }

    private String version() throws Exception {
        return json.writeValueAsString(Map.of("modDttm", TOKEN.toString()));
    }

    private String stopBody(List<String> ids, List<String> projects) throws Exception {
        return json.writeValueAsString(Map.of("users", ids.stream()
            .map(id -> Map.of("userId", id, "modDttm", TOKEN.toString())).toList(),
            "confirmedProjectIds", projects));
    }

    @Test void stopPostDoesNotFallThroughToAnAccountUpdate() throws Exception {
        data.add("member", "USE", List.of("EXEC"));
        mvc.perform(post("/api/common/users/stop").contentType("application/json")
                .content(stopBody(List.of("member"), List.of())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.processedUserIds[0]").value("member"));
        assertThat(data.accounts.get("member").userStatCd).isEqualTo("STOP");
        verify(mapper, never()).updateProfile(anyString(), anyString(), anyString());
    }

    @Test void anonymousConfirmedPmStopAndResumeKeepDeletedAssignmentsDeleted() throws Exception {
        data.add("firstpm", "USE", List.of("PM"));
        data.projects.get("firstpm").add("project-a");
        data.add("secondpm", "LOCK", List.of("PM"));
        data.projects.get("secondpm").add("project-b");
        mvc.perform(get("/api/common/users/change-impact")
                .param("userIds", "firstpm,secondpm").param("action", "STOP"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projectCount").value(2));
        mvc.perform(post("/api/common/users/stop").contentType("application/json")
                .content(stopBody(List.of("firstpm", "secondpm"), List.of("project-a", "project-b"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.processedUserIds.length()").value(2))
            .andExpect(jsonPath("$.data.unprocessedCount").value(0));
        assertThat(data.projects.get("firstpm")).isEmpty();
        assertThat(data.projects.get("secondpm")).isEmpty();
        assertThat(data.history).filteredOn(item -> item.item().equals("담당 프로젝트"))
            .hasSize(2).allSatisfy(item -> {
                assertThat(item.before()).isNotBlank();
                assertThat(item.after()).isEqualTo("–");
            });
        var stopped = mvc.perform(get("/api/common/users/firstpm"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userStatCd").value("STOP")).andReturn();
        var token = json.readTree(stopped.getResponse().getContentAsString()).path("data").get("modDttm");
        mvc.perform(post("/api/common/users/firstpm/resume").contentType("application/json")
                .content(json.writeValueAsString(Map.of("modDttm", token.asText()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userStatCd").value("USE"))
            .andExpect(jsonPath("$.data.projectIds").isEmpty());
        assertThat(data.history).filteredOn(item -> item.type().equals("RESUME")).hasSize(1);
        assertThat(data.accesses).isEmpty();
        verify(mapper, never()).insertScope(anyString(), anyString(), anyString());
    }

    @Test void headquartersRegistrationAllowsMultipleResponsibleUsers() throws Exception {
        for (String id : List.of("leaderone", "leadertwo")) {
            mvc.perform(post("/api/common/users").contentType("application/json")
                    .content(json.writeValueAsString(Map.of("userId", id, "name", "담당자",
                        "email", id + "@example.invalid", "roleCds", List.of("SALES_LDR"),
                        "headquarters", true, "projectIds", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.headquarters").value(true))
                .andExpect(jsonPath("$.data.account.password").doesNotExist())
                .andExpect(jsonPath("$.data.account.pwdHash").doesNotExist());
        }
        assertThat(data.headquarters).containsExactlyInAnyOrder("leaderone", "leadertwo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"password-reset", "resume", "unlock"})
    void malformedVersionIsRejectedBeforeAnyPersistence(String operation) throws Exception {
        data.add("member", "LOCK", List.of("EXEC"));
        mvc.perform(post("/api/common/users/member/" + operation).contentType("application/json")
                .content("{\"modDttm\":\"not-a-date\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("modDttm"));
        verifyNoInteractions(mapper, options);
        assertThat(data.accounts.get("member").modDttm).isEqualTo(TOKEN);
        assertThat(data.failures.get("member")).isEqualTo(5);
    }

    @Test void stalePmStopKeepsScopesAndHistoryUntouched() throws Exception {
        data.add("stalepm", "USE", List.of("PM")).modDttm = TOKEN.plusNanos(1000);
        data.projects.get("stalepm").add("project-a");
        mvc.perform(post("/api/common/users/stop").contentType("application/json")
                .content(stopBody(List.of("stalepm"), List.of("project-a"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false));
        assertThat(data.projects.get("stalepm")).containsExactly("project-a");
        assertThat(data.accounts.get("stalepm").userStatCd).isEqualTo("USE");
        assertThat(data.history).isEmpty();
        verify(mapper, never()).deleteProject(anyString(), anyString());
    }

    @Test void infrastructureFailureDoesNotLeakDatabaseDetails() throws Exception {
        data.add("member", "LOCK", List.of("EXEC"));
        // Override the stateful fixture without executing its existing answer during stubbing.
        doThrow(new IllegalStateException("SQLSTATE 23505 TB_SY_USER pwd_hash internal detail"))
            .when(mapper).changeState("member", TOKEN, "USE", true);
        assertThat(data.accounts.get("member").modDttm).isEqualTo(TOKEN);
        assertThat(data.accounts.get("member").userStatCd).isEqualTo("LOCK");
        var result = mvc.perform(post("/api/common/users/member/unlock").contentType("application/json")
                .content(version()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors").isEmpty()).andReturn();
        assertThat(result.getResponse().getContentAsString())
            .doesNotContain("SQLSTATE", "TB_SY_USER", "pwd_hash", "internal detail");
        verify(mapper).changeState("member", TOKEN, "USE", true);
        assertThat(data.accounts.get("member").modDttm).isEqualTo(TOKEN);
        assertThat(data.accounts.get("member").userStatCd).isEqualTo("LOCK");
        assertThat(data.failures.get("member")).isEqualTo(5);
    }

    private String create(String id, List<String> roles, List<String> projects) throws Exception {
        return json.writeValueAsString(Map.of("userId", id, "name", "시험 사용자",
            "email", id + "@example.invalid", "roleCds", roles,
            "headquarters", false, "projectIds", projects));
    }

    @Test
    @DisplayName("happy auth side-effect: TB_SY_USER and TB_SY_ACCS_LOG registration; REG_USER_ID=admin; no SMTP transport")
    void anonymousPostRegistrationPersistsAndCanBeReadWithoutDisclosingPassword() throws Exception {
        mvc.perform(post("/api/common/users").contentType("application/json")
                .content(create("abcd", List.of("EXEC"), List.of())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.account.userId").value("abcd"))
            .andExpect(jsonPath("$.data.account.password").doesNotExist())
            .andExpect(jsonPath("$.data.account.pwdHash").doesNotExist());
        mvc.perform(get("/api/common/users/abcd"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleCds[0]").value("EXEC"));
        assertThat(data.roles.get("abcd")).containsExactly("EXEC");
        assertThat(data.hashes.get("abcd")).matches("\\$2[aby]\\$10\\$.{53}");
        assertThat(data.temporary).contains("abcd");
        assertThat(data.accesses).containsExactly("abcd:USER_REG");
        verify(mapper).insertAccess(eq("abcd"), eq("USER_REG"), contains("abcd"));
        assertThat(userSql()).contains("INSERT INTO TB_SY_USER (", "reg_user_id,reg_dttm",
            "INSERT INTO TB_SY_ACCS_LOG", "'USE','N',0,'admin'");
        assertThat(TemporaryUserPassword.class.getDeclaredFields())
            .noneMatch(field -> field.getType().getName().contains("MailSender")
                || field.getType().getName().contains("Transport"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABCd", "abc", "abcdefghijklmnopqrstu"})
    void registrationPostRejectsInvalidIdWithoutWriting(String id) throws Exception {
        mvc.perform(post("/api/common/users").contentType("application/json")
                .content(create(id, List.of("EXEC"), List.of())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("userId"));
        assertThat(data.accounts).doesNotContainKey(id);
        verify(mapper, never()).insertUser(any(), anyString());
    }

    @Test void registrationPostRequiresAtLeastOneRole() throws Exception {
        mvc.perform(post("/api/common/users").contentType("application/json")
                .content(create("abcd", List.of(), List.of())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("roleCds"));
        assertThat(data.accounts).doesNotContainKey("abcd");
        verify(mapper, never()).insertUser(any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "email"})
    void registrationPostRequiresProfileFields(String field) throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode)
            json.readTree(create("abcd", List.of("EXEC"), List.of()));
        body.put(field, "");
        mvc.perform(post("/api/common/users").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value(field));
        verify(mapper, never()).insertUser(any(), anyString());
    }

    @Test void registrationPostRejectsMalformedEmail() throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode)
            json.readTree(create("abcd", List.of("EXEC"), List.of()));
        body.put("email", "not-an-email");
        mvc.perform(post("/api/common/users").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("email"));
        verify(mapper, never()).insertUser(any(), anyString());
    }

    @Test void duplicatePostRegistrationPreservesExistingRolesAndProjects() throws Exception {
        data.add("abcd", "USE", List.of("PM"));
        data.projects.get("abcd").add("project-a");
        mvc.perform(post("/api/common/users").contentType("application/json")
                .content(create("abcd", List.of("EXEC"), List.of())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("userId"));
        assertThat(data.roles.get("abcd")).containsExactly("PM");
        assertThat(data.projects.get("abcd")).containsExactly("project-a");
        assertThat(data.accounts.get("abcd").modDttm).isEqualTo(TOKEN);
        verify(mapper, never()).insertUser(any(), anyString());
    }

    @Test void postRegistrationTransfersOnlyPreviousPmAssignment() throws Exception {
        data.add("oldpm", "USE", List.of("PM"));
        data.projects.get("oldpm").add("project-a");
        data.add("reader", "USE", List.of("PROJECT_READER"));
        data.projects.get("reader").add("project-a");
        mvc.perform(post("/api/common/users").contentType("application/json")
                .content(create("newpm", List.of("PM"), List.of("project-a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.transferredProjects[0].projectId").value("project-a"));
        assertThat(data.projects.get("oldpm")).isEmpty();
        assertThat(data.projects.get("newpm")).containsExactly("project-a");
        assertThat(data.projects.get("reader")).containsExactly("project-a");
    }

    @Test void eligibleNonPmPostRegistrationsShareAProject() throws Exception {
        for (String id : List.of("readerone", "readertwo")) {
            mvc.perform(post("/api/common/users").contentType("application/json")
                    .content(create(id, List.of("PROJECT_READER"), List.of("project-a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transferredProjects").isEmpty());
            assertThat(data.projects.get(id)).containsExactly("project-a");
        }
        verify(mapper, never()).selectPmOwners(anyString(), anyList());
    }

    @Test void emptyStopPostDoesNotWriteAccountsOrScopes() throws Exception {
        mvc.perform(post("/api/common/users/stop").contentType("application/json")
                .content(stopBody(List.of(), List.of())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("users"));
        verifyNoInteractions(mapper, options);
    }

    @Test
    @DisplayName("business side-effect: TB_SY_USER.USER_STAT_CD STOP and TB_SY_CHG_HIST only for accepted accounts")
    void stopPostPreservesCurrentAndFutureLeadersAndLastUseAdmin() throws Exception {
        data.add("normal", "USE", List.of("EXEC"));
        data.add("currentlead", "USE", List.of("SALES_LDR"));
        data.add("futurelead", "USE", List.of("SALES_LDR"));
        data.leaders.put("currentlead", List.of("현재 파트"));
        data.leaders.put("futurelead", List.of("예정 파트"));
        mvc.perform(post("/api/common/users/stop").contentType("application/json")
                .content(stopBody(List.of("normal", "currentlead", "futurelead", "admin"), List.of())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.processedUserIds[0]").value("normal"))
            .andExpect(jsonPath("$.data.unprocessedCount").value(3))
            .andExpect(jsonPath("$.data.unprocessed[0].userId").value("admin"))
            .andExpect(jsonPath("$.data.unprocessed[0].reason").value(org.hamcrest.Matchers.containsString("관리자")))
            .andExpect(jsonPath("$.data.unprocessed[1].reason").value(org.hamcrest.Matchers.containsString("현재 파트")))
            .andExpect(jsonPath("$.data.unprocessed[2].reason").value(org.hamcrest.Matchers.containsString("예정 파트")));
        for (String id : List.of("currentlead", "futurelead", "admin")) {
            assertThat(data.accounts.get(id).userStatCd).isEqualTo("USE");
            assertThat(data.accounts.get(id).modDttm).isEqualTo(TOKEN);
        }
        assertThat(data.accounts.get("normal").userStatCd).isEqualTo("STOP");
        assertThat(data.history).hasSize(1);
        assertThat(data.history.get(0).table()).isEqualTo("TB_SY_USER");
        assertThat(data.history.get(0).type()).isEqualTo("STOP");
        assertThat(data.history.get(0).key()).isEqualTo("normal");
        verify(mapper).changeState("normal", TOKEN, "STOP", false);
        assertThat(userSql()).contains("INSERT INTO TB_SY_CHG_HIST", "user_stat_cd=#{state}");
    }

    @Test void lockedAdminIsNotAReplacementForLastUseAdminOnPost() throws Exception {
        data.add("lockedadmin", "LOCK", List.of("ADMIN"));
        mvc.perform(post("/api/common/users/stop").contentType("application/json")
                .content(stopBody(List.of("admin"), List.of())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.processedUserIds").isEmpty())
            .andExpect(jsonPath("$.data.unprocessedCount").value(1));
        assertThat(mapper.countActiveAdmins()).isEqualTo(1);
        verify(mapper, never()).changeState(anyString(), any(), anyString(), anyBoolean());
    }

    @ParameterizedTest
    @ValueSource(strings = {"password-reset", "resume", "unlock"})
    void missingPostVersionLeavesAccountUnchanged(String operation) throws Exception {
        String state = operation.equals("resume") ? "STOP" : "LOCK";
        data.add("member", state, List.of("EXEC"));
        mvc.perform(post("/api/common/users/member/" + operation)
                .contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("modDttm"));
        assertThat(data.accounts.get("member").userStatCd).isEqualTo(state);
        assertThat(data.accounts.get("member").modDttm).isEqualTo(TOKEN);
        verifyNoInteractions(mapper, options);
    }

    @ParameterizedTest
    @ValueSource(strings = {"password-reset", "resume", "unlock"})
    void stalePostVersionLeavesLatestAccountAndScopesUnchanged(String operation) throws Exception {
        String state = operation.equals("resume") ? "STOP" : "LOCK";
        data.add("member", state, List.of("PM")).modDttm = TOKEN.plusNanos(1000);
        data.projects.get("member").add("project-a");
        mvc.perform(post("/api/common/users/member/" + operation)
                .contentType("application/json").content(version()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false));
        assertThat(data.accounts.get("member").userStatCd).isEqualTo(state);
        assertThat(data.accounts.get("member").modDttm).isEqualTo(TOKEN.plusNanos(1000));
        assertThat(data.projects.get("member")).containsExactly("project-a");
        assertThat(data.temporary).doesNotContain("member");
        assertThat(data.accesses).isEmpty();
        assertThat(data.history).isEmpty();
        verify(mapper, never()).changeState(anyString(), any(), anyString(), anyBoolean());
        verify(mapper, never()).resetPassword(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("happy auth side-effect: TB_SY_USER TEMP_PWD_YN=Y MOD_USER_ID=admin MOD_DTTM; no TB_SY_ACCS_LOG")
    void anonymousPasswordResetPostChangesHashAndVersionWithoutAccessLog() throws Exception {
        data.add("member", "USE", List.of("EXEC"));
        String before = new TemporaryUserPassword().hashAndDeliver("member@example.invalid");
        data.hashes.put("member", before);
        mvc.perform(post("/api/common/users/member/password-reset")
                .contentType("application/json").content(version()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tempPwdYn").value("Y"))
            .andExpect(jsonPath("$.data.modDttm").value(TOKEN.plusNanos(1000).toString()))
            .andExpect(jsonPath("$.data.password").doesNotExist())
            .andExpect(jsonPath("$.data.pwdHash").doesNotExist());
        assertThat(data.hashes.get("member")).isNotEqualTo(before).hasSize(60);
        assertThat(data.temporary).contains("member");
        assertThat(data.accesses).isEmpty();
        verify(mapper).resetPassword(eq("member"), eq(TOKEN), anyString());
        verify(mapper, never()).insertAccess(anyString(), anyString(), anyString());
        assertThat(userSql()).contains("temp_pwd_yn='Y'", "mod_user_id='admin'", "mod_dttm=GREATEST");
        mvc.perform(get("/api/common/users/member"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.modDttm").value(TOKEN.plusNanos(1000).toString()));
    }

    @Test
    @DisplayName("happy auth side-effect: PWD_ERR_CNT 5 to 0; MOD_USER_ID=admin MOD_DTTM; no TB_SY_ACCS_LOG")
    void anonymousUnlockPostClearsFailureCountWithoutAccessLog() throws Exception {
        data.add("member", "LOCK", List.of("EXEC"));
        mvc.perform(post("/api/common/users/member/unlock")
                .contentType("application/json").content(version()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userStatCd").value("USE"))
            .andExpect(jsonPath("$.data.pwdErrCnt").value(0))
            .andExpect(jsonPath("$.data.modDttm").value(TOKEN.plusNanos(1000).toString()));
        assertThat(data.failures.get("member")).isZero();
        assertThat(data.accesses).isEmpty();
        verify(mapper).changeState("member", TOKEN, "USE", true);
        verify(mapper, never()).insertAccess(anyString(), anyString(), anyString());
        assertThat(userSql()).contains("pwd_err_cnt=0", "mod_user_id='admin'", "mod_dttm=GREATEST");
        mvc.perform(get("/api/common/users/member"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userStatCd").value("USE"));
    }

    @Test
    @DisplayName("business: password-reset stale MOD_DTTM preserves hash and TEMP_PWD_YN")
    void businessPasswordResetRejectsStaleVersion() throws Exception {
        data.add("member", "USE", List.of("EXEC")).modDttm = TOKEN.plusNanos(1000);
        mvc.perform(post("/api/common/users/member/password-reset")
                .contentType("application/json").content(version()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));
        verify(mapper, never()).resetPassword(anyString(), any(), anyString());
        assertThat(data.hashes).doesNotContainKey("member");
        assertThat(data.temporary).doesNotContain("member");
        assertThat(data.accounts.get("member").modDttm).isEqualTo(TOKEN.plusNanos(1000));
        assertThat(data.accesses).isEmpty();
    }

    @Test
    @DisplayName("business: unlock stale MOD_DTTM preserves LOCK and PWD_ERR_CNT=5")
    void businessUnlockRejectsStaleVersion() throws Exception {
        data.add("member", "LOCK", List.of("EXEC")).modDttm = TOKEN.plusNanos(1000);
        mvc.perform(post("/api/common/users/member/unlock")
                .contentType("application/json").content(version()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));
        assertThat(data.accounts.get("member").userStatCd).isEqualTo("LOCK");
        assertThat(data.failures.get("member")).isEqualTo(5);
        verify(mapper, never()).changeState(anyString(), any(), anyString(), anyBoolean());
        assertThat(data.accesses).isEmpty();
    }

    @Test
    @DisplayName("validation: resume requires modDttm and preserves STOP and TB_SY_CHG_HIST")
    void validationResumeRequiresVersion() throws Exception {
        data.add("member", "STOP", List.of("EXEC"));
        mvc.perform(post("/api/common/users/member/resume").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("modDttm"));
        verifyNoInteractions(mapper, options);
        assertThat(data.accounts.get("member").userStatCd).isEqualTo("STOP");
        assertThat(data.history).isEmpty();
    }

    @Test
    @DisplayName("business: resume stale MOD_DTTM preserves STOP and removed PROJ scopes")
    void businessResumeRejectsStaleVersion() throws Exception {
        data.add("member", "STOP", List.of("PM")).modDttm = TOKEN.plusNanos(1000);
        mvc.perform(post("/api/common/users/member/resume").contentType("application/json").content(version()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));
        assertThat(data.accounts.get("member").userStatCd).isEqualTo("STOP");
        assertThat(data.projects.get("member")).isEmpty();
        assertThat(data.history).isEmpty();
        verify(mapper, never()).changeState(anyString(), any(), anyString(), anyBoolean());
        verify(mapper, never()).insertScope(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("happy auth side-effect: TB_SY_USER resume MOD_USER_ID=admin MOD_DTTM and TB_SY_CHG_HIST; no PROJ restore")
    void authSideEffectResumeWritesStateAndHistoryWithoutRestoringScopes() throws Exception {
        data.add("member", "STOP", List.of("PM"));
        mvc.perform(post("/api/common/users/member/resume").contentType("application/json").content(version()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.userStatCd").value("USE"))
            .andExpect(jsonPath("$.data.projectIds").isEmpty())
            .andExpect(jsonPath("$.data.modDttm").value(TOKEN.plusNanos(1000).toString()));
        verify(mapper).changeState("member", TOKEN, "USE", false);
        assertThat(data.history).containsExactly(new com.company.common.dto.UserContracts.History(
            "TB_SY_USER", "member", "RESUME", "상태", "중지", "사용"));
        assertThat(data.projects.get("member")).isEmpty();
        verify(mapper, never()).insertScope(anyString(), anyString(), anyString());
        assertThat(userSql()).contains("mod_user_id='admin'", "mod_dttm=GREATEST", "INSERT INTO TB_SY_CHG_HIST");
        mvc.perform(get("/api/common/users/member")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userStatCd").value("USE"))
            .andExpect(jsonPath("$.data.projectIds").isEmpty());
        mvc.perform(get("/api/common/users/member/change-history")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].changedBy").value("admin"))
            .andExpect(jsonPath("$.data[0].before").value("중지"))
            .andExpect(jsonPath("$.data[0].after").value("사용"));
    }
}
