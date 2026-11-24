package com.company.common.manpowerboard.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ManpowerBoardContracts {
    private ManpowerBoardContracts() {}

    public record Filter(Integer year, String jobCode, String gradeCode,
        String employeeTypeCode, boolean onlyAvailable) {}
    public record Result(int year, Summary summary, List<Person> items,
        List<MonthTotal> monthTotals, int totalCount) {}
    public record Summary(int idleCount, BigDecimal availableMm, int withdrawalCount,
        int highExtensionCount, int concurrentCount, int overAllocatedCount,
        BigDecimal futureAvailableMm) {}
    public record Person(String developerId, String name, String jobName, List<Cell> cells) {}
    public record Cell(String month, String statusName, BigDecimal totalMm, List<Project> projects,
        String extensionPossibilityName, BigDecimal winningProbability, boolean tentative) {}
    public record Project(String projectName, BigDecimal monthlyMm) {}
    public record MonthTotal(String month, Integer idleCount, BigDecimal availableMm) {}
    public record ExcelFile(byte[] bytes, String fileName, int count) {}
}
