package com.company.common.operatingprofitstatements.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.operatingprofitstatements.dto.OperatingProfitStatementContracts.*;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService;
import com.company.common.support.service.CodeFailure;
import com.company.common.users.dto.UserContracts.Response;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommonOperatingProfitStatementService {
    public static final List<String> CALC_IDS = List.of("식-00", "식-02", "식-04", "식-09", "식-11",
        "식-13", "식-14", "식-15", "식-28", "식-29", "식-30", "식-31", "식-35", "식-55", "식-57", "식-59");
    private final ProfitStructureMonthlyProfitService monthly;
    private final UserPreferencePeriodBasis periods;
    private final CalculationRuleService calculation;
    public CommonOperatingProfitStatementService(ProfitStructureMonthlyProfitService monthly,
        UserPreferencePeriodBasis periods, CalculationRuleService calculation) {
        this.monthly = monthly;
        this.periods = periods;
        this.calculation = calculation;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Response<Result> getCommonOperatingProfitStatements(Filter filter) {
        if (filter.year() == null || filter.year() < 2025 || filter.year() > 9999)
            throw CodeFailure.invalid("year", "2025년 이후의 조회 연도를 선택해 주세요.");
        String ym = filter.baseMonth();
        if (ym == null || !ym.matches("[0-9]{4}(0[1-9]|1[0-2])")
            || Integer.parseInt(ym.substring(0, 4)) != filter.year())
            throw CodeFailure.invalid("baseMonth", "선택 연도와 같은 연도의 기준월을 선택해 주세요.");
        if (!(periods.metadata().get("periodBasis") instanceof UserPreferencePeriodBasis.Basis basis))
            throw CodeFailure.invalid("baseMonth", "기간 기준을 확인해 주세요.");
        if (ym.compareTo(basis.latestYm()) > 0)
            throw CodeFailure.invalid("baseMonth", "조회할 수 있는 기준월을 선택해 주세요.");
        CALC_IDS.forEach(calculation::definition);
        int end = Integer.parseInt(ym.substring(4));
        var source = new TreeMap<String, ProfitStructureMonthlyProfitService.Month>();
        source.putAll(monthly.year(filter.year(), end, basis.cashBaseYm()));
        source.putAll(monthly.year(filter.year() - 1, end == 1 ? 12 : end, basis.cashBaseYm()));
        return new Response<>(true, Map.of(), OperatingProfitStatementProjection.of(filter, source));
    }
}
