package com.company.common.parts;

import com.company.common.dto.UserContracts.History;
import com.company.common.mapper.UserMapper;
import com.company.common.mapper.UserRows;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

final class PartTestData {
    final Map<String, PartRows.Master> masters = new LinkedHashMap<>();
    final Map<String, PartRows.Period> rows = new LinkedHashMap<>();
    final List<History> histories = new ArrayList<>();
    final List<PartRows.Leader> leaders = new ArrayList<>();
    String now = "202609";
    String confirmed;
    long revision;

    PartTestData(PartMapper mapper, UserMapper management) {
        leader("leader-a"); leader("leader-b");
        master("part-a", "기존 파트");
        period("part-a", "202601", null, "leader-a", "40", "1500000");
        when(mapper.selectCurrentMonth()).thenAnswer(call -> now);
        when(mapper.selectLeaders()).thenAnswer(call -> leaders);
        when(mapper.selectMaster(anyString())).thenAnswer(call -> copy(masters.get(call.getArgument(0))));
        when(mapper.selectParts(anyString())).thenAnswer(call -> masters.values().stream().map(this::copy).toList());
        when(mapper.selectPeriods(anyString())).thenAnswer(call -> rows.values().stream()
            .filter(row -> row.partId.equals(call.getArgument(0))).map(this::copy).toList());
        when(mapper.nameExists(anyString(), nullable(String.class))).thenAnswer(call -> masters.values().stream()
            .anyMatch(row -> row.partName.equals(call.getArgument(0)) && !row.partId.equals(call.getArgument(1))));
        when(mapper.insertPart(anyString(), anyString())).thenAnswer(call -> {
            master(call.getArgument(0), call.getArgument(1)); return 1;
        });
        when(mapper.insertPeriod(any())).thenAnswer(call -> {
            PartContracts.PeriodMutation value = call.getArgument(0);
            period(value.partId(), value.month(), value.endMonth(), value.values().leaderUserId(),
                value.values().distributionRate().toPlainString(), value.values().salesExpense().toPlainString());
            return 1;
        });
        when(mapper.updatePeriod(any())).thenAnswer(call -> {
            PartContracts.PeriodMutation value = call.getArgument(0);
            period(value.partId(), value.month(), value.endMonth(), value.values().leaderUserId(),
                value.values().distributionRate().toPlainString(), value.values().salesExpense().toPlainString());
            return 1;
        });
        when(mapper.endPeriod(anyString(), anyString(), anyString())).thenAnswer(call -> {
            rows.get(call.getArgument(0) + ":" + call.getArgument(1)).endMonth = call.getArgument(2); return 1;
        });
        when(mapper.touch(anyString(), nullable(Instant.class))).thenAnswer(call -> {
            masters.get(call.getArgument(0)).modifiedAt = Instant.parse("2026-09-01T00:00:00Z").plusSeconds(++revision);
            return 1;
        });
        when(mapper.rename(anyString(), anyString())).thenAnswer(call -> {
            masters.get(call.getArgument(0)).partName = call.getArgument(1); return 1;
        });
        when(mapper.stop(anyString())).thenAnswer(call -> {
            masters.get(call.getArgument(0)).useYn = "N"; return 1;
        });
        when(mapper.selectLastConfirmed(anyString(), anyString())).thenAnswer(call -> confirmed);
        doAnswer(call -> { histories.add(call.getArgument(0)); return null; }).when(management).insertHistory(any());
        when(mapper.selectHistory(anyString())).thenAnswer(call -> histories.stream()
            .filter(h -> h.key().equals(call.getArgument(0)) || h.key().startsWith(call.getArgument(0) + ":"))
            .map(h -> {
                var row = new UserRows.Change(); row.changedAt = Instant.parse("2026-09-01T00:00:00Z");
                row.item = h.item(); row.before = h.before(); row.after = h.after(); row.changedBy = "admin";
                return row;
            }).toList());
    }
    void leader(String id) {
        var row = new PartRows.Leader(); row.userId = id; row.userName = id; leaders.add(row);
    }
    void master(String id, String name) {
        var row = new PartRows.Master(); row.partId = id; row.partName = name;
        row.useYn = "Y"; row.personCount = 0L; row.projectCount = 0L; masters.put(id, row);
    }
    void period(String id, String month, String end, String leader, String rate, String expense) {
        var row = new PartRows.Period(); row.partId = id; row.month = month; row.endMonth = end;
        row.leaderUserId = leader; row.leaderName = leader; row.distributionRate = new BigDecimal(rate);
        row.salesExpense = new BigDecimal(expense); rows.put(id + ":" + month, row);
    }
    private PartRows.Master copy(PartRows.Master old) {
        if (old == null) return null;
        var row = new PartRows.Master(); row.partId = old.partId; row.partName = old.partName;
        row.useYn = old.useYn; row.modifiedAt = old.modifiedAt;
        row.personCount = old.personCount; row.projectCount = old.projectCount; return row;
    }
    private PartRows.Period copy(PartRows.Period old) {
        var row = new PartRows.Period(); row.partId = old.partId; row.month = old.month;
        row.endMonth = old.endMonth; row.leaderUserId = old.leaderUserId; row.leaderName = old.leaderName;
        row.distributionRate = old.distributionRate; row.salesExpense = old.salesExpense; return row;
    }
}
