package com.company.common.parts.mapper;

import java.math.BigDecimal;
import java.time.Instant;

public final class PartRows {
    private PartRows() {}
    public static class Master {
        public String partId;
        public String partName;
        public String useYn;
        public Instant modifiedAt;
        public Long personCount;
        public Long projectCount;
    }
    public static class Period {
        public String partId;
        public String month;
        public String endMonth;
        public String leaderUserId;
        public String leaderName;
        public BigDecimal distributionRate;
        public BigDecimal salesExpense;
    }
    public static class Leader {
        public String userId;
        public String userName;
    }
}
