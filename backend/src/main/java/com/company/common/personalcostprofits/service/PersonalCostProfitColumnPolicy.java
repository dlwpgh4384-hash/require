package com.company.common.personalcostprofits.service;

import com.company.common.roles.dto.RoleContracts.Role;
import com.company.common.roles.service.RoleSensitivePolicy;
import com.company.common.support.service.CodeFailure;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PersonalCostProfitColumnPolicy {
    private PersonalCostProfitColumnPolicy() {}
    public static final List<String> KEYS = List.of("developerName", "affiliationName", "positionName",
        "employeeTypeName", "joinDate", "operatingRate", "mm", "unitPrice", "monthlySalary",
        "allowance", "companyInsurance", "retirementReserve", "personalCost", "teamExpenses",
        "projectExpenses", "totalCost", "operatingProfit", "profitRate", "personalInsurance",
        "withholdingTax", "incomeTax", "netPay");
    public static final List<String> LABELS = List.of("이름", "소속", "직위", "직원구분", "입사일",
        "가동률", "MM", "단가", "월 급여", "수당", "4대보험(회사)", "퇴직금 적립", "개인별 원가",
        "팀비", "프로젝트경비", "원가합계", "영업이익", "이익률", "4대보험(본인)",
        "3.3% 원천세", "근로소득세", "실지급액");
    public static final Set<String> REFERENCES = Set.of("personalInsurance", "withholdingTax", "incomeTax", "netPay");
    private static final Set<String> SENSITIVE = Set.of("monthlySalary", "personalCost");

    public static List<String> columns(List<String> requested, Collection<Role> roles) {
        if (requested != null && requested.stream().anyMatch(key -> !KEYS.contains(key))) {
            throw CodeFailure.invalid("columns", "등록된 열을 선택해 주세요.");
        }
        boolean visible = RoleSensitivePolicy.visible(roles);
        return KEYS.stream().filter(key -> requested == null ? !REFERENCES.contains(key) : requested.contains(key))
            .filter(key -> visible || !SENSITIVE.contains(key)).toList();
    }

    public static Map<String, Object> project(Map<String, Object> source, List<String> columns) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        for (String key : REFERENCES) if (!columns.contains(key)) result.remove(key);
        for (String key : SENSITIVE) if (!columns.contains(key)) result.remove(key);
        return result;
    }
}
