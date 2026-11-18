package com.company.common.withdrawals.service;

import com.company.common.codegroups.dto.CodeContracts.Item;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.support.service.CodeFailure;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        return related(options("JOB"), developerJobCode, needJobCode);
    }

    static boolean related(List<Item> jobs, String developerJobCode, String needJobCode) {
        if (developerJobCode == null || needJobCode == null) return false;
        return jobs.stream().filter(item -> developerJobCode.equals(item.code))
            .map(item -> Objects.requireNonNullElse(item.relatedJobs, ""))
            .flatMap(value -> Arrays.stream(value.split(","))).map(String::trim)
            .anyMatch(needJobCode::equals);
    }

    /**
     * 한 요청 안에서 쓰는 공통코드 묶음. 그룹마다 한 번만 읽어 두고 줄·자리마다 다시 읽지 않는다
     * (목록 줄 수·추천 자리 수만큼 공통코드 표를 읽던 것을 막는다).
     */
    public static final class Snapshot {
        private final WithdrawalCodes codes;
        private final Map<String, List<Item>> groups = new HashMap<>();
        private final Map<String, String> extensionNames = new HashMap<>();

        public Snapshot(WithdrawalCodes codes) {
            this.codes = codes;
        }

        public List<Item> options(String groupId) {
            var cached = groups.get(groupId);
            if (cached == null) {
                cached = Objects.requireNonNullElse(codes.options(groupId), List.<Item>of());
                groups.put(groupId, cached);
            }
            return cached;
        }

        public String extensionName(String storedCode) {
            String key = Objects.requireNonNullElse(storedCode, "");
            return extensionNames.computeIfAbsent(key, value -> codes.extensionName(value.isEmpty() ? null : value));
        }

        public boolean relatedJob(String developerJobCode, String needJobCode) {
            return related(options("JOB"), developerJobCode, needJobCode);
        }

        public boolean hasJob(String jobCode) {
            return options("JOB").stream().anyMatch(item -> Objects.equals(jobCode, item.code));
        }

        public String jobName(String jobCode, String fallback) {
            return options("JOB").stream().filter(item -> Objects.equals(jobCode, item.code))
                .map(item -> item.codeName).findFirst().orElse(fallback);
        }

        /** 등급 순서(GRADE 정렬 순서). 모르는 등급이면 null. */
        public Integer gradeOrder(String gradeCode) {
            if (gradeCode == null) return null;
            return options("GRADE").stream().filter(item -> gradeCode.equals(item.code))
                .map(item -> item.sortOrd).filter(Objects::nonNull).findFirst().orElse(null);
        }
    }
}
