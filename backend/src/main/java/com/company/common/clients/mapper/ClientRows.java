package com.company.common.clients.mapper;

import java.time.Instant;
import java.time.LocalDate;

public final class ClientRows {
    private ClientRows() {}
    public static class Client {
        public String clientId;
        public String clientTypeCode;
        public String clientName;
        public String businessNumber;
        public String representativeName;
        public String industryCode;
        public String contactName;
        public String contactPhone;
        public String taxEmail;
        public String bankCode;
        public String encryptedAccount;
        public String accountHolder;
        public String bankName;
        public String useYn;
        public Instant modifiedAt;
        public long projectCount;
        public long personCount;
    }
    public static class Assignment {
        public String projectId;
        public String projectName;
        public String personName;
        public String companyName;
        public String jobName;
        public LocalDate withdrawalDate;
    }
    public static class Change {
        public Instant changedAt;
        public String fieldName;
        public String beforeValue;
        public String afterValue;
        public String changedBy;
    }
}
