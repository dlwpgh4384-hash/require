package com.company.common.netprofitstatements.service;

import com.company.common.netprofitstatements.dto.NetProfitStatementContracts.Contribution;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Code;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService.ProjectValue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public final class NetProfitStatementContributions {
    private NetProfitStatementContributions() {}

    public static List<Contribution> of(List<ProjectValue> projects, List<Code> codes) {
        var types = codes.stream().filter(c -> "CTRT_TYPE".equals(c.groupCode())
            && Set.of("TK", "MO", "DP").contains(c.code())).toList();
        var amounts = new TreeMap<String, BigDecimal>();
        types.forEach(c -> amounts.put(c.code(), BigDecimal.ZERO));
        for (var project : projects) {
            String code = project.project().contractType();
            if (amounts.containsKey(code)) {
                amounts.merge(code, project.revenue().subtract(project.allocatedCost()), BigDecimal::add);
            }
        }
        var denominator = amounts.values().stream().filter(a -> a.signum() > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var tenths = new HashMap<String, BigDecimal>();
        var remainders = new HashMap<String, BigDecimal>();
        if (denominator.signum() > 0) {
            amounts.forEach((code, amount) -> {
                if (amount.signum() > 0) {
                    var numerator = amount.multiply(BigDecimal.valueOf(1000));
                    var floor = numerator.divide(denominator, 0, RoundingMode.DOWN);
                    tenths.put(code, floor);
                    remainders.put(code, numerator.subtract(floor.multiply(denominator)));
                }
            });
            int remaining = 1000 - tenths.values().stream().mapToInt(BigDecimal::intValueExact).sum();
            var ordered = new ArrayList<>(remainders.keySet());
            ordered.sort(Comparator.<String, BigDecimal>comparing(remainders::get).reversed()
                .thenComparing(Comparator.naturalOrder()));
            for (int i = 0; i < remaining; i++) {
                tenths.merge(ordered.get(i), BigDecimal.ONE, BigDecimal::add);
            }
        }
        return types.stream().map(code -> {
            var amount = amounts.get(code.code());
            BigDecimal share = denominator.signum() == 0 || amount.signum() < 0 ? null
                : tenths.getOrDefault(code.code(), BigDecimal.ZERO).movePointLeft(1).setScale(1);
            return new Contribution(code.code(), code.name(), amount, share);
        }).toList();
    }
}
