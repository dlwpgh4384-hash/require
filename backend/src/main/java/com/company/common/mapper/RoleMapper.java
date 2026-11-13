package com.company.common.mapper;

import com.company.common.dto.UserContracts.History;
import java.math.BigInteger;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.company.common.dto.RoleContracts.*;

@Mapper
public interface RoleMapper {
    List<RoleRows.Role> selectRoles();
    RoleRows.Role selectRole(@Param("id") String id);
    List<RoleRows.Menu> selectMenus(@Param("id") String id);
    List<RoleRows.ScopeCode> selectDataScopes();
    boolean nameExists(@Param("name") String name);
    BigInteger selectIssuedNumber();
    int selectNextSortOrder();
    void insertRole(@Param("id") String id, @Param("input") Copy input,
        @Param("source") RoleRows.Role source, @Param("sort") int sort);
    void insertBlankMenus(@Param("id") String id);
    List<RoleRows.Change> selectChanges(@Param("id") String id);
    void copyMenus(@Param("id") String id, @Param("sourceId") String sourceId);
    void updateRole(@Param("id") String id, @Param("input") Update input);
    void updateMenu(@Param("id") String id, @Param("input") MenuInput input);
    long countAssignments(@Param("id") String id);
    void stopRole(@Param("id") String id);
    List<RoleRows.Scope> selectRemovableScopes(@Param("id") String id, @Param("type") String type);
    List<String> selectPreservedPmUsers(@Param("id") String id);
    int deleteScope(RoleRows.Scope scope);
    void insertHistory(History history);
    void insertRoleAccess(@Param("id") String id);
}
