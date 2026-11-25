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
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AiRecommendationCodes {
    private static final Set<String> GROUPS = Set.of(
        "DATA_SCOPE", "CTRT_TYPE", "EMP_TYPE", "JOB", "GRADE", "JBPS", "INPUT_TYPE",
        "EXT_PSBL", "PROJ_STAT", "WORK_STAT", "SALES_STEP", "APRV_STAT", "LOSE_RSN",
        "RCMD_EXCL", "BANK", "INDUTY", "CLNT_TYPE", "DRCT_CLS", "ASGN_STAT",
        "OPP_STAT", "TENT_STAT", "RCMD_TYPE", "USER_ACT", "ACT", "SKILL"
    );
    private final CommonCodeService codes;

    public AiRecommendationCodes(CommonCodeService codes) {
        this.codes = codes;
    }

    public List<Item> selectable(String groupId) {
        return registered(groupId).stream().filter(item -> "Y".equals(item.useYn)).toList();
    }

    public String name(String groupId, String code) {
        return registered(groupId).stream().filter(item -> Objects.equals(code, item.code))
            .map(item -> item.codeName).findFirst()
            .orElseThrow(() -> CodeFailure.invalid("code", "등록된 공통코드를 확인해 주세요."));
    }

    private List<Item> registered(String groupId) {
        if (!GROUPS.contains(Objects.requireNonNullElse(groupId, ""))) {
            throw CodeFailure.invalid("groupId", "추천 공통코드 그룹을 확인해 주세요.");
        }
        return codes.getCommonCodes(groupId).items();
    }
}
