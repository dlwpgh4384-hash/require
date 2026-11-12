package com.company.common.commonops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserOptionsMapper {
    List<UserOptionRows.Role> selectRoles();
    List<UserOptionRows.Project> selectProjects();
    List<UserOptionRows.UserState> selectUserStates();
}
