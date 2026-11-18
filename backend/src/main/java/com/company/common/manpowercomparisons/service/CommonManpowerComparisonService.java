package com.company.common.manpowercomparisons.service;

import com.company.common.manpowercomparisons.dto.ManpowerComparisonContracts.*;
import com.company.common.manpowercomparisons.mapper.ManpowerComparisonMapper;
import com.company.common.projects.service.ProjectRules;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommonManpowerComparisonService {
    private final ManpowerComparisonMapper mapper;
    private final ManpowerComparisonRules rules;

    public CommonManpowerComparisonService(ManpowerComparisonMapper mapper, ManpowerComparisonRules rules) {
        this.mapper = mapper;
        this.rules = rules;
    }

    public Result getCommonManpowerComparisons(Filter filter) {
        if (filter.year() == null || filter.year() < 2025 || filter.year() > 2027) {
            throw CodeFailure.invalid("year", "2025년부터 2027년까지 선택해 주세요.");
        }
        if (filter.projectId() != null) ProjectRules.id(filter.projectId());
        if (filter.differenceStatus() != null
            && !Set.of("부족", "초과", "맞음").contains(filter.differenceStatus())) {
            throw CodeFailure.invalid("differenceStatus", "부족, 초과, 맞음 중 선택해 주세요.");
        }
        rules.requireDefinitions();
        var items = mapper.selectSources(filter).stream()
            .map(source -> rules.compare(source, filter.year()))
            .filter(row -> filter.differenceStatus() == null || filter.differenceStatus().equals(row.differenceStatus()))
            .sorted(Comparator.comparing(Row::differenceMm).thenComparing(Row::projectId).thenComparing(Row::jobCode))
            .toList();
        var planned = BigDecimal.ZERO;
        var actual = BigDecimal.ZERO;
        var difference = BigDecimal.ZERO;
        for (var row : items) {
            planned = planned.add(row.plannedMm());
            actual = actual.add(row.actualMm());
            difference = difference.add(row.differenceMm());
        }
        return new Result(items, new Totals(planned, actual, difference));
    }
}
