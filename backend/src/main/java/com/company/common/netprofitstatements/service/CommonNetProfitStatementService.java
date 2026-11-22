package com.company.common.netprofitstatements.service;

import com.company.common.common.NetProfitStatementDefinitions;
import com.company.common.netprofitstatements.dto.NetProfitStatementContracts.*;
import com.company.common.operatingprofitstatements.dto.OperatingProfitStatementContracts;
import com.company.common.operatingprofitstatements.service.OperatingProfitStatementProjection;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.profitstructures.mapper.ProfitStructureRows.Snapshot;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService.Month;
import com.company.common.support.service.CodeFailure;
import com.company.common.users.dto.UserContracts.Response;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.company.common.operatingprofitstatements.service.OperatingProfitStatementProjection.*;
import static com.company.common.profitstructures.service.ProfitStructureRules.ratio;

@Service
public class CommonNetProfitStatementService {
    private final ProfitStructureMonthlyProfitService monthly;
    private final UserPreferencePeriodBasis periods;
    private final NetProfitStatementDefinitions definitions;

    public CommonNetProfitStatementService(ProfitStructureMonthlyProfitService monthly,
        UserPreferencePeriodBasis periods, NetProfitStatementDefinitions definitions) {
        this.monthly = monthly;
        this.periods = periods;
        this.definitions = definitions;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Response<Result> getCommonNetProfitStatements(Filter filter) {
        if (filter.year() == null || filter.year() < 2025 || filter.year() > 9999) {
            throw CodeFailure.invalid("year", "2025년 이후의 조회 연도를 선택해 주세요.");
        }
        String ym = filter.baseMonth();
        if (ym == null || !ym.matches("[0-9]{4}(0[1-9]|1[0-2])")
            || Integer.parseInt(ym.substring(0, 4)) != filter.year()) {
            throw CodeFailure.invalid("baseMonth", "선택 연도와 같은 연도의 기준월을 선택해 주세요.");
        }
        if (!(periods.metadata().get("periodBasis") instanceof UserPreferencePeriodBasis.Basis basis)) {
            throw CodeFailure.invalid("baseMonth", "기간 기준을 확인해 주세요.");
        }
        if (ym.compareTo(basis.latestYm()) > 0) {
            throw CodeFailure.invalid("baseMonth", "조회할 수 있는 기준월을 선택해 주세요.");
        }
        definitions.definitions();
        var codes = definitions.codes();
        int end = Integer.parseInt(ym.substring(4));
        var source = new TreeMap<String, Month>();
        source.putAll(monthly.year(filter.year(), end, basis.cashBaseYm()));
        source.putAll(monthly.year(filter.year() - 1, end == 1 ? 12 : end, basis.cashBaseYm()));
        var base = source.get(ym);
        var current = window(source, filter.year(), end);
        var cumulative = total(current, end, Snapshot::netProfit);
        var revenue = total(current, end, Snapshot::revenue);
        var baseAmount = base == null ? null : base.values().netProfit();
        String lastKey = com.company.common.profitstructures.service.ProfitStructurePeriod.key(
            YearMonth.of(filter.year(), end).minusMonths(1));
        var previous = source.get(lastKey);
        var summary = new Summary(cumulative, ratio(cumulative, revenue), baseAmount,
            change(baseAmount, previous == null ? null : previous.values().netProfit()));
        boolean provisional = base != null && base.provisional();
        boolean firstYear = filter.year() <= Integer.parseInt(basis.cashBaseYm().substring(0, 4)) - 1;
        boolean visible = ym.compareTo(basis.cashBaseYm()) >= 0;
        var table = OperatingProfitStatementProjection.netProfitTable(
            new OperatingProfitStatementContracts.Filter(filter.year(), ym), source, firstYear);
        return new Response<>(true, Map.of(), new Result(filter.year(), ym, provisional,
            provisional ? end + "월은 아직 마감 전이라 요약 칸과 누계는 잠정 숫자입니다. 마감하면 확정됩니다." : null,
            summary, table, visible,
            visible ? NetProfitStatementContributions.of(base.projects(), codes) : List.of()));
    }
}
