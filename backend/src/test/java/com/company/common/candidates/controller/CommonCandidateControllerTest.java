package com.company.common.candidates.controller;

import com.company.common.candidates.dto.CandidateContracts.*;
import com.company.common.candidates.mapper.*;
import com.company.common.candidates.mapper.CandidateRows.*;
import com.company.common.candidates.service.*;
import com.company.common.common.CandidateCodes;
import com.company.common.codegroups.dto.CodeContracts.Item;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.support.service.BusinessCalendar;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.*;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonCandidateController.class)
@ContextConfiguration(classes = {CommonCandidateController.class, CommonCandidateService.class,
    com.company.common.support.service.ExcelExportService.class, com.company.common.support.service.CurrentBusinessActor.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class})
class CommonCandidateControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean CandidateMapper mapper;
    @MockBean CandidateCodes codes;
    @MockBean BusinessCalendar calendar;
    @MockBean CandidateCalculationRules calculations;
    @MockBean PlatformTransactionManager transactions;
    final LocalDate today = LocalDate.of(2026, 11, 15);
    final Instant now = Instant.parse("2026-11-15T00:00:00Z");
    Map<String, CandidateRow> rows;
    Map<String, List<String>> skills;
    Map<String, List<ContactRow>> contacts;
    long version;

    @BeforeEach void setup() {
        rows = new LinkedHashMap<>(); skills = new HashMap<>(); contacts = new HashMap<>(); version = 0;
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(calendar.today()).thenReturn(today);
        when(calendar.currentMonth()).thenReturn(YearMonth.from(today));
        when(calendar.fromDatabaseMonth(anyString())).thenAnswer(call -> YearMonth.parse(((String) call.getArgument(0)).substring(0, 4) + "-" + ((String) call.getArgument(0)).substring(4)));
        when(codes.selectable(anyString())).thenAnswer(call -> {
            var values = switch ((String) call.getArgument(0)) {
                case "EMP_TYPE" -> List.of("FL", "OS"); case "JOB" -> List.of("DEV", "PM");
                case "GRADE" -> List.of("HIGH", "MID", "LOW"); case "CAND_SRC" -> List.of("SITE");
                case "CNTC_STAT" -> List.of("BEFORE", "DONE", "ITVW", "HOLD", "CTRT"); default -> List.<String>of();
            };
            return values.stream().map(value -> { var item = new Item(); item.code = value; item.codeName = value; item.useYn = "Y"; return item; }).toList();
        });
        when(codes.name(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(mapper.selectableCompany("vendor")).thenReturn(true);
        when(mapper.selectArea("1168000000")).thenReturn(new Area("1168000000", "서울 강남구"));
        when(mapper.selectCandidate(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        when(mapper.lockCandidate(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        when(mapper.selectSkills(anyString())).thenAnswer(call -> List.copyOf(skills.getOrDefault(call.getArgument(0), List.of())));
        when(mapper.selectContacts(anyString())).thenAnswer(call -> List.copyOf(contacts.getOrDefault(call.getArgument(0), List.of())));
        when(mapper.selectCandidates(any())).thenAnswer(call -> {
            Query q = call.getArgument(0);
            return rows.values().stream().filter(row -> q.filter().jobCode() == null || q.filter().jobCode().equals(row.primaryJobCode()))
                .filter(row -> q.availableThrough() == null || !row.availableDate().isAfter(q.availableThrough())).toList();
        });
        when(mapper.insertCandidate(any())).thenAnswer(call -> {
            Write w = call.getArgument(0); if (rows.containsKey(w.candidateId())) return 0;
            rows.put(w.candidateId(), apply(w, null)); return 1;
        });
        when(mapper.updateCandidate(any())).thenAnswer(call -> {
            Write w = call.getArgument(0); rows.put(w.candidateId(), apply(w, rows.get(w.candidateId()))); return 1;
        });
        doAnswer(call -> { skills.put(call.getArgument(0), new ArrayList<>()); return null; }).when(mapper).deleteSkills(anyString());
        doAnswer(call -> { skills.get((String) call.getArgument(0)).add(call.getArgument(1)); return null; }).when(mapper).insertSkill(anyString(), anyString(), anyInt());
        doAnswer(call -> {
            ContactWrite w = call.getArgument(0);
            contacts.computeIfAbsent(w.candidateId(), key -> new ArrayList<>()).add(0,
                new ContactRow(w.status(), w.memo(), now.plusSeconds(++version), w.actor())); return null;
        }).when(mapper).insertContact(any());
        when(mapper.updateContact(any())).thenAnswer(call -> {
            ContactWrite w = call.getArgument(0); var old = rows.get(w.candidateId());
            rows.put(w.candidateId(), new CandidateRow(old.candidateId(), old.name(), old.employeeTypeCode(), old.companyId(),
                old.phone(), old.email(), old.privacyAgreementYn(), old.privacyAgreementDate(), old.privacyAgreementUserId(),
                old.primaryJobCode(), old.secondaryJobCode(), old.gradeCode(), old.homeAreaCode(), old.homeAddress(),
                old.desiredMonthlyRate(), old.availableDate(), w.status(), today, old.sourceCode(), old.memo(), old.registeredAt(),
                now.plusSeconds(++version), old.companyName(), old.privacyAgreementUserName())); return 1;
        });
        when(calculations.terms(anyBoolean(), any())).thenReturn(new CandidateCalculationRules.Terms(BigDecimal.valueOf(100), BigDecimal.valueOf(100000), BigDecimal.valueOf(15)));
        when(mapper.selectSeats(anyString())).thenReturn(List.of());
    }
    CandidateRow apply(Write w, CandidateRow old) {
        var i = w.input();
        LocalDate consent = old != null ? old.privacyAgreementDate() : null;
        if (consent == null && i.privacyAgreement()) consent = today;
        return new CandidateRow(w.candidateId(), i.name(), i.employeeTypeCode(), i.companyId(), i.phone(), i.email(),
            i.privacyAgreement() ? "Y" : "N", consent, consent == null ? null : "admin", i.primaryJobCode(), i.secondaryJobCode(),
            i.gradeCode(), i.homeAreaCode(), i.homeAddress(), i.desiredMonthlyRate(), i.availableDate(),
            old == null ? "BEFORE" : old.contactStatusCode(), old == null ? today : old.lastContactDate(), i.sourceCode(), i.memo(),
            old == null ? now : old.registeredAt(), old == null ? null : now.plusSeconds(++version), i.companyId(), consent == null ? null : "admin");
    }
    ObjectNode input() {
        var b = json.createObjectNode();
        b.put("name", "후보"); b.put("desiredMonthlyRate", 6500000); b.put("employeeTypeCode", "FL");
        b.put("primaryJobCode", "DEV"); b.put("secondaryJobCode", "DEV"); b.putArray("skills").add("Java").add("Java");
        b.put("gradeCode", "MID"); b.put("availableDate", "2026-12-01"); b.put("sourceCode", "SITE");
        b.put("phone", "02-1234-5678"); b.put("email", "a@example.org"); b.put("privacyAgreement", true);
        b.put("homeAreaCode", "1168000000"); b.put("memo", "기존 메모"); return b;
    }
    String create() throws Exception {
        var response = mvc.perform(post("/api/common/candidates").contentType("application/json").content(input().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).path("data").path("candidateId").asText();
    }
    ObjectNode contact(String status) {
        var b = json.createObjectNode(); b.putNull("modifiedAt"); b.put("contactStatusCode", status); b.put("memo", "통화함"); return b;
    }
    @Test void publicCreateReadsFieldsSkillsConsentAndInitialDateBack() throws Exception {
        String id = create();
        assertThat(id).hasSize(20);
        mvc.perform(get("/api/common/candidates/{id}", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("후보")).andExpect(jsonPath("$.data.skills.length()").value(1))
            .andExpect(jsonPath("$.data.secondaryJobCode").isEmpty()).andExpect(jsonPath("$.data.contactStatusCode").value("BEFORE"))
            .andExpect(jsonPath("$.data.lastContactDate").value(today.toString())).andExpect(jsonPath("$.data.privacyAgreementUserId").value("admin"))
            .andExpect(jsonPath("$.data.privacyAgreementDate").value(today.toString())).andExpect(jsonPath("$.data.modifiedAt").isEmpty())
            .andExpect(jsonPath("$.data.homeAddress").value("서울 강남구"));
    }
    @Test void creationAddsExactlyOneSystemRegistrationContact() throws Exception {
        String id = create();
        mvc.perform(get("/api/common/candidates/{id}/contacts", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].memo").value("등록"))
            .andExpect(jsonPath("$.data.items[0].systemRecord").value(true)).andExpect(jsonPath("$.data.items[0].recordedBy").value("SYSTEM"));
    }
    @ParameterizedTest @ValueSource(strings = {"name", "skills", "phone", "email", "memo", "desiredMonthlyRate", "homeAddress", "primaryJobCode"})
    void invalidCreateDoesNotInsert(String field) throws Exception {
        var b = input();
        switch (field) {
            case "name" -> b.put(field, "가".repeat(51)); case "skills" -> b.putArray(field);
            case "phone" -> b.put(field, "010 abc"); case "email" -> b.put(field, "not-email");
            case "memo" -> b.put(field, "가".repeat(501)); case "desiredMonthlyRate" -> b.put(field, -1);
            case "homeAddress" -> b.put(field, "가".repeat(51)); default -> b.put(field, "x".repeat(11));
        }
        mvc.perform(post("/api/common/candidates").contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
        assertThat(rows).isEmpty(); verify(mapper, never()).insertCandidate(any());
    }
    @ParameterizedTest @ValueSource(strings = {"-1", "0.1", "1000000000000000"})
    void invalidAmountIsNeverRounded(String value) throws Exception {
        var b = input(); b.put("desiredMonthlyRate", new BigDecimal(value));
        mvc.perform(post("/api/common/candidates").contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("desiredMonthlyRate"));
        assertThat(rows).isEmpty();
    }
    @Test void zeroAmountIsPermitted() throws Exception {
        var b = input(); b.put("desiredMonthlyRate", 0);
        mvc.perform(post("/api/common/candidates").contentType("application/json").content(b.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.desiredMonthlyRate").value(0));
    }
    @Test void outsourcingRequiresCompany() throws Exception {
        var b = input(); b.put("employeeTypeCode", "OS");
        mvc.perform(post("/api/common/candidates").contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("companyId"));
        assertThat(rows).isEmpty();
    }
    @Test void inactiveCompanyIsNotSelectable() throws Exception {
        var b = input(); b.put("employeeTypeCode", "OS"); b.put("companyId", "inactive");
        mvc.perform(post("/api/common/candidates").contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("companyId"));
        assertThat(rows).isEmpty();
    }
    @Test void contactDetailsRequireConsent() throws Exception {
        var b = input(); b.put("privacyAgreement", false);
        mvc.perform(post("/api/common/candidates").contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("privacyAgreement"));
        assertThat(rows).isEmpty();
    }
    @Test void sameStatusPublicContactUpdatesTodayAndRecordsAdmin() throws Exception {
        String id = create();
        mvc.perform(post("/api/common/candidates/{id}/contacts", id).contentType("application/json").content(contact("BEFORE").toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.contactStatusCode").value("BEFORE"))
            .andExpect(jsonPath("$.data.lastContactDate").value(today.toString())).andExpect(jsonPath("$.data.modifiedAt").isString());
        mvc.perform(get("/api/common/candidates/{id}/contacts", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].recordedBy").value("admin")).andExpect(jsonPath("$.data.items[0].systemRecord").value(false));
    }
    @ParameterizedTest @ValueSource(strings = {"HOLD", "CTRT"})
    void terminalContactIsNewestAndNoLongerOverdue(String state) throws Exception {
        String id = create();
        mvc.perform(post("/api/common/candidates/{id}/contacts", id).contentType("application/json").content(contact(state).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.overdueContact").value(false));
        mvc.perform(get("/api/common/candidates/{id}/contacts", id)).andExpect(jsonPath("$.data.items[0].contactStatusCode").value(state));
    }
    @ParameterizedTest @ValueSource(strings = {"long", "blank", "status", "missingVersion"})
    void invalidContactDoesNotAppend(String rule) throws Exception {
        String id = create(); var b = contact("DONE");
        switch (rule) { case "long" -> b.put("memo", "가".repeat(201)); case "blank" -> b.put("memo", "  ");
            case "status" -> b.put("contactStatusCode", "OTHER"); default -> b.remove("modifiedAt"); }
        mvc.perform(post("/api/common/candidates/{id}/contacts", id).contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(rule.equals("status") ? "contactStatusCode" : rule.equals("missingVersion") ? "modifiedAt" : "memo"));
        assertThat(contacts.get(id)).hasSize(1); assertThat(rows.get(id).modifiedAt()).isNull();
    }
    @Test void staleContactPreservesAllState() throws Exception {
        String id = create(); var before = rows.get(id); var b = contact("HOLD"); b.put("modifiedAt", now.toString());
        mvc.perform(post("/api/common/candidates/{id}/contacts", id).contentType("application/json").content(b.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0].message").value("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."));
        assertThat(rows.get(id)).isEqualTo(before); assertThat(contacts.get(id)).hasSize(1);
    }
    @Test void publicUpdatePreservesLastContactAndAddsOneSystemInformationChange() throws Exception {
        String id = create(), other = create(); var untouched = rows.get(other); var last = rows.get(id).lastContactDate();
        var b = input(); b.putNull("modifiedAt"); b.put("desiredMonthlyRate", 6000000);
        mvc.perform(put("/api/common/candidates/{id}", id).contentType("application/json").content(b.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.desiredMonthlyRate").value(6000000))
            .andExpect(jsonPath("$.data.lastContactDate").value(last.toString())).andExpect(jsonPath("$.data.modifiedAt").isString());
        assertThat(rows.get(other)).isEqualTo(untouched);
        mvc.perform(get("/api/common/candidates/{id}/contacts", id)).andExpect(jsonPath("$.data.items[0].memo").value("정보 수정"))
            .andExpect(jsonPath("$.data.items[0].systemRecord").value(true)).andExpect(jsonPath("$.data.items.length()").value(2));
    }
    /** 민감 체크가 없는 로그인 사용자(보안 필터가 요청에 남기는 값과 같은 모양). */
    static org.springframework.test.web.servlet.request.RequestPostProcessor nonSensitive() {
        return request -> {
            new com.company.common.support.auth.RequestAccess("viewer", Set.of("PM"),
                new com.company.common.users.service.UserScopePolicy.Scope(true, Set.of(), Set.of()), false,
                Set.of(), Set.of(), "127.0.0.1", false, Set.of()).store(request);
            return request;
        };
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "null", "number"})
    @DisplayName("민감 체크 없는 사용자의 수정: 희망 단가는 빠지거나 null·다른 값이 와도 저장된 값을 그대로 둔다")
    void nonSensitiveUpdateKeepsStoredDesiredRate(String shape) throws Exception {
        String id = create(); assertThat(rows.get(id).desiredMonthlyRate()).isEqualByComparingTo("6500000");
        var b = input(); b.putNull("modifiedAt"); b.put("memo", "바꾼 메모");
        switch (shape) {
            case "missing" -> b.remove("desiredMonthlyRate");
            case "null" -> b.putNull("desiredMonthlyRate");
            default -> b.put("desiredMonthlyRate", 1);
        }
        mvc.perform(put("/api/common/candidates/{id}", id).with(nonSensitive()).contentType("application/json").content(b.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.desiredMonthlyRate").doesNotExist())
            .andExpect(jsonPath("$.data.memo").value("바꾼 메모"));
        assertThat(rows.get(id).desiredMonthlyRate()).isEqualByComparingTo("6500000");
        assertThat(rows.get(id).memo()).isEqualTo("바꾼 메모");
    }
    @Test void sensitiveUpdateStillRequiresDesiredRate() throws Exception {
        String id = create(); var before = rows.get(id); var b = input(); b.putNull("modifiedAt"); b.remove("desiredMonthlyRate");
        mvc.perform(put("/api/common/candidates/{id}", id).contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("desiredMonthlyRate"));
        assertThat(rows.get(id)).isEqualTo(before);
    }
    @Test void oversizedUpdateMemoLeavesOriginal() throws Exception {
        String id = create(); var old = rows.get(id); var b = input(); b.putNull("modifiedAt"); b.put("memo", "가".repeat(501));
        mvc.perform(put("/api/common/candidates/{id}", id).contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("memo"));
        assertThat(rows.get(id)).isEqualTo(old); assertThat(contacts.get(id)).hasSize(1);
    }
    @Test void staleUpdateChangesNeitherCandidateSkillsNorHistory() throws Exception {
        String id = create(); var before = rows.get(id); var b = input(); b.put("modifiedAt", now.toString()); b.putArray("skills").add("Kotlin");
        mvc.perform(put("/api/common/candidates/{id}", id).contentType("application/json").content(b.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0].message").value("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."));
        assertThat(rows.get(id)).isEqualTo(before); assertThat(skills.get(id)).containsExactly("Java"); assertThat(contacts.get(id)).hasSize(1);
    }
    @Test void explicitNullCannotOverwriteNonNullVersion() throws Exception {
        String id = create(); var b = input(); b.putNull("modifiedAt");
        mvc.perform(put("/api/common/candidates/{id}", id).contentType("application/json").content(b.toString())).andExpect(status().isOk());
        var before = rows.get(id);
        mvc.perform(put("/api/common/candidates/{id}", id).contentType("application/json").content(b.toString())).andExpect(status().isConflict());
        assertThat(rows.get(id)).isEqualTo(before); assertThat(contacts.get(id)).hasSize(2);
    }
    @Test void missingVersionIsFieldError() throws Exception {
        String id = create(); mvc.perform(put("/api/common/candidates/{id}", id).contentType("application/json").content(input().toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("modifiedAt"));
    }
    @Test void firstConsentMetadataSurvivesWithdrawalAndReconsent() throws Exception {
        String id = create(); var first = rows.get(id).privacyAgreementDate();
        var b = input(); b.putNull("modifiedAt"); b.putNull("phone"); b.putNull("email"); b.put("privacyAgreement", false);
        mvc.perform(put("/api/common/candidates/{id}", id).contentType("application/json").content(b.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.privacyAgreement").value(false))
            .andExpect(jsonPath("$.data.privacyAgreementDate").value(first.toString()));
        when(calendar.today()).thenReturn(today.plusDays(4));
        b.put("modifiedAt", rows.get(id).modifiedAt().toString()); b.put("privacyAgreement", true);
        mvc.perform(put("/api/common/candidates/{id}", id).contentType("application/json").content(b.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.privacyAgreementDate").value(first.toString()));
    }
    @ParameterizedTest
    @ValueSource(strings = {"name", "desiredMonthlyRate", "employeeTypeCode", "primaryJobCode", "skills", "gradeCode", "availableDate", "sourceCode"})
    void missingRequiredCreateFieldDoesNotWrite(String field) throws Exception {
        var body = input(); body.remove(field);
        mvc.perform(post("/api/common/candidates").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
        assertThat(rows).isEmpty(); verify(mapper, never()).insertCandidate(any());
    }
    @Test void candidateIdentityCannotBeProvidedByCreateCaller() throws Exception {
        var body = input().put("candidateId", "client-issued");
        mvc.perform(post("/api/common/candidates").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest());
        assertThat(rows).isEmpty();
    }
    @Test void oversizedSkillIsFieldErrorWithoutAnyWrite() throws Exception {
        var body = input(); body.putArray("skills").add("가".repeat(51));
        mvc.perform(post("/api/common/candidates").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("skills"));
        assertThat(rows).isEmpty();
    }
    @Test void consentCannotBeWithdrawnWhileContactDetailsRemain() throws Exception {
        String id = create(); var before = rows.get(id); var history = List.copyOf(contacts.get(id));
        mvc.perform(put("/api/common/candidates/{id}", id).contentType("application/json").content(input().putNull("modifiedAt").put("privacyAgreement", false).toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("privacyAgreement"));
        assertThat(rows.get(id)).isEqualTo(before); assertThat(contacts.get(id)).isEqualTo(history);
    }
    @Test void legacyNullLastContactUsesKoreanRegistrationDateWithoutWriting() throws Exception {
        String id = create(); var old = rows.get(id);
        rows.put(id, new CandidateRow(old.candidateId(), old.name(), old.employeeTypeCode(), old.companyId(),
            old.phone(), old.email(), old.privacyAgreementYn(), old.privacyAgreementDate(), old.privacyAgreementUserId(),
            old.primaryJobCode(), old.secondaryJobCode(), old.gradeCode(), old.homeAreaCode(), old.homeAddress(),
            old.desiredMonthlyRate(), old.availableDate(), old.contactStatusCode(), null, old.sourceCode(), old.memo(),
            Instant.parse("2026-10-01T16:00:00Z"), old.modifiedAt(), old.companyName(), old.privacyAgreementUserName()));
        clearInvocations(mapper);
        mvc.perform(get("/api/common/candidates/{id}", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.lastContactDate").value("2026-10-02"))
            .andExpect(jsonPath("$.data.overdueContact").value(true));
        assertThat(rows.get(id).lastContactDate()).isNull();
        verify(mapper, never()).updateCandidate(any()); verify(mapper, never()).updateContact(any());
    }
    @Test void listPassesCalendarBoundariesAndFiltersWithoutWrites() throws Exception {
        create();
        mvc.perform(get("/api/common/candidates").param("jobCode", "DEV").param("availabilityWindow", "next-month"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
        verify(mapper).selectCandidates(new Query(new Filter("DEV", null, null, null, "next-month"), LocalDate.of(2026, 12, 31)));
        mvc.perform(get("/api/common/candidates").param("availabilityWindow", "within-three-months")).andExpect(status().isOk());
        verify(mapper).selectCandidates(new Query(new Filter(null, null, null, null, "within-three-months"), LocalDate.of(2027, 1, 31)));
    }
    @Test void invalidListFilterIsRejected() throws Exception {
        mvc.perform(get("/api/common/candidates").param("availabilityWindow", "unknown")).andExpect(status().isBadRequest());
        verify(mapper, never()).selectCandidates(any());
    }
    @ParameterizedTest @ValueSource(strings = {"", "/contacts", "/matches"})
    void missingCandidateReturns404(String suffix) throws Exception {
        mvc.perform(get("/api/common/candidates/absent" + suffix)).andExpect(status().isNotFound());
    }
    @Test void matchingUsesPrimaryJobInclusiveEndMonthAndUpdatedRateEvenForTerminalCandidate() throws Exception {
        String id = create();
        var seats = List.of(new Seat("op-b", BigDecimal.ONE, "기회 B", BigDecimal.valueOf(50), "DEV", "MID", "202611", BigDecimal.valueOf(2), BigDecimal.valueOf(8000000)),
            new Seat("expired", BigDecimal.ONE, "지난 기회", BigDecimal.valueOf(90), "DEV", "MID", "202610", BigDecimal.ONE, BigDecimal.valueOf(9000000)),
            new Seat("other-job", BigDecimal.ONE, "PM", BigDecimal.valueOf(90), "PM", "MID", "202612", BigDecimal.ONE, BigDecimal.valueOf(9000000)),
            new Seat("unpriced", BigDecimal.ONE, "미정", BigDecimal.TEN, "DEV", "MID", "202612", BigDecimal.ONE, null));
        when(mapper.selectSeats("DEV")).thenReturn(seats);
        mvc.perform(get("/api/common/candidates/{id}/matches", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].expectedProfitRate").value(17.5))
            .andExpect(jsonPath("$.data.items[0].lowMargin").value(false)).andExpect(jsonPath("$.data.items[0].lowMarginRate").value(15));
        mvc.perform(post("/api/common/candidates/{id}/contacts", id).contentType("application/json").content(contact("HOLD").toString())).andExpect(status().isOk());
        var b = input(); b.put("modifiedAt", rows.get(id).modifiedAt().toString()); b.put("desiredMonthlyRate", 6000000);
        mvc.perform(put("/api/common/candidates/{id}", id).contentType("application/json").content(b.toString())).andExpect(status().isOk());
        mvc.perform(get("/api/common/candidates/{id}/matches", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].expectedProfitRate").value(23.75));
    }
    @Test void excelHasAllFilteredRowsInListColumnOrderAndRecordsDownload() throws Exception {
        when(calendar.now()).thenReturn(now);
        create(); create();
        var other = input(); other.put("primaryJobCode", "PM");
        mvc.perform(post("/api/common/candidates").contentType("application/json").content(other.toString())).andExpect(status().isOk());
        var response = mvc.perform(get("/api/common/candidates/excel").param("jobCode", "DEV"))
            .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "2"))
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn().getResponse();
        assertThat(response.getHeader("Content-Disposition")).contains("UTF-8''").contains(".xlsx");
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).toString()).startsWith("외부 인력 후보 · 다운로드: admin");
            var head = sheet.getRow(2);
            var labels = new ArrayList<String>();
            for (var cell : head) labels.add(cell.toString());
            assertThat(labels).containsExactly("이름", "구분", "직무", "등급", "기술", "거주지", "희망 단가",
                "투입 가능일", "연락 상태", "최근 연락", "출처");
            assertThat(sheet.getLastRowNum()).isEqualTo(4);
            var row = sheet.getRow(3);
            assertThat(row.getCell(0).toString()).isEqualTo("후보");
            assertThat(row.getCell(4).toString()).isEqualTo("Java");
            assertThat(row.getCell(5).toString()).isEqualTo("서울 강남구");
            assertThat(row.getCell(6).getNumericCellValue()).isEqualTo(6500000d);
            assertThat(row.getCell(7).toString()).isEqualTo("2026-12-01");
        }
        verify(mapper).insertAccess("admin", "외부 인력 후보 2건");
    }
    @Test void excelRejectsUnknownFilterWithoutRecordingDownload() throws Exception {
        mvc.perform(get("/api/common/candidates/excel").param("availabilityWindow", "someday"))
            .andExpect(status().isBadRequest());
        verify(mapper, never()).insertAccess(anyString(), anyString());
    }
}
