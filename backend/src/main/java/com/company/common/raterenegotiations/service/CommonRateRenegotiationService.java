package com.company.common.raterenegotiations.service;

import com.company.common.raterenegotiations.dto.RateRenegotiationContracts.*;
import com.company.common.raterenegotiations.mapper.RateRenegotiationMapper;
import com.company.common.support.service.BusinessCalendar;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.company.common.raterenegotiations.service.RateRenegotiationRules.*;
import static java.math.BigDecimal.ZERO;

@Service
public class CommonRateRenegotiationService {
    private final RateRenegotiationSource source;
    private final RateRenegotiationMapper mapper;
    private final BusinessCalendar calendar;

    public CommonRateRenegotiationService(RateRenegotiationSource source,
        RateRenegotiationMapper mapper, BusinessCalendar calendar) {
        this.source = source;
        this.mapper = mapper;
        this.calendar = calendar;
    }
    private record Candidate(Row row, BigDecimal exactRate, BigDecimal targetAnnual) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result getCommonRateRenegotiations(Filter request) {
        Filter filter = normalize(request);
        var context = source.read();
        var today = calendar.today();
        var candidates = new ArrayList<Candidate>();
        // Roles are read in the same snapshot at the future projection seam. This release is public fixed admin.
        java.util.Objects.requireNonNull(context.roles());
        for (var a : mapper.selectAssignmentInfo(context.yearMonth(), context.closed())) {
            var cost = context.costs().stream().filter(c -> a.assignmentId().equals(c.assignmentId())
                && a.developerId().equals(c.developerId()) && a.projectId().equals(c.projectId())).findFirst().orElse(null);
            if (cost == null || !eligible(a, cost.mm(), filter, today)) continue;
            BigDecimal revenue = a.currentUnitPrice().multiply(cost.mm());
            if (!belowThreshold(revenue, cost.labor(), filter.profitRateThreshold())) continue;
            Row row = row(a, cost.mm(), cost.labor(), context.targetRate(), filter.increaseRate(), today);
            candidates.add(new Candidate(row, exactProfit(revenue, cost.labor()), targetAnnual(row, a.contractMm())));
        }
        candidates.sort(Comparator.comparing(Candidate::exactRate));
        var people = new HashSet<String>();
        BigDecimal sumRates = ZERO, assumed = ZERO, target = ZERO;
        for (var c : candidates) {
            people.add(c.row().developerId());
            sumRates = sumRates.add(c.exactRate());
            assumed = assumed.add(c.row().annualProfitIncrease());
            target = target.add(c.targetAnnual());
        }
        BigDecimal average = candidates.isEmpty() ? null
            : sumRates.divide(BigDecimal.valueOf(candidates.size()), 1, RoundingMode.HALF_UP);
        var summary = new Summary(people.size(), average, context.targetRate(), assumed, target);
        String ym = context.yearMonth();
        return new Result(ym.substring(0, 4) + "-" + ym.substring(4), filter.increaseRate(),
            candidates.stream().map(Candidate::row).toList(), summary, assumed, context.help());
    }
}
