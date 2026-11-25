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
                case "CTRT_TYPE" -> List.of("TK", "MO", "DP", "IN"); case "LOSE_RSN" -> List.of("PRICE", "TECH", "SCHED", "CANCEL", "ETC");
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
        when(mapper.developerConversions(anyString())).thenAnswer(c -> reservations.stream().filter(r -> r.developerId().equals(c.getArgument(0)) && "CNVT".equals(r.status())).toList());
        when(mapper.developersReservations(anyCollection())).thenAnswer(c -> { Collection<String> ids = c.getArgument(0); return reservations.stream().filter(r -> ids.contains(r.developerId()) && "ACT".equals(r.status())).toList(); });
        when(mapper.confirmedMm(anyString(), anyString())).thenReturn(BigDecimal.ZERO);
        when(calculations.price(any())).thenAnswer(c -> ((Need) c.getArgument(0)).estimatedUnitPrice() == null ? new BigDecimal("10000000") : ((Need) c.getArgument(0)).estimatedUnitPrice());
        when(calculations.stageProbability(anyString())).thenAnswer(c -> new BigDecimal(switch ((String)c.getArgument(0)) { case "PROP" -> "30"; case "NEGO" -> "60"; case "PRE" -> "90"; default -> "10"; }));
        when(calculations.evaluate(anyList())).thenReturn(evaluation("14"));
        when(mapper.insert(any())).thenAnswer(c -> { Write w = c.getArgument(0); headers.put(w.id(), header(w.id(), w.input(), "ING", w.approval(), w.manualYn(), w.profit(), null, null)); return 1; });
        when(mapper.nextOpportunityNumber()).thenAnswer(c -> headers.keySet().stream().filter(k -> k.matches("O-[0-9]{4}"))
            .mapToInt(k -> Integer.parseInt(k.substring(2))).max().orElse(0) + 1);
        when(mapper.update(any())).thenAnswer(c -> { Write w = c.getArgument(0); var old = headers.get(w.id()); headers.put(w.id(), header(w.id(), w.input(), old.status(), w.approval(), w.manualYn(), w.profit(), old.approvedProfitRate(), old.projectId())); return 1; });
        when(mapper.insertNeed(any())).thenAnswer(c -> { NeedWrite w = c.getArgument(0); needs.computeIfAbsent(w.id(), k -> new ArrayList<>()).add(needRow(w.need())); return 1; });
        when(mapper.updateNeed(any())).thenAnswer(c -> { NeedWrite w = c.getArgument(0); needs.get(w.id()).removeIf(n -> n.needSn().equals(w.need().needSn())); needs.get(w.id()).add(needRow(w.need())); return 1; });
        when(mapper.removeNeed(anyString(), anyInt())).thenAnswer(c -> { needs.get(c.getArgument(0)).removeIf(n -> n.needSn().equals(c.getArgument(1))); return 1; });
        when(mapper.stage(any())).thenAnswer(c -> { StageWrite w = c.getArgument(0); var old = headers.get(w.id()); var in = input(old, w.stage(), w.probability()); headers.put(w.id(), header(w.id(), in, old.status(), old.approvalStatus(), old.manualYn(), old.profitRate(), old.approvedProfitRate(), old.projectId())); return 1; });
        when(mapper.approval(any())).thenAnswer(c -> { ApprovalWrite w = c.getArgument(0); var old = headers.get(w.id()); headers.put(w.id(), header(w.id(), input(old, old.stageCode(), old.winningProbability()), old.status(), w.status(), old.manualYn(), old.profitRate(), "OK".equals(w.status()) ? old.profitRate() : old.approvedProfitRate(), old.projectId())); return 1; });
        when(mapper.appendHistory(any())).thenAnswer(c -> { ApprovalWrite w = c.getArgument(0); histories.computeIfAbsent(w.id(), k -> new ArrayList<>()).add(new ApprovalHistory(w.status(), now.plusSeconds(++sequence), w.reason())); return 1; });
        when(mapper.notifyOwner(any())).thenAnswer(c -> { notifications.add(c.getArgument(0)); return 1; });
        when(mapper.result(any())).thenAnswer(c -> { ResultWrite w = c.getArgument(0); var old = headers.get(w.id()); var h = header(w.id(), input(old, old.stageCode(), old.winningProbability()), w.status(), old.approvalStatus(), old.manualYn(), old.profitRate(), old.approvedProfitRate(), old.projectId()); headers.put(w.id(), new Header(h.opportunityId(), h.opportunityName(), h.customerId(), h.customerName(), h.salesPartId(), h.stageCode(), h.winningProbability(), h.manualYn(), h.expectedAmount(), h.contractTypeCode(), h.startMonth(), h.expectedMonths(), h.workplace(), h.workAreaCode(), h.workAreaName(), h.nextAction(), h.nextActionDueDate(), h.memo(), h.status(), h.profitRate(), h.approvalStatus(), h.approvedProfitRate(), w.date(), w.reason(), w.memo(), h.projectId(), h.creator(), h.modifiedAt())); return 1; });
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
        return new Header(id, in.opportunityName(), in.customerId(), "고객", in.salesPartId(), in.stageCode(), in.winningProbability(), manual, in.expectedAmount(), in.contractTypeCode(), in.startMonth().replace("-", ""), in.expectedMonths(), in.workplace(), in.workAreaCode(), in.workAreaCode() == null ? null : "서울 중구 을지로동", in.nextAction(), in.nextActionDueDate(), in.memo(), status, profit, approval, baseline, null, null, null, projectId, "admin", now.plusSeconds(++sequence));
    }
    Input input(Header h, String stage, BigDecimal probability) { return new Input(h.opportunityName(), h.customerId(), h.salesPartId(), stage, probability, h.expectedAmount(), h.contractTypeCode(), "2026-11", 6, h.workplace(), h.workAreaCode(), h.nextAction(), h.nextActionDueDate(), h.memo(), List.of()); }
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
    // --- 점검 고침(UI-29) ---
    ObjectNode body(String stage, Object probability) throws Exception { var b = valid().put("stageCode", stage); if (probability == null) b.remove("winningProbability"); else b.put("winningProbability", new BigDecimal(probability.toString())); return b; }
    void createBody(ObjectNode b, int expected) throws Exception { mvc.perform(post(root).contentType("application/json").content(b.toString())).andExpect(status().is(expected)); }
    @ParameterizedTest @ValueSource(strings = {"NEGO", "PRE"})
    void pendingQuoteCannotBeCreatedAtNegotiationOrAbove(String stage) throws Exception {
        mvc.perform(post(root).contentType("application/json").content(body(stage, 60).toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("stageCode")).andExpect(jsonPath("$.message").value("「새 기회」은 견적 승인 전이라 협상 단계로 옮길 수 없습니다."));
        verify(mapper, never()).insert(any()); verify(mapper, never()).insertNeed(any());
    }
    @Test void quoteAboveThresholdMayBeCreatedAtNegotiation() throws Exception { when(calculations.evaluate(anyList())).thenReturn(evaluation("30")); createBody(body("NEGO", 60), 200); }
    @Test void editThatTurnsQuotePendingCannotMoveOrStayAtNegotiation() throws Exception {
        when(calculations.evaluate(anyList())).thenReturn(evaluation("30"));
        var id = create(); assertThat(headers.get(id).approvalStatus()).isEqualTo("NONE");
        when(calculations.evaluate(anyList())).thenReturn(evaluation("14"));
        var before = headers.get(id);
        mvc.perform(put(root + "/" + id).contentType("application/json").content(update(id).put("stageCode", "NEGO").toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("stageCode"));
        assertThat(headers.get(id)).isEqualTo(before); verify(mapper, never()).update(any());
        when(calculations.evaluate(anyList())).thenReturn(evaluation("30"));
        mvc.perform(put(root + "/" + id).contentType("application/json").content(update(id).put("stageCode", "NEGO").toString())).andExpect(status().isOk());
        assertThat(headers.get(id).stageCode()).isEqualTo("NEGO");
    }
    @Test void missingOrZeroProbabilityOnCreateIsStageDefaultAndNotManual() throws Exception {
        when(calculations.evaluate(anyList())).thenReturn(evaluation("30"));
        for (Object probability : new Object[] {null, 0}) {
            var response = mvc.perform(post(root).contentType("application/json").content(body("PROP", probability).toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var id = json.readTree(response).path("data").path("opportunityId").asText();
            assertThat(headers.get(id).winningProbability()).isEqualByComparingTo("30"); assertThat(headers.get(id).manualYn()).isEqualTo("N");
        }
    }
    @Test void probabilityEqualToStageDefaultIsNotManualAndOtherValueIs() throws Exception {
        when(calculations.evaluate(anyList())).thenReturn(evaluation("30"));
        var same = json.readTree(mvc.perform(post(root).contentType("application/json").content(body("PROP", 30).toString())).andReturn().getResponse().getContentAsString()).path("data").path("opportunityId").asText();
        assertThat(headers.get(same).manualYn()).isEqualTo("N");
        var other = json.readTree(mvc.perform(post(root).contentType("application/json").content(body("PROP", 45).toString())).andReturn().getResponse().getContentAsString()).path("data").path("opportunityId").asText();
        assertThat(headers.get(other).manualYn()).isEqualTo("Y"); assertThat(headers.get(other).winningProbability()).isEqualByComparingTo("45");
    }
    @Test void editStageChangeUsesNewDefaultUnlessProbabilityWasManual() throws Exception {
        when(calculations.evaluate(anyList())).thenReturn(evaluation("30"));
        var id = create();
        mvc.perform(put(root + "/" + id).contentType("application/json").content(update(id).put("stageCode", "PROP").toString())).andExpect(status().isOk());
        assertThat(headers.get(id).winningProbability()).isEqualByComparingTo("30"); assertThat(headers.get(id).manualYn()).isEqualTo("N");
        state(id, "ING", "NONE", "PROP", "Y");
        mvc.perform(put(root + "/" + id).contentType("application/json").content(update(id).put("stageCode", "NEGO").put("winningProbability", 30).toString())).andExpect(status().isOk());
        assertThat(headers.get(id).winningProbability()).isEqualByComparingTo("30"); assertThat(headers.get(id).manualYn()).isEqualTo("Y");
        var b = update(id).put("stageCode", "PRE"); b.remove("winningProbability");
        mvc.perform(put(root + "/" + id).contentType("application/json").content(b.toString())).andExpect(status().isOk());
        assertThat(headers.get(id).winningProbability()).isEqualByComparingTo("90"); assertThat(headers.get(id).manualYn()).isEqualTo("N");
    }
    @Test void internalContractTypeIsRejectedOnCreateEditAndWin() throws Exception {
        createBody(valid().put("contractTypeCode", "IN"), 400);
        verify(mapper, never()).insert(any());
        var id = create();
        mvc.perform(put(root + "/" + id).contentType("application/json").content(update(id).put("contractTypeCode", "IN").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("contractTypeCode"));
        mvc.perform(post(root + "/" + id + "/win").contentType("application/json").content(winBody(id).put("contractTypeCode", "IN").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("contractTypeCode"));
        verify(projects, never()).create(any(), any());
        for (String type : List.of("TK", "MO", "DP")) createBody(valid().put("contractTypeCode", type), 200);
    }
    void handOff(String id) { reservations.add(new Reservation(++sequence, id, headers.get(id).opportunityName(), 1, "developer", "202611", "CNVT")); }
    @Test void handedOffCandidateCannotBeTentativelyAssignedToAnotherOpportunity() throws Exception {
        var won = create(); handOff(won); state(won, "WIN", "NONE", "PRE", "N"); clearInvocations(mapper);
        var b = valid(); ((ObjectNode)b.path("needs").get(0)).putArray("tentativeDeveloperIds").add("developer");
        mvc.perform(post(root).contentType("application/json").content(b.toString())).andExpect(status().isConflict())
            .andExpect(jsonPath("$.errors[0].field").value("needs[0].tentativeDeveloperIds"))
            .andExpect(jsonPath("$.message").value("투입 배정으로 넘긴 후보입니다. 투입 상세에서 확인해 주세요."));
        verify(mapper, never()).insert(any());
        var id = create();
        mvc.perform(put(root + "/" + id + "/tentative-assignment").contentType("application/json").content(tentativeBody(id).toString())).andExpect(status().isConflict())
            .andExpect(jsonPath("$.errors[0].field").value("developerId"));
        var edit = update(id); ((ObjectNode)edit.path("needs").get(0)).putArray("tentativeDeveloperIds").add("developer");
        mvc.perform(put(root + "/" + id).contentType("application/json").content(edit.toString())).andExpect(status().isConflict());
        verify(mapper, never()).tentative(any());
        assertThat(reservations).singleElement().satisfies(r -> assertThat(r.status()).isEqualTo("CNVT"));
        mvc.perform(post(root + "/" + id + "/tentative-assignment-check").contentType("application/json").content(tentativeBody(id).toString())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.warnings[0].message").value("투입 배정으로 넘긴 후보입니다. 투입 상세에서 확인해 주세요."));
    }
    @Test void draftCheckWarnsForNewOpportunityWithoutNeedSnOrWrites() throws Exception {
        var other = create(); reserve(other, 1); when(mapper.confirmedMm("developer", "202612")).thenReturn(new BigDecimal("1.0")); clearInvocations(mapper, projects);
        mvc.perform(post(root + "/tentative-assignment-check").contentType("application/json").content("{\"developerId\":\"developer\",\"startMonth\":\"2026-12\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.warnings.length()").value(2))
            .andExpect(jsonPath("$.data.warnings[0].needSn").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.warnings[0].message").value("「새 기회」에 가배정되어 있습니다. 저장하면 이 기회로 옮깁니다."))
            .andExpect(jsonPath("$.data.warnings[1].message").value("해당 월에 1.0 MM 이상 확정 투입되어 있습니다."));
        verify(mapper, never()).tentative(any()); verify(mapper, never()).touch(anyString(), anyString()); verify(projects, never()).lockSources();
    }
    @Test void draftCheckForNewLineOfSavedOpportunityWarnsSameOpportunityAsTwoLines() throws Exception {
        var id = create(); reserve(id, 1);
        mvc.perform(post(root + "/tentative-assignment-check").contentType("application/json").content("{\"opportunityId\":\"" + id + "\",\"developerId\":\"developer\",\"startMonth\":\"2026-11\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.warnings.length()").value(2))
            .andExpect(jsonPath("$.data.warnings[0].message").value("같은 사람이 두 줄에 있습니다.")).andExpect(jsonPath("$.data.warnings[1].needSn").value(1));
    }
    @Test void draftCheckValidatesDeveloperMonthAndOpportunity() throws Exception {
        mvc.perform(post(root + "/tentative-assignment-check").contentType("application/json").content("{\"developerId\":\"missing\",\"startMonth\":\"2026-12\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("developerId"));
        mvc.perform(post(root + "/tentative-assignment-check").contentType("application/json").content("{\"developerId\":\"developer\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("startMonth"));
        mvc.perform(post(root + "/tentative-assignment-check").contentType("application/json").content("{\"developerId\":\"developer\",\"startMonth\":\"2026-12\",\"needSn\":1}")).andExpect(status().isBadRequest());
        mvc.perform(post(root + "/tentative-assignment-check").contentType("application/json").content("{\"opportunityId\":\"missing\",\"developerId\":\"developer\",\"startMonth\":\"2026-12\"}")).andExpect(status().isNotFound());
    }
    @Test void manpowerPlanAboveColumnLimitIsValidationNotServerError() throws Exception {
        var id = create(); when(mapper.manpowerPlans(id)).thenReturn(List.of(new BigDecimal("9999.99"), new BigDecimal("10000.00")));
        var before = headers.get(id);
        mvc.perform(post(root + "/" + id + "/win").contentType("application/json").content(winBody(id).toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("needs"));
        verify(projects, never()).create(any(), any()); verify(mapper, never()).manpower(any()); assertThat(headers.get(id)).isEqualTo(before);
        when(mapper.manpowerPlans(id)).thenReturn(List.of(new BigDecimal("9999.99")));
        postAction(id, "win", winBody(id), 200);
    }
    @Test void statusOnlyActionsLockOnlyTheOpportunityRow() throws Exception {
        var id = create(); clearInvocations(mapper, projects);
        putAction(id, "approve", version(id), 200);
        putAction(id, "stage", version(id).put("stageCode", "NEGO"), 200);
        postAction(id, "stop", version(id), 200);
        postAction(id, "resume", version(id), 200);
        postAction(id, "lose", version(id).put("lossReasonCode", "PRICE"), 200);
        verify(projects, never()).lockSources(); verify(mapper, never()).lockDevelopers(any()); verify(mapper, never()).lockOpportunities(any());
        verify(mapper, atLeast(5)).lock(id);
    }
    @Test void tentativeLocksProjectsThenThatPersonThenTouchedOpportunitiesInIdOrder() throws Exception {
        var old = create(); var id = create(); reserve(old, 1); clearInvocations(mapper, projects);
        putAction(id, "tentative-assignment", tentativeBody(id), 200);
        var order = inOrder(projects, mapper);
        order.verify(projects).lockSources(); order.verify(mapper).lockDevelopers(new TreeSet<>(Set.of("developer")));
        order.verify(mapper).lockOpportunities(new TreeSet<>(Set.of(old, id))); order.verify(mapper).lock(id); order.verify(mapper).tentative(any());
    }
    // --- 점검 고침 2(UI-29) ---
    @Test void newOpportunityIdsAreSequentialONumbersInsideTheLock() throws Exception {
        assertThat(create()).isEqualTo("O-0001");
        headers.put("Olegacy0123456789012", headers.get("O-0001"));
        assertThat(create()).isEqualTo("O-0002");
        var order = inOrder(projects, mapper);
        order.verify(projects).lockSources(); order.verify(mapper).nextOpportunityNumber(); order.verify(mapper).insert(any());
        when(mapper.nextOpportunityNumber()).thenReturn(10000); clearInvocations(mapper);
        mvc.perform(post(root).contentType("application/json").content(valid().toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("opportunityId"));
        verify(mapper, never()).insert(any());
    }
    @Test void blankUnitPriceIsStoredBlankAndOnlyTheCalculationUsesTheJobPrice() throws Exception {
        var id = create();
        assertThat(needs.get(id).get(0).estimatedUnitPrice()).isNull();
        mvc.perform(get(root + "/" + id)).andExpect(jsonPath("$.data.needs[0].estimatedUnitPrice").value(org.hamcrest.Matchers.nullValue()));
        verify(calculations).evaluate(argThat(list -> list.size() == 1 && list.get(0).estimatedUnitPrice() == null));
        var body = valid(); ((ObjectNode)body.path("needs").get(0)).put("estimatedUnitPrice", 8000000);
        var priced = json.readTree(mvc.perform(post(root).contentType("application/json").content(body.toString())).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString()).path("data").path("opportunityId").asText();
        assertThat(needs.get(priced).get(0).estimatedUnitPrice()).isEqualByComparingTo("8000000");
    }
    @Test void savingKeepsAnUnchangedReservationAndItsStartMonth() throws Exception {
        var b = valid(); ((ObjectNode)b.path("needs").get(0)).putArray("tentativeDeveloperIds").add("developer");
        var id = json.readTree(mvc.perform(post(root).contentType("application/json").content(b.toString())).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString()).path("data").path("opportunityId").asText();
        // 철수 리스트(UI-05)가 max(철수 다음 달, 줄 시작월)로 시작월을 바꿔 둔 가배정
        var stored = reservations.get(0);
        reservations.set(0, new Reservation(stored.tentativeId(), id, stored.opportunityName(), 1, "developer", "202612", "ACT"));
        clearInvocations(mapper);
        var edit = update(id).put("opportunityName", "고침"); ((ObjectNode)edit.path("needs").get(0)).putArray("tentativeDeveloperIds").add("developer");
        mvc.perform(put(root + "/" + id).contentType("application/json").content(edit.toString())).andExpect(status().isOk());
        verify(mapper, never()).tentative(any()); verify(mapper, never()).release(anyLong(), anyString());
        assertThat(reservations).singleElement().satisfies(r -> assertThat(r.startMonth()).isEqualTo("202612"));
        // 다른 줄로 옮기면 그 줄 시작월로 새로 쓴다.
        var move = update(id); ((com.fasterxml.jackson.databind.node.ArrayNode)move.path("needs")).addObject().put("jobCode", "PM").put("requiredCount", 1)
            .put("startMonth", "2027-01").put("months", 3).put("monthlyMm", 1).putArray("tentativeDeveloperIds").add("developer");
        mvc.perform(put(root + "/" + id).contentType("application/json").content(move.toString())).andExpect(status().isOk());
        verify(mapper).tentative(argThat(w -> w.needSn() == 2 && "2027-01".equals(w.startMonth())));
        assertThat(reservations).singleElement().satisfies(r -> { assertThat(r.needSn()).isEqualTo(2); assertThat(r.startMonth()).isEqualTo("202701"); });
    }
    @Test void approvalWithoutStoredProfitRateIsValidationNotServerError() throws Exception {
        var id = create(); var h = headers.get(id);
        headers.put(id, header(id, input(h, h.stageCode(), h.winningProbability()), "ING", "WAIT", "N", null, null, null));
        mvc.perform(put(root + "/" + id + "/approve").contentType("application/json").content(version(id).toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("예상 이익률을 다시 계산해 저장한 뒤 승인해 주세요."));
        mvc.perform(put(root + "/" + id + "/reject").contentType("application/json").content(version(id).toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("예상 이익률을 다시 계산해 저장한 뒤 반려해 주세요."));
        verify(mapper, never()).approval(any()); verify(mapper, times(1)).appendHistory(any());
    }
    @Test void needRowErrorsUseKoreanLabels() throws Exception {
        var id = create();
        var wrongRow = update(id); ((ObjectNode)wrongRow.path("needs").get(0)).put("needSn", 7);
        mvc.perform(put(root + "/" + id).contentType("application/json").content(wrongRow.toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("needs[0].needSn"))
            .andExpect(jsonPath("$.message").value("1번째 줄의 필요 인력 줄 칸을 확인해 주세요."));
        var unknown = update(id); ((ObjectNode)unknown.path("needs").get(0)).putArray("tentativeDeveloperIds").add("missing");
        mvc.perform(put(root + "/" + id).contentType("application/json").content(unknown.toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("needs[0].tentativeDeveloperIds"))
            .andExpect(jsonPath("$.message").value("1번째 줄의 가배정 인력 칸을 확인해 주세요."));
        var shape = valid(); ((ObjectNode)shape.path("needs").get(0)).put("tentativeDeveloperIds", "developer");
        mvc.perform(post(root).contentType("application/json").content(shape.toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("1번째 줄의 가배정 인력 칸을 확인해 주세요."));
    }
    @Test void detailCarriesStoredAndApprovedProfitRateAndApprovalStandard() throws Exception {
        when(calculations.approvalRate()).thenReturn(new BigDecimal("15"));
        var id = create();
        mvc.perform(get(root + "/" + id)).andExpect(jsonPath("$.data.expectedProfitRate").value(14.0))
            .andExpect(jsonPath("$.data.approvedProfitRate").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.approvalRate").value(15));
        mvc.perform(put(root + "/" + id + "/approve").contentType("application/json").content(version(id).toString())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.approvedProfitRate").value(14.0));
    }
    // 근무지 지역(2026-11-25): 고른 마지막 단계의 행정구역 코드. 사용 중인 코드만, 비우면 없음, 상세는 이름도 보인다.
    @Test void workAreaCodeIsSavedAndShownWithItsName() throws Exception {
        when(mapper.area("1114000000")).thenReturn(true);
        var id = json.readTree(mvc.perform(post(root).contentType("application/json").content(valid().put("workAreaCode", "1114000000").toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.workAreaCode").value("1114000000"))
            .andReturn().getResponse().getContentAsString()).path("data").path("opportunityId").asText();
        verify(mapper).insert(argThat(w -> "1114000000".equals(w.input().workAreaCode())));
        mvc.perform(get(root + "/" + id)).andExpect(jsonPath("$.data.workAreaName").value("서울 중구 을지로동"));
        mvc.perform(put(root + "/" + id).contentType("application/json").content(update(id).putNull("workAreaCode").toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.workAreaCode").doesNotExist());
        assertThat(headers.get(id).workAreaCode()).isNull();
    }
    @Test void unknownOrUnusedWorkAreaCodeIsKoreanFieldErrorWithoutWrite() throws Exception {
        for (String code : new String[]{"9999999999", "서울"}) {
            mvc.perform(post(root).contentType("application/json").content(valid().put("workAreaCode", code).toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("workAreaCode"))
                .andExpect(jsonPath("$.errors[0].message").value("근무지 지역을 목록에서 다시 골라 주세요."));
        }
        verify(mapper, never()).insert(any());
    }
    @Test void unchangedStoredWorkAreaCodeIsKeptEvenAfterTheAreaStopsBeingUsed() throws Exception {
        when(mapper.area("1114000000")).thenReturn(true);
        var id = json.readTree(mvc.perform(post(root).contentType("application/json").content(valid().put("workAreaCode", "1114000000").toString()))
            .andReturn().getResponse().getContentAsString()).path("data").path("opportunityId").asText();
        when(mapper.area("1114000000")).thenReturn(false);
        mvc.perform(put(root + "/" + id).contentType("application/json").content(update(id).put("workAreaCode", "1114000000").toString()))
            .andExpect(status().isOk());
        assertThat(headers.get(id).workAreaCode()).isEqualTo("1114000000");
    }
    @Test void winHandsTheStoredWorkAreaToProjectCreation() throws Exception {
        when(mapper.area("1114000000")).thenReturn(true);
        when(calculations.evaluate(anyList())).thenReturn(evaluation("30"));
        var id = json.readTree(mvc.perform(post(root).contentType("application/json").content(valid().put("workAreaCode", "1114000000").toString()))
            .andReturn().getResponse().getContentAsString()).path("data").path("opportunityId").asText();
        postAction(id, "win", winBody(id), 200);
        verify(projects).create(argThat(h -> "1114000000".equals(h.workAreaCode())), any());
    }
    @Test void equalWinDatesRejectBeforeProjectCreation() throws Exception { var id = create(); var before = headers.get(id); mvc.perform(post(root + "/" + id + "/win").contentType("application/json").content(winBody(id).put("contractEndDate", "2026-11-01").toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("contractEndDate")); assertThat(headers.get(id)).isEqualTo(before); verify(projects, never()).create(any(), any()); }
}
