package com.company.common.codegroups.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

public final class CodeContracts {
    private CodeContracts() {}
    public record Group(String groupId, String groupName, String sysCodeYn, String valCodeYn, int codeCount) {}
    public record Groups(List<Group> items) {}
    public record Codes(List<Item> items) {}
    public record Deleted(String groupId, String code, String delYn) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        public String code;
        public String codeName;
        public Integer sortOrd;
        public String useYn;
        public BigDecimal codeVal;
        public String unitNm;
        public String relatedJobs;
        public BigDecimal estimateUnitPrice;
        public String requiredJobs;
        public BigDecimal weight;
        public String contractTypes;
        public String bankCode;
        public String targetField;
        public String job;
        /** 마지막으로 바뀐 시각(ISO UTC). 저장할 때 그대로 돌려보내 동시 수정을 막는다. */
        public String modifiedAt;
    }
}
