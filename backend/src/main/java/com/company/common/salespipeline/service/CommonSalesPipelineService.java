package com.company.common.salespipeline.service;

import com.company.common.clients.service.ClientRules;
import com.company.common.common.SalesPipelineCodes;
import com.company.common.salesopportunities.dto.SalesOpportunityContracts.Need;
import com.company.common.salesopportunities.service.SalesOpportunityRules;
import com.company.common.salespipeline.dto.SalesPipelineContracts.*;
import com.company.common.salespipeline.mapper.SalesPipelineMapper;
import com.company.common.salespipeline.mapper.SalesPipelineRows.RequiredJob;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommonSalesPipelineService {
    private final SalesPipelineMapper mapper;
    private final SalesPipelineCodes codes;
    private final BusinessCalendar calendar;
    private final SalesPipelineCalculationRules calculations;

    public CommonSalesPipelineService(SalesPipelineMapper mapper, SalesPipelineCodes codes,
        BusinessCalendar calendar, SalesPipelineCalculationRules calculations) {
        this.mapper = mapper;
        this.codes = codes;
        this.calendar = calendar;
        this.calculations = calculations;
    }

    public Pipeline getCommonSalesPipeline() {
        calculations.definitions();
        var rows = mapper.ongoing();
        // 필요 직무는 진행 기회 전체를 한 번에 읽어 기회별로 나눈다(카드마다 따로 읽지 않음).
        var required = mapper.requiredJobs().stream().collect(Collectors.groupingBy(RequiredJob::opportunityId,
            LinkedHashMap::new, Collectors.mapping(j -> new JobHeadcount(j.jobCode(), j.jobName(), j.headcount()),
                Collectors.toList())));
        var cards = rows.stream().map(row -> {
            var jobs = required.getOrDefault(row.opportunityId(), List.of());
            return new Card(row.opportunityId(), row.opportunityName(),
                row.customerId(), row.customerName(), row.stageCode(), row.winningProbability(),
                row.expectedAmount(), row.startMonth(), row.nextAction(), row.nextActionDueDate(),
                jobs.stream().map(JobHeadcount::jobCode).toList(),
                Duration.between(row.modifiedAt(), calendar.now()).compareTo(Duration.ofDays(14)) > 0,
                row.approvalStatusCode(), row.modifiedAt(), List.copyOf(jobs));
        }).toList();
        var stages = new ArrayList<Stage>();
        for (var stage : codes.selectable("SALES_STEP")) {
            var items = cards.stream().filter(c -> stage.code.equals(c.stageCode())).toList();
            String suffix = switch (stage.code) {
                case "FIND" -> "1"; case "PROP" -> "2"; case "NEGO" -> "3"; case "PRE" -> "4";
                default -> throw CodeFailure.invalid("stageCode", "영업 단계를 확인해 주세요.");
            };
            stages.add(new Stage(stage.code, items.size(), items.stream().map(Card::expectedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add), items, stage.codeName,
                calculations.standard("STEP_PRBL_" + suffix)));
        }
        return new Pipeline(new Summary(cards.size(), Math.toIntExact(cards.stream().filter(Card::stale).count()),
            rows.stream().map(r -> r.expectedAmount()).reduce(BigDecimal.ZERO, BigDecimal::add),
            rows.stream().map(r -> r.expectedAmount().multiply(r.winningProbability()).movePointLeft(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add),
            rows.stream().map(r -> r.manpower().multiply(r.winningProbability()).movePointLeft(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add), mapper.closedCount()), List.copyOf(stages));
    }

    public Items<Closed> getCommonSalesPipelineClosed(String result, boolean includeStopped) {
        String filter = result == null || result.isBlank() ? null : result;
        if (filter != null && !Set.of("WIN", "LOSE", "STOP").contains(filter))
            throw CodeFailure.invalid("result", "종료 결과를 골라 주세요.");
        // 종료 탭은 결과(전체·수주·실주)와 [사용 중지 포함]을 따로 보낸다. 사용 중지만 고르고 포함을 끄면
        // 빈 목록이 조용히 나가므로 입력 오류로 알린다.
        if ("STOP".equals(filter) && !includeStopped)
            throw CodeFailure.invalid("includeStopped", "사용 중지한 기회는 [사용 중지 포함]을 켠 뒤 볼 수 있습니다.");
        return new Items<>(mapper.closed(new ClosedFilter(filter, includeStopped)));
    }

    public Quote postCommonSalesPipelineQuote(JsonNode body) {
        ClientRules.object(body, Set.of("needs"));
        var array = body.get("needs");
        if (array == null || !array.isArray() || array.isEmpty() || array.size() > 999)
            throw SalesOpportunityRules.invalid("needs");
        var needs = new ArrayList<Need>();
        for (int i = 0; i < array.size(); i++) {
            try { needs.add(SalesOpportunityRules.need(array.get(i))); }
            catch (CodeFailure failure) {
                throw CodeFailure.invalid("needs[" + i + "]." + failure.getField(),
                    (i + 1) + "번째 줄의 입력 값을 확인해 주세요.");
            }
        }
        return calculations.quote(List.copyOf(needs));
    }
}
