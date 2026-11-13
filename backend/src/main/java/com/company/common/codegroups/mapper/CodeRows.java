package com.company.common.codegroups.mapper;

import com.company.common.codegroups.dto.CodeContracts;
import java.math.BigDecimal;

public final class CodeRows {
    private CodeRows() {}
    public static class Group {
        public String groupId;
        public String groupName;
        public String sysCodeYn;
        public String valCodeYn;
        public int codeCount;
        public int sortOrd;
        public CodeContracts.Group value() {
            return new CodeContracts.Group(groupId, groupName, sysCodeYn, valCodeYn, codeCount);
        }
    }
    public static class Code {
        public String groupId;
        public String code;
        public String codeName;
        public int sortOrd;
        public String useYn;
        public String delYn;
        public BigDecimal codeVal;
        public String unitNm;
        public String addVal1;
        public String addVal2;
        public String addVal3;
        /** COALESCE(mod_dttm, reg_dttm): 동시 수정 검사에 쓴다. */
        public java.time.Instant changedAt;
        public CodeContracts.Item value() {
            var item = new CodeContracts.Item();
            item.code = code;
            item.modifiedAt = changedAt == null ? null : changedAt.toString();
            item.codeName = codeName;
            item.sortOrd = sortOrd;
            item.useYn = useYn;
            if ("ALWNC".equals(groupId)) {
                item.codeVal = codeVal;
                item.unitNm = unitNm;
            }
            switch (groupId) {
                case "JOB" -> {
                    item.relatedJobs = addVal1;
                    item.estimateUnitPrice = decimal(addVal2);
                }
                case "SKILL" -> item.requiredJobs = addVal1;
                case "PROJ_STEP" -> {
                    item.weight = decimal(addVal1);
                    item.contractTypes = addVal2;
                }
                case "BANK" -> item.bankCode = addVal1;
                case "RSM_LABEL" -> item.targetField = addVal1;
                case "RSM_JOB_KW" -> item.job = addVal1;
                default -> { }
            }
            return item;
        }
        private BigDecimal decimal(String value) {
            return value == null ? null : new BigDecimal(value);
        }
    }
}
