package com.company.common.common;

import com.company.common.codegroups.dto.CodeContracts.Item;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.support.service.CodeFailure;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MonthCloseCodes {
    private static final Set<String> GROUPS = Set.of(
        "CTRT_TYPE", "EMP_TYPE", "JOB", "INPUT_TYPE", "EXT_PSBL", "SGA_ITEM", "FIN_CLS",
        "CLOSE_STAT", "DRCT_CLS", "ASGN_STAT", "COST_CLS", "SETL_STAT", "ACT", "CHK_ITEM", "CHK_STAT");
    private final CommonCodeService codes;

    public MonthCloseCodes(CommonCodeService codes) {
        this.codes = codes;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public String name(String groupId, String code) {
        return registered(groupId).stream().filter(item -> Objects.equals(code, item.code))
            .map(item -> item.codeName).findFirst()
            .orElseThrow(() -> CodeFailure.invalid("code", "등록된 공통코드를 확인해 주세요."));
    }

    private List<Item> registered(String groupId) {
        if (!GROUPS.contains(Objects.requireNonNullElse(groupId, ""))) {
            throw CodeFailure.invalid("groupId", "월 마감 공통코드 그룹을 확인해 주세요.");
        }
        return codes.getCommonCodes(groupId).items();
    }
}
