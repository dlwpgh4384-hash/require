package com.company.common.withdrawals.service;

import com.company.common.codegroups.dto.CodeContracts.Item;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.support.service.CodeFailure;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WithdrawalCodes {
    private static final Set<String> GROUPS = Set.of(
        "CTRT_TYPE", "EMP_TYPE", "JOB", "GRADE", "JBPS", "INPUT_TYPE", "EXT_PSBL",
        "WORK_STAT", "SALES_STEP", "APRV_STAT", "ASGN_CHG_RSN", "LOSE_RSN", "RCMD_EXCL",
        "CHG_TYPE", "DRCT_CLS", "ASGN_STAT", "ASGN_CHG_TYPE", "OPP_STAT", "TENT_STAT",
        "RCMD_TYPE", "USER_ACT", "ACT");
    private final CommonCodeService codes;

    public WithdrawalCodes(CommonCodeService codes) {
        this.codes = codes;
    }

    public List<Item> options(String groupId) {
        if (!GROUPS.contains(Objects.requireNonNullElse(groupId, "")))
            throw CodeFailure.invalid("groupId", "등록된 코드 그룹을 골라 주세요.");
        return codes.getCommonCodes(groupId).items().stream()
            .filter(item -> "Y".equals(item.useYn)).toList();
    }

    public String extensionName(String storedCode) {
        if (storedCode == null || storedCode.isEmpty()) return "미정";
        return codes.getCommonCodes("EXT_PSBL").items().stream()
            .filter(item -> storedCode.equals(item.code)).map(item -> item.codeName)
            .findFirst().orElse(storedCode);
    }

    public String extensionStorageCode(String input) {
        if (input == null || input.length() > 10)
            throw CodeFailure.invalid("extensionPossibilityCode", "연장 가능성을 골라 주세요.");
        if (input.isEmpty()) return null;
        if (options("EXT_PSBL").stream().noneMatch(item -> input.equals(item.code)))
            throw CodeFailure.invalid("extensionPossibilityCode", "등록된 값을 골라 주세요.");
        return input;
    }

    public String extensionResponseCode(String storedCode) {
        return Objects.requireNonNullElse(storedCode, "");
    }

    public boolean relatedJob(String developerJobCode, String needJobCode) {
        if (developerJobCode == null || needJobCode == null) return false;
        return options("JOB").stream().filter(item -> developerJobCode.equals(item.code))
            .map(item -> Objects.requireNonNullElse(item.relatedJobs, ""))
            .flatMap(value -> Arrays.stream(value.split(","))).map(String::trim)
            .anyMatch(needJobCode::equals);
    }
}
