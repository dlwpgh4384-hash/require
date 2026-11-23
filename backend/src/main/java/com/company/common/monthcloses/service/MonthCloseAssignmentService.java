package com.company.common.monthcloses.service;

import com.company.common.assignments.mapper.AssignmentMapper;
import com.company.common.assignments.mapper.AssignmentRows.MonthWrite;
import com.company.common.assignments.service.AssignmentMonthRules;
import com.company.common.assignments.service.AssignmentPricePolicy;
import com.company.common.support.service.BusinessCalendar;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class MonthCloseAssignmentService {
    private final AssignmentMapper assignments;
    private final AssignmentMonthRules rules;
    private final BusinessCalendar calendar;
    public MonthCloseAssignmentService(AssignmentMapper assignments, AssignmentMonthRules rules,
        BusinessCalendar calendar) {
        this.assignments = assignments;
        this.rules = rules;
        this.calendar = calendar;
    }
    public List<MonthWrite> calculate(YearMonth month, String actor) {
        return assignments.rows().stream()
            .filter(row -> "ACT".equals(row.statusCode()) && !row.startDate().isAfter(month.atEndOfMonth())
                && !row.endDate().isBefore(month.atDay(1)))
            .map(row -> {
                var history = assignments.history(row.assignmentId());
                var contractual = rules.effective(history, "RATE", month, row.monthlyMm());
                var price = rules.effective(history, "UPRC", month,
                    Objects.requireNonNullElse(row.billingPrice(), BigDecimal.ZERO));
                var mm = rules.mm(contractual, row.startDate(), row.endDate(), month);
                return new MonthWrite(row.assignmentId(), calendar.toDatabaseMonth(month), row.developerId(),
                    row.projectId(), rules.days(row.startDate(), row.endDate(), month), mm, price,
                    rules.sales(mm, price, AssignmentPricePolicy.billable(row.contractTypeCode(), row.inputTypeCode())), actor);
            }).toList();
    }
    public int overCount(YearMonth month) {
        return (int) calculate(month, null).stream().collect(java.util.stream.Collectors.toMap(
            MonthWrite::developerId, MonthWrite::mm, BigDecimal::add)).values().stream().filter(rules::over).count();
    }
}
