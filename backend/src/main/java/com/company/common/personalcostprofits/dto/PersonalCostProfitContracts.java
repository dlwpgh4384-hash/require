package com.company.common.personalcostprofits.dto;

import java.util.List;
import java.util.Map;

public final class PersonalCostProfitContracts {
    private PersonalCostProfitContracts() {}

    public record Filter(Integer year, Integer baseMonth, String displayMode, String name,
        String employeeTypeCode, String affiliationId, String salesPartId, String jobCode,
        String view, Boolean includeTurnkey, List<String> columns) {}

    public record Result(List<Map<String, Object>> items, Map<String, Object> summary,
        Map<String, Object> totals, List<Map<String, Object>> contributions) {}
    public record ExcelFile(byte[] bytes, String fileName, int count) {}
}
