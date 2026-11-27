package com.company.common.mfa.mapper;

import java.time.Instant;

public final class MfaRows {
    private MfaRows() {}
    public static class Account {
        public String userId;
        /** 사용자 이름(USER_NM). 로그인 응답 userName에 싣는다. */
        public String name;
        public String email;
        public String status;
        public String codeHash;
        public Instant expiresAt;
        public int errors;
        public String temporary;
        public boolean administrator;
    }
}
