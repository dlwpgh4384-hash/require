package com.company.common.users;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.company.common.users.UserContracts.*;

@Mapper
public interface UserMapper {
    void lockManagement();
    UserRows.Account selectAccount(@Param("id") String id);
    List<UserRows.Account> selectUsers(Filter filter);
    long countUsers(Filter filter);
    List<String> selectRoles(@Param("id") String id);
    List<String> selectRoleNames(@Param("id") String id);
    List<String> selectParts(@Param("id") String id);
    List<String> selectLeaders(@Param("id") String id);
    List<String> selectHeadquarters();
    boolean hasHeadquarters(@Param("id") String id);
    List<UserRows.Project> selectProjects(@Param("id") String id);
    List<UserRows.Owner> selectPmOwners(@Param("id") String id, @Param("projects") List<String> projects);
    long countActiveAdmins();
    List<UserRows.Log> selectLogs(@Param("id") String id, @Param("filter") Filter filter);
    long countLogs(@Param("id") String id);
    int insertUser(@Param("input") Create input, @Param("hash") String hash);
    int touch(@Param("id") String id, @Param("token") LocalDateTime token);
    int changeState(@Param("id") String id, @Param("token") LocalDateTime token,
        @Param("state") String state, @Param("unlock") boolean unlock);
    int resetPassword(@Param("id") String id, @Param("token") LocalDateTime token, @Param("hash") String hash);
    void deleteRoles(@Param("id") String id);
    void insertRole(@Param("id") String id, @Param("role") String role);
    void deleteProject(@Param("id") String id, @Param("project") String project);
    void deleteHeadquarters(@Param("id") String id);
    void insertScope(@Param("id") String id, @Param("type") String type, @Param("value") String value);
    void insertAccess(@Param("id") String id, @Param("action") String action, @Param("target") String target);
    void insertHistory(History history);
}
