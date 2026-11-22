package com.company.common.commonops.service;

import com.company.common.common.BusinessPlanComparisonDefinitions;
import com.company.common.commonops.dto.BusinessPlanComparisonContracts.*;
import com.company.common.commonops.mapper.BusinessPlanComparisonMapper;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService;
import com.company.common.support.service.CodeFailure;
import com.company.common.users.dto.UserContracts.Response;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommonBusinessPlanComparisonService {
    private final BusinessPlanComparisonMapper targets;
    private final ProfitStructureMonthlyProfitService monthly;
    private final UserPreferencePeriodBasis periods;
    private final BusinessPlanComparisonDefinitions definitions;

    public CommonBusinessPlanComparisonService(BusinessPlanComparisonMapper targets,
        ProfitStructureMonthlyProfitService monthly, UserPreferencePeriodBasis periods,
        BusinessPlanComparisonDefinitions definitions) {
        this.targets = targets;
        this.monthly = monthly;
        this.periods = periods;
        this.definitions = definitions;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Response<Result> getCommonBusinessPlanComparisons(Filter filter) {
        if (filter.year() == null || filter.year() < 2026 || filter.year() > 9999)
            throw CodeFailure.invalid("year", "2026년 이후의 조회 연도를 선택해 주세요.");
        String ym = filter.baseMonth();
        if (ym == null || !ym.matches("[0-9]{4}(0[1-9]|1[0-2])")
            || Integer.parseInt(ym.substring(0, 4)) != filter.year())
            throw CodeFailure.invalid("baseMonth", "선택 연도와 같은 연도의 기준월을 선택해 주세요.");
        if (!(periods.metadata().get("periodBasis") instanceof UserPreferencePeriodBasis.Basis basis))
            throw CodeFailure.invalid("baseMonth", "기간 기준을 확인해 주세요.");
        if (ym.compareTo(basis.latestYm()) > 0)
            throw CodeFailure.invalid("baseMonth", "조회할 수 있는 기준월을 선택해 주세요.");
        definitions.definitions();
        int end = Integer.parseInt(ym.substring(4));
        var source = monthly.year(filter.year(), end, basis.cashBaseYm());
        var result = BusinessPlanComparisonProjection.of(filter, targets.targets(filter.year()), source,
            basis.closedMonths().contains(ym));
        return new Response<>(true, Map.of(), result);
    }
}
