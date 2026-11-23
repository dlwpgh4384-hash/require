package com.company.common.monthcloses.service;

import com.company.common.monthcloses.dto.MonthCloseContracts.StoredCheck;
import com.company.common.monthcloses.mapper.MonthCloseMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** [다시 점검] result rows (TB_CL_CLOSE_CHK) for an open month; its own short transaction. */
@Service
public class MonthCloseCheckRecorder {
    private final MonthCloseMapper mapper;

    public MonthCloseCheckRecorder(MonthCloseMapper mapper) { this.mapper = mapper; }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(String month, List<StoredCheck> checks, String actor) {
        if (checks == null || checks.isEmpty()) return;
        // TB_CL_CLOSE_CHK.CLOSE_YM references TB_CL_CLOSE; an open month gets a WORK row without a version.
        mapper.insertWorkMonth(month, actor);
        mapper.saveChecks(month, checks, actor);
    }
}
