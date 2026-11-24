package com.company.common.salesopportunities.controller;

import com.company.common.salesopportunities.dto.SalesOpportunityContracts.*;
import com.company.common.salesopportunities.mapper.*;
import com.company.common.salesopportunities.mapper.SalesOpportunityRows.*;
import com.company.common.salesopportunities.service.*;
import com.company.common.common.*;
import com.company.common.codegroups.dto.CodeContracts.Item;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
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

@WebMvcTest(CommonSalesOpportunityController.class)
@ContextConfiguration(classes = {CommonSalesOpportunityController.class, CommonSalesOpportunityService.class,
    CurrentBusinessActor.class, CodeExceptionHandler.class, ApiExceptionHandler.class})
class CommonSalesOpportunityControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean SalesOpportunityMapper mapper;
    @MockBean SalesPipelineCodes codes;
    @MockBean SalesOpportunityCalculations calculations;
    @MockBean SalesOpportunityProjects projects;
    @MockBean BusinessCalendar calendar;
    @MockBean PlatformTransactionManager transactions;
    final Instant now = Instant.parse("2026-11-15T00:00:00Z");
    final LocalDate today = LocalDate.of(2026, 11, 15);
    final String root = "/api/common/sales-opportunities";
    Map<String, Header> headers;
    Map<String, List<NeedRow>> needs;
    Map<String, List<ApprovalHistory>> histories;
    List<Reservation> reservations;
    List<Notification> notifications;
    long sequence;

    @BeforeEach void setup() {
        headers = new LinkedHashMap<>(); needs = new HashMap<>(); histories = new HashMap<>();
        reservations = new ArrayList<>(); notifications = new ArrayList<>(); sequence = 0;
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(calendar.today()).thenReturn(today);
        when(calendar.now()).thenReturn(now);
        when(calendar.currentMonth()).thenReturn(YearMonth.from(today));
        when(codes.selectable(anyString())).thenAnswer(call -> {
            var values = switch ((String) call.getArgument(0)) {
                case "SALES_STEP" -> List.of("FIND", "PROP", "NEGO", "PRE");
                case "JOB" -> List.of("BE", "PM"); case "GRADE" -> List.of("HIGH");
                case "CTRT_TYPE" -> List.of("TK", "MO"); case "LOSE_RSN" -> List.of("PRICE", "TECH", "SCHED", "CANCEL", "ETC");
                default -> List.<String>of();
            };
            return values.stream().map(code -> { var item = new Item(); item.code = code; item.codeName = code; item.useYn = "Y"; return item; }).toList();
        });
        when(codes.name(anyString(), anyString())).thenAnswer(c -> c.getArgument(1));
        when(mapper.customer("customer")).thenReturn(true);
        when(mapper.part("part")).thenReturn(true);
        when(mapper.developer("developer")).thenReturn(true);
        when(mapper.select(anyString())).thenAnswer(c -> headers.get(c.getArgument(0)));
        when(mapper.lock(anyString())).thenAnswer(c -> headers.get(c.getArgument(0)));
        when(mapper.needs(anyString())).thenAnswer(c -> List.copyOf(needs.getOrDefault(c.getArgument(0), List.of())));
        when(mapper.history(anyString())).thenAnswer(c -> List.copyOf(histories.getOrDefault(c.getArgument(0), List.of())));
        when(mapper.reservations(anyString())).thenAnswer(c -> reservations.stream().filter(r -> r.opportunityId().equals(c.getArgument(0)) && Set.of("ACT", "CNVT").contains(r.status())).toList());
        when(mapper.developerReservations(anyString())).thenAnswer(c -> reservations.stream().filter(r -> r.developerId().equals(c.getArgument(0)) && "ACT".equals(r.status())).toList());
        when(mapper.confirmedMm(anyString(), anyString())).thenReturn(BigDecimal.ZERO);
        when(calculations.price(any())).thenAnswer(c -> ((Need) c.getArgument(0)).estimatedUnitPrice() == null ? new BigDecimal("10000000") : ((Need) c.getArgument(0)).estimatedUnitPrice());
        when(calculations.stageProbability(anyString())).thenAnswer(c -> new BigDecimal(switch ((String)c.getArgument(0)) { case "PROP" -> "30"; case "NEGO" -> "60"; case "PRE" -> "90"; default -> "10"; }));
        when(calculations.evaluate(anyList())).thenReturn(evaluation("14"));
        when(mapper.insert(any())).thenAnswer(c -> { Write w = c.getArgument(0); headers.put(w.id(), header(w.id(), w.input(), "ING", w.approval(), w.manualYn(), w.profit(), null, null)); return 1; });
        when(mapper.update(any())).thenAnswer(c -> { Write w = c.getArgument(0); var old = headers.get(w.id()); headers.put(w.id(), header(w.id(), w.input(), old.status(), w.approval(), w.manualYn(), w.profit(), old.approvedProfitRate(), old.projectId())); return 1; });
        when(mapper.insertNeed(any())).thenAnswer(c -> { NeedWrite w = c.getArgument(0); needs.computeIfAbsent(w.id(), k -> new ArrayList<>()).add(needRow(w.need())); return 1; });
        when(mapper.updateNeed(any())).thenAnswer(c -> { NeedWrite w = c.getArgument(0); needs.get(w.id()).removeIf(n -> n.needSn().equals(w.need().needSn())); needs.get(w.id()).add(needRow(w.need())); return 1; });
        when(mapper.removeNeed(anyString(), anyInt())).thenAnswer(c -> { needs.get(c.getArgument(0)).removeIf(n -> n.needSn().equals(c.getArgument(1))); return 1; });
        when(mapper.stage(any())).thenAnswer(c -> { StageWrite w = c.getArgument(0); var old = headers.get(w.id()); var in = input(old, w.stage(), w.probability()); headers.put(w.id(), header(w.id(), in, old.status(), old.approvalStatus(), old.manualYn(), old.profitRate(), old.approvedProfitRate(), old.projectId())); return 1; });
        when(mapper.approval(any())).thenAnswer(c -> { ApprovalWrite w = c.getArgument(0); var old = headers.get(w.id()); headers.put(w.id(), header(w.id(), input(old, old.stageCode(), old.winningProbability()), old.status(), w.status(), old.manualYn(), old.profitRate(), "OK".equals(w.status()) ? old.profitRate() : old.approvedProfitRate(), old.projectId())); return 1; });
        when(mapper.appendHistory(any())).thenAnswer(c -> { ApprovalWrite w = c.getArgument(0); histories.computeIfAbsent(w.id(), k -> new ArrayList<>()).add(new ApprovalHistory(w.status(), now.plusSeconds(++sequence), w.reason())); return 1; });
        when(mapper.notifyOwner(any())).thenAnswer(c -> { notifications.add(c.getArgument(0)); return 1; });
        when(mapper.result(any())).thenAnswer(c -> { ResultWrite w = c.getArgument(0); var old = headers.get(w.id()); var h = header(w.id(), input(old, old.stageCode(), old.winningProbability()), w.status(), old.approvalStatus(), old.manualYn(), old.profitRate(), old.approvedProfitRate(), old.projectId()); headers.put(w.id(), new Header(h.opportunityId(), h.opportunityName(), h.customerId(), h.customerName(), h.salesPartId(), h.stageCode(), h.winningProbability(), h.manualYn(), h.expectedAmount(), h.contractTypeCode(), h.startMonth(), h.expectedMonths(), h.workplace(), h.nextAction(), h.nextActionDueDate(), h.memo(), h.status(), h.profitRate(), h.approvalStatus(), h.approvedProfitRate(), w.date(), w.reason(), w.memo(), h.projectId(), h.creator(), h.modifiedAt())); return 1; });
        when(mapper.releaseAll(anyString(), anyString())).thenAnswer(c -> { reservations.removeIf(r -> r.opportunityId().equals(c.getArgument(0)) && "ACT".equals(r.status())); return 1; });
        when(mapper.release(anyLong(), anyString())).thenAnswer(c -> { reservations.removeIf(r -> r.tentativeId() == (long)c.getArgument(0)); return 1; });
        when(mapper.tentative(any())).thenAnswer(c -> { TentativeWrite w = c.getArgument(0); reservations.removeIf(r -> r.opportunityId().equals(w.id()) && r.needSn() == w.needSn() && r.developerId().equals(w.developerId())); reservations.add(new Reservation(++sequence, w.id(), headers.get(w.id()).opportunityName(), w.needSn(), w.developerId(), w.startMonth().replace("-", ""), "ACT")); return 1; });
        when(mapper.touch(anyString(), anyString())).thenAnswer(c -> { var h = headers.get(c.getArgument(0)); headers.put(h.opportunityId(), header(h.opportunityId(), input(h, h.stageCode(), h.winningProbability()), h.status(), h.approvalStatus(), h.manualYn(), h.profitRate(), h.approvedProfitRate(), h.projectId())); return 1; });
        when(projects.create(any(), any())).thenReturn("project");
        when(mapper.linkProject(any())).thenAnswer(c -> { ProjectLink w = c.getArgument(0); var h = headers.get(w.id()); headers.put(w.id(), header(w.id(), input(h, h.stageCode(), h.winningProbability()), "WIN", h.approvalStatus(), h.manualYn(), h.profitRate(), h.approvedProfitRate(), w.projectId())); return 1; });
        when(mapper.convert(anyString(), anyString())).thenAnswer(c -> { reservations.replaceAll(r -> r.opportunityId().equals(c.getArgument(0)) ? new Reservation(r.tentativeId(), r.opportunityId(), r.opportunityName(), r.needSn(), r.developerId(), r.startMonth(), "CNVT") : r); return 1; });
    }
    SalesOpportunityCalculations.Evaluation evaluation(String rate) { return new SalesOpportunityCalculations.Evaluation(new BigDecimal(rate), new SalesPipelineApprovalStandards.Values(new BigDecimal("15"), new BigDecimal("0.5"))); }
    Header header(String id, Input in, String status, String approval, String manual, BigDecimal profit, BigDecimal baseline, String projectId) {
        return new Header(id, in.opportunityName(), in.customerId(), "고객", in.salesPartId(), in.stageCode(), in.winningProbability(), manual, in.expectedAmount(), in.contractTypeCode(), in.startMonth().replace("-", ""), in.expectedMonths(), in.workplace(), in.nextAction(), in.nextActionDueDate(), in.memo(), status, profit, approval, baseline, null, null, null, projectId, "admin", now.plusSeconds(++sequence));
    }
    Input input(Header h, String stage, BigDecimal probability) { return new Input(h.opportunityName(), h.customerId(), h.salesPartId(), stage, probability, h.expectedAmount(), h.contractTypeCode(), "2026-11", 6, h.workplace(), h.nextAction(), h.nextActionDueDate(), h.memo(), List.of()); }
    NeedRow needRow(Need n) { return new NeedRow(n.needSn(), n.jobCode(), n.gradeCode(), n.requiredCount(), n.startMonth().replace("-", ""), n.months(), n.monthlyMm(), n.estimatedUnitPrice()); }
    ObjectNode valid() throws Exception { return (ObjectNode) json.readTree("""
        {"opportunityName":"새 기회","customerId":"customer","salesPartId":"part","stageCode":"FIND","winningProbability":10,"expectedAmount":60000000,"startMonth":"2026-11","needs":[{"jobCode":"BE","requiredCount":1,"startMonth":"2026-11","months":6,"monthlyMm":1.0}]}
        """); }
    String create() throws Exception { return json.readTree(mvc.perform(post(root).contentType("application/json").content(valid().toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("opportunityId").asText(); }
    ObjectNode version(String id) { var b = json.createObjectNode(); b.put("modifiedAt", headers.get(id).modifiedAt().toString()); return b; }
    ObjectNode update(String id) throws Exception { var b = valid(); b.put("modifiedAt", headers.get(id).modifiedAt().toString()); ((ObjectNode)b.path("needs").get(0)).put("needSn", 1); return b; }
    void putAction(String id, String action, ObjectNode body, int expected) throws Exception { mvc.perform(put(root + "/" + id + "/" + action).contentType("application/json").content(body.toString())).andExpect(status().is(expected)); }
    void postAction(String id, String action, ObjectNode body, int expected) throws Exception { mvc.perform(post(root + "/" + id + "/" + action).contentType("application/json").content(body.toString())).andExpect(status().is(expected)); }
    void state(String id, String status, String approval, String stage, String manual) { var h = headers.get(id); headers.put(id, header(id, input(h, stage, h.winningProbability()), status, approval, manual, h.profitRate(), h.approvedProfitRate(), h.projectId())); }
    @Test void publicCreateAndDetailKeepWholeFormAndNeed() throws Exception {
        var id = create();
        mvc.perform(get(root + "/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.opportunityName").value("새 기회"))
            .andExpect(jsonPath("$.data.needs[0].monthlyMm").value(1.0)).andExpect(jsonPath("$.data.needs[0].months").value(6))
            .andExpect(jsonPath("$.data.modifiedAt").isNotEmpty());
        assertThat(needs.get(id)).hasSize(1);
        var order = inOrder(mapper); order.verify(mapper).insert(any()); order.verify(mapper).insertNeed(any());
    }
    @Test void unknownDetailIs404() throws Exception { mvc.perform(get(root + "/missing")).andExpect(status().isNotFound()); }
    @Test void detailUsesTheSameFourteenDayBoundaryAsThePipeline() throws Exception {
        var id = create();
        var modified = headers.get(id).modifiedAt();
        when(calendar.now()).thenReturn(modified.plus(Duration.ofDays(14)));
        mvc.perform(get(root + "/" + id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stale").value(false));
        when(calendar.now()).thenReturn(modified.plus(Duration.ofDays(14)).plusSeconds(1));
        mvc.perform(get(root + "/" + id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stale").value(true));
    }
    @Test void probability101IsFieldErrorWithoutInsert() throws Exception {
        var body = valid().put("winningProbability", 101);
        mvc.perform(post(root).contentType("application/json").content(body.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("winningProbability"));
        verify(mapper, never()).insert(any());
    }
    @Test void probability100AndMmOneAreAccepted() throws Exception { mvc.perform(post(root).contentType("application/json").content(valid().put("winningProbability", 100).toString())).andExpect(status().isOk()); }
    @Test void exactThresholdIsNotWaiting() throws Exception { when(calculations.evaluate(anyList())).thenReturn(evaluation("15.0")); assertThat(headers.get(create()).approvalStatus()).isEqualTo("NONE"); }
    @Test void belowThresholdWaits() throws Exception { assertThat(headers.get(create()).approvalStatus()).isEqualTo("WAIT"); }
    @Test void updateRetainsNeedIdentityAndChangesMonths() throws Exception {
        String id = create(); var old = headers.get(id).modifiedAt(); var body = update(id).put("opportunityName", "수정"); ((ObjectNode)body.path("needs").get(0)).put("months", 8);
        mvc.perform(put(root + "/" + id).contentType("application/json").content(body.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.opportunityName").value("수정")).andExpect(jsonPath("$.data.needs[0].needSn").value(1)).andExpect(jsonPath("$.data.needs[0].months").value(8));
        assertThat(headers.get(id).modifiedAt()).isAfter(old); verify(mapper, never()).removeNeed(anyString(), anyInt());
    }
    @Test void invalidNeedLeavesExistingNeedsUnchanged() throws Exception {
        var id = create(); var before = List.copyOf(needs.get(id)); var body = update(id); ((ObjectNode)body.path("needs").get(0)).put("monthlyMm", 0);
        mvc.perform(put(root + "/" + id).contentType("application/json").content(body.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("needs[0].monthlyMm"));
        assertThat(needs.get(id)).isEqualTo(before); verify(mapper, never()).update(any());
    }
    @Test void staleUpdateCannotWriteNeeds() throws Exception { var id = create(); var old = headers.get(id); var body = update(id).put("modifiedAt", now.minusSeconds(1).toString()); mvc.perform(put(root + "/" + id).contentType("application/json").content(body.toString())).andExpect(status().isConflict()); assertThat(headers.get(id)).isEqualTo(old); verify(mapper, never()).updateNeed(any()); }
    @Test void rejectedSaveReentersWaitAboveThreshold() throws Exception { var id = create(); state(id, "ING", "REJ", "FIND", "N"); when(calculations.evaluate(anyList())).thenReturn(evaluation("30")); mvc.perform(put(root + "/" + id).contentType("application/json").content(update(id).toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.approvalStatusCode").value("WAIT")); }
    @Test void publicApproveAppendsHistoryAndReadsApprovedState() throws Exception { var id = create(); putAction(id, "approve", version(id), 200); mvc.perform(get(root + "/" + id)).andExpect(jsonPath("$.data.approvalStatusCode").value("OK")).andExpect(jsonPath("$.data.approvalHistory[1].actionCode").value("OK")); }
    @Test void repeatedApprovalDoesNotAppendHistory() throws Exception { var id = create(); putAction(id, "approve", version(id), 200); var count = histories.get(id).size(); putAction(id, "approve", version(id), 400); assertThat(histories.get(id)).hasSize(count); }
    @Test void reject300PreservesFullReasonAndTruncatesOnlyNotification() throws Exception { var id = create(); var reason = "가".repeat(300); putAction(id, "reject", version(id).put("reason", reason), 200); assertThat(histories.get(id).get(1).reason()).isEqualTo(reason); assertThat(notifications.get(0).text()).hasSize(300).endsWith("…"); assertThat(notifications.get(0).recipient()).isEqualTo("admin"); }
    @Test void reject301HasNoHistoryOrNotificationWrite() throws Exception { var id = create(); var count = histories.get(id).size(); putAction(id, "reject", version(id).put("reason", "가".repeat(301)), 400); assertThat(histories.get(id)).hasSize(count); assertThat(notifications).isEmpty(); }
    @Test void emptyReasonIsAllowedWithoutSuffix() throws Exception { var id = create(); putAction(id, "reject", version(id), 200); assertThat(notifications.get(0).text()).isEqualTo("「새 기회」 견적이 반려됐습니다"); assertThat(histories.get(id).get(1).reason()).isNull(); }
    @ParameterizedTest @ValueSource(strings = {"approve", "reject", "stage", "tentative-assignment"})
    void stalePutActionsHaveNoWrites(String action) throws Exception { var id = create(); var before = headers.get(id); var b = version(id).put("modifiedAt", now.minusSeconds(1).toString()); if (action.equals("stage")) b.put("stageCode", "PROP"); if (action.equals("tentative-assignment")) b.put("developerId", "developer").put("needSn", 1).put("startMonth", "2026-11"); var history = List.copyOf(histories.get(id)); putAction(id, action, b, 409); assertThat(headers.get(id)).isEqualTo(before); assertThat(histories.get(id)).isEqualTo(history); verify(mapper, never()).tentative(any()); verify(mapper, never()).approval(any()); }
    @ParameterizedTest @ValueSource(strings = {"approve", "reject", "stage", "tentative-assignment"})
    void missingVersionForPutIsFieldError(String action) throws Exception { var id = create(); mvc.perform(put(root + "/" + id + "/" + action).contentType("application/json").content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("modifiedAt")); }
    @Test void defaultStageProbabilityBecomes30() throws Exception { var id = create(); putAction(id, "stage", version(id).put("stageCode", "PROP"), 200); assertThat(headers.get(id).winningProbability()).isEqualByComparingTo("30"); }
    @Test void manuallyChosenProbabilitySurvivesStage() throws Exception { var id = create(); state(id, "ING", "NONE", "FIND", "Y"); putAction(id, "stage", version(id).put("stageCode", "PROP"), 200); assertThat(headers.get(id).winningProbability()).isEqualByComparingTo("10"); }
    @Test void unknownStageDoesNotWrite() throws Exception { var id = create(); var before = headers.get(id); putAction(id, "stage", version(id).put("stageCode", "UNKNOWN"), 400); assertThat(headers.get(id)).isEqualTo(before); }
    @ParameterizedTest @ValueSource(strings = {"WAIT", "REJ"})
    void waitingAndRejectedOnlyBlockForwardNegotiation(String approval) throws Exception {
        var id = create();
        state(id, "ING", approval, "PROP", "N");
        mvc.perform(put(root + "/" + id + "/stage").contentType("application/json")
            .content(version(id).put("stageCode", "NEGO").toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("「새 기회」은 견적 승인 전이라 협상 단계로 옮길 수 없습니다."));
        assertThat(headers.get(id).stageCode()).isEqualTo("PROP");
        putAction(id, "stage", version(id).put("stageCode", "PROP"), 200);
        putAction(id, "stage", version(id).put("stageCode", "FIND"), 200);
    }
    @ParameterizedTest @ValueSource(strings = {"lose", "stop", "resume", "win"})
    void stalePostActionsCannotWrite(String action) throws Exception { var id = create(); var b = action.equals("win") ? winBody(id) : version(id); if (action.equals("lose")) b.put("lossReasonCode", "PRICE"); b.put("modifiedAt", now.minusSeconds(1).toString()); var before = headers.get(id); postAction(id, action, b, 409); assertThat(headers.get(id)).isEqualTo(before); verify(projects, never()).create(any(), any()); verify(mapper, never()).releaseAll(anyString(), anyString()); }
    @ParameterizedTest @ValueSource(strings = {"stop", "resume"})
    void missingVersionForPostIsFieldError(String action) throws Exception { var id = create(); mvc.perform(post(root + "/" + id + "/" + action).contentType("application/json").content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("modifiedAt")); }
    @Test void publicStopRetainsRowAndReleasesActiveReservations() throws Exception { var id = create(); reserve(id, 1); postAction(id, "stop", version(id), 200); assertThat(headers.get(id).status()).isEqualTo("STOP"); assertThat(headers.get(id).resultDate()).isEqualTo(today); assertThat(reservations).isEmpty(); }
    @ParameterizedTest @ValueSource(strings = {"WIN", "LOSE"})
    void closedOpportunityCannotStop(String status) throws Exception { var id = create(); state(id, status, "NONE", "PRE", "N"); var before = headers.get(id); postAction(id, "stop", version(id), 400); assertThat(headers.get(id)).isEqualTo(before); }
    @Test void publicResumeClearsDateAndDoesNotRestoreReservations() throws Exception { var id = create(); reserve(id, 1); postAction(id, "stop", version(id), 200); postAction(id, "resume", version(id), 200); assertThat(headers.get(id).status()).isEqualTo("ING"); assertThat(headers.get(id).resultDate()).isNull(); assertThat(reservations).isEmpty(); }
    @Test void onlyStoppedOpportunityCanResume() throws Exception { var id = create(); postAction(id, "resume", version(id), 400); }
    @Test void publicLoseStoresReasonAndReleasesReservations() throws Exception { var id = create(); reserve(id, 1); postAction(id, "lose", version(id).put("lossReasonCode", "PRICE").put("memo", "가격"), 200); mvc.perform(get(root + "/" + id)).andExpect(jsonPath("$.data.lossReasonCode").value("PRICE")).andExpect(jsonPath("$.data.opportunityStatusCode").value("LOSE")); assertThat(reservations).isEmpty(); }
    @Test void missingLossReasonIsFieldErrorAndNoWrite() throws Exception { var id = create(); var before = headers.get(id); mvc.perform(post(root + "/" + id + "/lose").contentType("application/json").content(version(id).toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("lossReasonCode")); assertThat(headers.get(id)).isEqualTo(before); }
    void reserve(String id, int needSn) { reservations.add(new Reservation(++sequence, id, headers.get(id).opportunityName(), needSn, "developer", "202611", "ACT")); }
    ObjectNode tentativeBody(String id) { return version(id).put("developerId", "developer").put("needSn", 1).put("startMonth", "2026-11"); }
    @Test void checkReturnsOtherOpportunityWarningWithoutWrites() throws Exception { var old = create(); var id = create(); reserve(old, 1); var before = headers.get(id); mvc.perform(post(root + "/" + id + "/tentative-assignment-check").contentType("application/json").content(tentativeBody(id).toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.warnings[0].message").value(org.hamcrest.Matchers.containsString("새 기회"))); assertThat(headers.get(id)).isEqualTo(before); assertThat(reservations).hasSize(1); verify(mapper, never()).touch(anyString(), anyString()); }
    @Test void sameDeveloperAndConfirmedOneMmGenerateWarningsOnly() throws Exception { var id = create(); reserve(id, 2); when(mapper.confirmedMm("developer", "202611")).thenReturn(BigDecimal.ONE); mvc.perform(post(root + "/" + id + "/tentative-assignment-check").contentType("application/json").content(tentativeBody(id).toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.warnings.length()").value(3)); verify(mapper, never()).tentative(any()); }
    @Test void missingDeveloperIsFieldError() throws Exception { var id = create(); var b = tentativeBody(id); b.remove("developerId"); mvc.perform(post(root + "/" + id + "/tentative-assignment-check").contentType("application/json").content(b.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("developerId")); }
    @Test void missingNeedSnIsFieldError() throws Exception { var id = create(); var b = tentativeBody(id); b.remove("needSn"); mvc.perform(put(root + "/" + id + "/tentative-assignment").contentType("application/json").content(b.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("needSn")); }
    @Test void transferTouchesBothAndKeepsSameOpportunitySecondNeed() throws Exception { var old = create(); var id = create(); reserve(old, 1); reserve(id, 2); var previous = headers.get(old).modifiedAt(); var destination = headers.get(id).modifiedAt(); putAction(id, "tentative-assignment", tentativeBody(id), 200); assertThat(reservations).hasSize(2).allMatch(r -> id.equals(r.opportunityId())); assertThat(headers.get(old).modifiedAt()).isAfter(previous); assertThat(headers.get(id).modifiedAt()).isAfter(destination); putAction(id, "tentative-assignment", tentativeBody(id), 200); assertThat(reservations).hasSize(2); }
    ObjectNode winBody(String id) { return version(id).put("contractAmount", 60000000).put("contractTypeCode", "TK").put("contractStartDate", "2026-11-01").put("contractEndDate", "2027-04-30").put("projectStatusCode", "ING"); }
    @ParameterizedTest @ValueSource(strings = {"WAIT", "REJ"})
    void publicWinAllowsPendingOrRejectedAndHandsOffCandidates(String approval) throws Exception { var id = create(); state(id, "ING", approval, "PROP", "N"); reserve(id, 1); postAction(id, "win", winBody(id), 200); mvc.perform(get(root + "/" + id)).andExpect(jsonPath("$.data.projectId").value("project")).andExpect(jsonPath("$.data.opportunityStatusCode").value("WIN")); assertThat(reservations).allMatch(r -> "CNVT".equals(r.status())); verify(mapper).manpower(any()); verify(mapper, never()).releaseAll(eq(id), anyString()); }
    @Test void equalWinDatesRejectBeforeProjectCreation() throws Exception { var id = create(); var before = headers.get(id); mvc.perform(post(root + "/" + id + "/win").contentType("application/json").content(winBody(id).put("contractEndDate", "2026-11-01").toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("contractEndDate")); assertThat(headers.get(id)).isEqualTo(before); verify(projects, never()).create(any(), any()); }
}
