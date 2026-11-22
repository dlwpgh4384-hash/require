package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Code;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NetProfitStatementDefinitions {
    private final CalculationRuleService metadata;
    private final ProfitStructureMapper source;

    public NetProfitStatementDefinitions(CalculationRuleService metadata, ProfitStructureMapper source) {
        this.metadata = metadata;
        this.source = source;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Definition> definitions() {
        return List.of("식-00", "식-02", "식-14", "식-15", "식-28", "식-29", "식-31", "식-32", "식-34", "식-59")
            .stream().map(metadata::definition).toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Code> codes() {
        var groups = Set.of("CTRT_TYPE", "SGA_ITEM", "FIN_CLS", "COST_CLS");
        return source.codes().stream().filter(code -> groups.contains(code.groupCode())).toList();
    }
}
