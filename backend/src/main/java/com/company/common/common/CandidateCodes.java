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
public class CandidateCodes {
    private static final Set<String> GROUPS = Set.of("EMP_TYPE", "JOB", "GRADE", "CNTC_STAT", "CAND_SRC");
    private final CommonCodeService codes;

    public CandidateCodes(CommonCodeService codes) {
        this.codes = codes;
    }

    public String name(String groupId, String code) {
        return registered(groupId).stream().filter(item -> Objects.equals(code, item.code))
            .map(item -> item.codeName).findFirst()
            .orElseThrow(() -> CodeFailure.invalid("code", "등록된 공통코드를 확인해 주세요."));
    }

    public List<Item> selectable(String groupId) {
        return registered(groupId).stream().filter(item -> "Y".equals(item.useYn)).toList();
    }

    private List<Item> registered(String groupId) {
        if (!GROUPS.contains(Objects.requireNonNullElse(groupId, ""))) {
            throw CodeFailure.invalid("groupId", "후보 공통코드 그룹을 확인해 주세요.");
        }
        return codes.getCommonCodes(groupId).items();
    }
}
