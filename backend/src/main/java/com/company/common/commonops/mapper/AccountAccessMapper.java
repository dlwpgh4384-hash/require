package com.company.common.commonops.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountAccessMapper {
    final class Account {
        public String userId, name, email, hash, status, temporary;
        public Instant modifiedAt, passwordChangedAt, lastLoginAt;
        public int failures;
    }
    final class Permission {
        public String menuId, screenId, readYn, createYn, updateYn, deleteYn;
        public String uploadYn, downloadYn, cnfmYn, pcOnlyYn;
    }
    Account account(@Param("id") String id);
    Account lockAccount(@Param("id") String id);
    Integer standard(@Param("code") String code);
    List<String> roles(@Param("id") String id);
    List<Permission> permissions(@Param("id") String id);
    List<String> scopeCodes(@Param("id") String id);
    List<String> scopeParts(@Param("id") String id);
    List<String> scopeProjects(@Param("id") String id);
    boolean sensitive(@Param("id") String id);
    String uploadForm(@Param("uploadId") long uploadId, @Param("userId") String userId);
    int activeRoleCount(@Param("roles") List<String> roles);
    void upsertSmoke(@Param("id") String id, @Param("hash") String hash);
    void clearSmokeRoles(@Param("id") String id);
    void assignSmokeRoles(@Param("id") String id, @Param("roles") List<String> roles);
    void failed(@Param("id") String id, @Param("threshold") int threshold);
    void complete(@Param("id") String id, @Param("now") Instant now);
    int password(@Param("id") String id, @Param("hash") String hash,
        @Param("temporary") boolean temporary, @Param("now") Instant now);
    void access(@Param("id") String id, @Param("action") String action,
        @Param("target") String target, @Param("ip") String ip, @Param("now") Instant now);
}
