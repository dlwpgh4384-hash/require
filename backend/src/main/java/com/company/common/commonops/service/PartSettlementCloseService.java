package com.company.common.commonops.service;

import com.company.common.commonops.dto.PartSettlementCloseContracts.Check;
import com.company.common.commonops.dto.PartSettlementCloseContracts.Item;
import com.company.common.commonops.dto.PartSettlementCloseContracts.Period;
import com.company.common.commonops.mapper.PartSettlementCloseMapper;
import com.company.common.support.service.CodeFailure;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartSettlementCloseService {
    private final PartSettlementCloseMapper mapper;

    public PartSettlementCloseService(PartSettlementCloseMapper mapper) {
        this.mapper = mapper;
    }

    /** 마감 점검과 자동확정의 재선택에 쓰는 동일 대상·상태·버전 조회. 쓰기 트랜잭션에서도 호출할 수 있다. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Check check(String yearMonth) {
        if (yearMonth == null || !yearMonth.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw CodeFailure.invalid("yearMonth", "년월은 YYYY-MM 형식으로 입력해 주세요.");
        }
        var month = YearMonth.parse(yearMonth);
        var period = new Period(month.format(DateTimeFormatter.ofPattern("uuuuMM")),
            month.plusMonths(1).atDay(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant());
        var items = mapper.selectTargets(period).stream()
            .map(row -> new Item(row.partId, row.confirmed, row.modifiedAt)).toList();
        int unconfirmed = (int) items.stream().filter(item -> !item.confirmed()).count();
        return new Check(yearMonth, items.size(), unconfirmed, items);
    }
}
