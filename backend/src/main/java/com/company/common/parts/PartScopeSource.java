package com.company.common.parts;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Database sources for project choices, effective leaders and part settlement periods. */
@Service
@Transactional(readOnly = true)
public class PartScopeSource {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("uuuuMM");
    private final CommonPartService parts;
    private final PartMapper mapper;

    public PartScopeSource(CommonPartService parts, PartMapper mapper) {
        this.parts = parts;
        this.mapper = mapper;
    }

    public List<PartContracts.Part> availableForProject() {
        return parts.getCommonParts().items().stream().filter(part -> "Y".equals(part.useYn())).toList();
    }

    public Optional<PartRows.Period> settlementStandard(String partId, YearMonth month) {
        if (month == null) throw PartRules.invalid("effectiveMonth", "적용 월을 확인해 주세요.");
        String value = month.format(MONTH);
        return mapper.selectPeriods(partId).stream().filter(row -> row.month.compareTo(value) <= 0
            && (row.endMonth == null || row.endMonth.compareTo(value) >= 0))
            .max(Comparator.comparing(row -> row.month));
    }

    public Optional<String> leaderAt(String partId, Instant instant) {
        return settlementStandard(partId, YearMonth.from(instant.atZone(ZoneId.of("Asia/Seoul"))))
            .map(row -> row.leaderUserId);
    }
}
