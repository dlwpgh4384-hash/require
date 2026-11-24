package com.company.common.salespipeline.service;

import com.company.common.clients.service.ClientRules;
import com.company.common.common.SalesPipelineCodes;
import com.company.common.salesopportunities.dto.SalesOpportunityContracts.Need;
import com.company.common.salesopportunities.service.SalesOpportunityRules;
import com.company.common.salespipeline.dto.SalesPipelineContracts.*;
import com.company.common.salespipeline.mapper.SalesPipelineMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
        var cards = rows.stream().map(row -> new Card(row.opportunityId(), row.opportunityName(),
            row.customerId(), row.customerName(), row.stageCode(), row.winningProbability(),
            row.expectedAmount(), row.startMonth(), row.nextAction(), row.nextActionDueDate(),
            mapper.requiredJobs(row.opportunityId()).stream().map(j -> j.jobCode()).toList(),
            Duration.between(row.modifiedAt(), calendar.now()).compareTo(Duration.ofDays(14)) > 0,
            row.approvalStatusCode(), row.modifiedAt())).toList();
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
                .reduce(BigDecimal.ZERO, BigDecimal::add)), List.copyOf(stages));
    }

    public Items<Closed> getCommonSalesPipelineClosed(String result, boolean includeStopped) {
        String filter = result == null || result.isBlank() ? null : result;
        if (filter != null && !Set.of("WIN", "LOSE", "STOP").contains(filter))
            throw CodeFailure.invalid("result", "종료 결과를 골라 주세요.");
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
