package com.company.common.mfa.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MfaMapper {
    MfaRows.Account lockAccount(@Param("userId") String userId);
    void challenge(@Param("userId") String userId, @Param("hash") String hash,
        @Param("expiresAt") Instant expiresAt);
    void disable(@Param("userId") String userId);
    void wrongCode(@Param("userId") String userId);
    void clear(@Param("userId") String userId);
    void complete(@Param("userId") String userId, @Param("now") Instant now);
    void loginAccess(@Param("userId") String userId, @Param("temporary") boolean temporary,
        @Param("clientIp") String clientIp, @Param("now") Instant now);
    void failureAccess(@Param("userId") String userId, @Param("target") String target,
        @Param("clientIp") String clientIp, @Param("now") Instant now);
}
