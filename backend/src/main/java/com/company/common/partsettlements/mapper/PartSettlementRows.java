package com.company.common.partsettlements.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class PartSettlementRows {
    private PartSettlementRows() {}
    public static class Part {
        public String partId, partName, hqYn, useYn, leaderName;
        public BigDecimal distributionRate, salesExpense;
    }
    public static class Header {
        public String partId;
        public String state, confirmedBy, confirmedByName, cancelReason;
        public BigDecimal card, profit;
        public Instant confirmedAt, modifiedAt;
    }
    public static class Input {
        public String developerId, developerName, partId, employeeTypeCode, payrollType;
        public String assignmentId, projectId, projectName, contractType, inputType;
        public LocalDate startDate, retireDate, firstStart;
        public BigDecimal mm, price, salary, vendorSupply, monthlySalary, distribution, allocatedCost, contractAmount, budget;
        public Boolean uploaded;
    }
    public static class History {
        public String tableName, target, item, before, after;
        public Instant at;
        public BigDecimal id;
    }
    public static class Detail {
        public String partId;
        public String developerId, developerName, employeeTypeCode, projectNames, notes;
        public BigDecimal monthlyMm, unitPrice, salary, salesExpense, retirementAmount, totalCost, profit, profitShare;
    }
}
