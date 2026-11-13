package com.company.common.controller;

import com.company.common.mapper.OperatingStandardRow;
import com.company.common.mapper.OperatingStandardsMapper;
import com.company.common.mapper.UserMapper;
import com.company.common.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.dto.OperatingStandardsContracts.Mutation;
import com.company.common.dto.UserContracts.History;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

final class OperatingStandardsTestData {
    final Map<String, OperatingStandardRow> rows = new LinkedHashMap<>();
    final List<History> histories = new ArrayList<>();
    final List<String> closed = new ArrayList<>();

    OperatingStandardsTestData(OperatingStandardsMapper mapper, UserMapper management,
        UserPreferencePeriodSourceMapper periods) {
        row("CASH_BASE_YM", "202601", "202601", "연월");
        row("CASH_BASE_AMT", "202601", "1150000000", "원");
        row("PART_DSTRB_RT", "202601", "40", "%");
        row("PART_SALES_EXPS", "202601", "1500000", "원");
        row("VAT_RT", "202601", "10", "%");
        row("TGT_UTIL_RT", "202601", "90", "%");
        row("TGT_MRGN_RT", "202601", "20", "%");
        row("LOW_MRGN_GAP", "202601", "5", "%p");
        row("DUN_D1", "202601", "7", "일");
        row("DUN_D2", "202601", "30", "일");
        row("DUN_D3", "202601", "60", "일");
        row("WTHDR_NOTI_D", "202601", "30", "일");
        row("EXT_PRBL_H", "202601", "80", "%");
        row("EXT_PRBL_M", "202601", "50", "%");
        row("EXT_PRBL_L", "202601", "0", "%");
        row("STEP_PRBL_1", "202601", "10", "%");
        row("STEP_PRBL_2", "202601", "30", "%");
        row("STEP_PRBL_3", "202601", "60", "%");
        row("STEP_PRBL_4", "202601", "90", "%");
        var ai = com.company.common.service.OperatingStandardsRules.AI;
        int[] weights = {25, 20, 15, 10, 5, 5, 10, 10};
        for (int i = 0; i < ai.size(); i++) row(ai.get(i), "202601", String.valueOf(weights[i]), "점");
        row("BDGT_WARN_RT", "202601", "80", "%");
        when(mapper.selectStandards()).thenAnswer(call -> new ArrayList<>(rows.values()));
        when(mapper.selectCurrentMonth()).thenReturn("202611");
        when(periods.selectClosedMonths()).thenAnswer(call -> new ArrayList<>(closed));
        doAnswer(call -> { histories.add(call.getArgument(0)); return null; })
            .when(management).insertHistory(any());
        when(mapper.insertStandard(any())).thenAnswer(call -> {
            Mutation value = call.getArgument(0);
            row(value.standardCode(), value.month(), value.value().toPlainString(), value.unit());
            rows.get(value.standardCode() + ":" + value.month()).standardName = value.standardName();
            return 1;
        });
        when(mapper.updateStandard(any())).thenAnswer(call -> {
            Mutation value = call.getArgument(0);
            rows.get(value.standardCode() + ":" + value.month()).value = value.value();
            return 1;
        });
        when(mapper.deleteStandard(any())).thenAnswer(call -> {
            Mutation value = call.getArgument(0);
            return rows.remove(value.standardCode() + ":" + value.month()) == null ? 0 : 1;
        });
    }
    void row(String code, String month, String value, String unit) {
        var row = new OperatingStandardRow();
        row.standardCode = code; row.month = month; row.standardName = code;
        row.value = new BigDecimal(value); row.unit = unit; row.description = null;
        rows.put(code + ":" + month, row);
    }
}
