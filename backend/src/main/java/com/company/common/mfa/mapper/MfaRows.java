package com.company.common.mfa.mapper;

import java.time.Instant;

public final class MfaRows {
    private MfaRows() {}
    public static class Account {
        public String userId;
        public String email;
        public String status;
        public String codeHash;
        public Instant expiresAt;
        public int errors;
        public String temporary;
        public boolean administrator;
    }
}
