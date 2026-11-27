package com.company.common.developers.service;

import com.company.common.users.service.UserScopePolicy.Scope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/** UI-22 인증 연결과 분리한 개발자 업무 및 후속 소비자 정책. */
public final class DeveloperBusinessPolicy {
    private DeveloperBusinessPolicy() {}

    /** sensitive = 역할의 민감 체크(SNSTV_VIEW_YN, 역할 여럿이면 하나라도 Y). 역할 이름으로 정하지 않는다(명세 ⑨-1). */
    public record Actor(Set<String> roles, Scope scope, boolean sensitive) {
        public Actor {
            roles = Set.copyOf(roles);
            Objects.requireNonNull(scope);
        }
    }

    public record StartingSalary(BigDecimal amount, String note) {}

    public static boolean canReadDeveloper(Actor actor, String partId) {
        return actor.scope().all()
            || (partId != null && actor.scope().partIds().contains(partId));
    }

    /** 월 단가: 민감 체크만. 파트장 자기 파트 예외도 없다(명세 ⑨-1). */
    public static boolean canReadMonthlyRate(Actor actor, String partId) {
        return canReadDeveloper(actor, partId) && actor.sensitive();
    }

    public static boolean canReadMonthlyPay(Actor actor, String partId) {
        return canReadDeveloper(actor, partId) && actor.sensitive();
    }

    public static boolean canManageStatuses(Set<String> roles) {
        return roles.contains("ADMIN") || roles.contains("FIN");
    }

    public static boolean canCancelRetirement(Set<String> roles, LocalDate retirementDate,
        LocalDate today, boolean closedMonth) {
        Objects.requireNonNull(retirementDate);
        Objects.requireNonNull(today);
        if (closedMonth) return false;
        return retirementDate.isBefore(today) ? roles.contains("ADMIN") : canManageStatuses(roles);
    }

    public static boolean canUploadPayroll(Set<String> roles) {
        return roles.contains("FIN");
    }

    public static boolean retired(LocalDate retirementDate, LocalDate today) {
        Objects.requireNonNull(today);
        return retirementDate != null && retirementDate.isBefore(today);
    }

    public static boolean canRegisterStatus(Set<String> roles, LocalDate retirementDate,
        LocalDate today) {
        return canManageStatuses(roles) && !retired(retirementDate, today);
    }

    public static boolean validDirectClass(String directClassCode, boolean managementStaff) {
        return "DRCT".equals(directClassCode) || ("INDRCT".equals(directClassCode) && managementStaff);
    }

    /** 투입 후보·운영 보드·AI·가동률·인원·개인 원가·수익표에 공통 적용한다. */
    public static boolean includedInDirectWorkforce(String directClassCode) {
        return "DRCT".equals(directClassCode);
    }

    public static boolean classifiedAsSellingAdministrativeCost(String directClassCode) {
        return "INDRCT".equals(directClassCode);
    }

    /** 비어 있는 기본 단가는 월단위·파견에 한해 민감 체크가 있는 사람의 명시 입력이 필요하다(명세 ⑨-5). */
    public static boolean canAssignWithRate(boolean sensitive, String contractTypeCode,
        BigDecimal monthlyRate, BigDecimal enteredRate) {
        if (!Set.of("TK", "MO", "DP", "IN").contains(contractTypeCode)) return false;
        if (!"MO".equals(contractTypeCode) && !"DP".equals(contractTypeCode)) return true;
        if (monthlyRate != null) return monthlyRate.signum() >= 0;
        return enteredRate != null && enteredRate.signum() >= 0 && sensitive;
    }

    /** 식-42 시작월에만 쓰며 B.1 월 원가를 대체하지 않는다. */
    public static StartingSalary startingSalary(BigDecimal monthlyPay) {
        return monthlyPay == null ? new StartingSalary(BigDecimal.ZERO, "⚠ 월 급여 없음")
            : new StartingSalary(monthlyPay, null);
    }
}
