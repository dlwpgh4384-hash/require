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
    /** 열 순서 = 시안 C03(참고 열은 4대보험(회사) 뒤·퇴직금 적립 뒤에 섞여 있음). */
    public static final List<String> KEYS = List.of("developerName", "affiliationName", "positionName",
        "employeeTypeName", "joinDate", "operatingRate", "mm", "unitPrice", "monthlySalary",
        "allowance", "companyInsurance", "personalInsurance", "withholdingTax", "incomeTax",
        "retirementReserve", "netPay", "personalCost", "teamExpenses",
        "projectExpenses", "totalCost", "operatingProfit", "profitRate");
    /** 엑셀 제목 = 화면 열 제목(시안 C03, ⓘ 빼고 참고 열은 「 (참고)」). 연 누계 때 단가·월 급여는 label()로 바꾼다. */
    public static final List<String> LABELS = List.of("이름", "소속", "직위", "구분", "입사일",
        "가동률", "MM", "단가", "월 급여", "그룹장·팀장 수당", "4대보험(회사)", "4대보험(본인) (참고)",
        "3.3% 원천세 (참고)", "근로소득세 (참고)", "퇴직금 적립", "실지급액 (참고)", "개인별 원가",
        "팀비", "프로젝트경비", "원가합계", "영업이익", "이익률");
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

    /** 시안 hl(): 연 누계면 「단가」→「매출」, 「월 급여」→「급여」. 월 평균은 그대로. */
    public static String label(String key, String displayMode) {
        if (!"월평균".equals(displayMode)) {
            if ("unitPrice".equals(key)) return "매출";
            if ("monthlySalary".equals(key)) return "급여";
        }
        return LABELS.get(KEYS.indexOf(key));
    }

    public static Map<String, Object> project(Map<String, Object> source, List<String> columns) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        for (String key : REFERENCES) if (!columns.contains(key)) result.remove(key);
        for (String key : SENSITIVE) if (!columns.contains(key)) result.remove(key);
        return result;
    }
}
