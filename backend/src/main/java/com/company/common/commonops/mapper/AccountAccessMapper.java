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
    /** roles와 같은 순서의 역할 이름(TB_SY_ROLE.ROLE_NM). 내 정보 화면에 보인다. */
    List<String> roleNames(@Param("id") String id);
    List<Permission> permissions(@Param("id") String id);
    List<String> scopeCodes(@Param("id") String id);
    List<String> scopeParts(@Param("id") String id);
    List<String> scopeProjects(@Param("id") String id);
    boolean sensitive(@Param("id") String id);
    /** 주소의 ID(프로젝트·청구·입금·배정·이슈)가 속한 프로젝트. 없으면 null. */
    String projectOf(@Param("kind") String kind, @Param("id") String id);
    String opportunityPart(@Param("id") String id);
    /** 개발자의 소속 영업 파트. 파트가 없으면 '', 개발자가 없으면 null. */
    String developerPart(@Param("id") String id);
    /** 프로젝트의 현재 영업 파트(이번 달 기준, 시작 전이면 첫 파트)가 이 파트들 중 하나인지. */
    boolean projectInParts(@Param("projectId") String projectId, @Param("parts") java.util.Collection<String> parts);
    String uploadForm(@Param("uploadId") long uploadId, @Param("userId") String userId);
    int activeRoleCount(@Param("roles") List<String> roles);
    /** 시험 계정을 새로 넣기만 한다. 같은 아이디가 이미 있으면 아무것도 바꾸지 않고 0을 돌려준다. */
    int insertSmoke(@Param("id") String id, @Param("hash") String hash);
    void assignSmokeRoles(@Param("id") String id, @Param("roles") List<String> roles);
    void failed(@Param("id") String id, @Param("threshold") int threshold);
    void complete(@Param("id") String id, @Param("now") Instant now);
    int password(@Param("id") String id, @Param("hash") String hash,
        @Param("temporary") boolean temporary, @Param("now") Instant now);
    void access(@Param("id") String id, @Param("action") String action,
        @Param("target") String target, @Param("ip") String ip, @Param("now") Instant now);
}
