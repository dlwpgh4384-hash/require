package com.company.common.personalcostprofits.mapper;

import com.company.common.personalcostprofits.dto.PersonalCostProfitContracts.Filter;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import com.company.common.roles.dto.RoleContracts.Role;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PersonalCostProfitMapper {
    List<Person> selectPeople(Filter filter);
    List<Pay> selectPayroll(Filter filter);
    List<Cost> selectCosts(Filter filter);
    List<Assignment> selectAssignments(Filter filter);
    List<Leave> selectLeaves(Filter filter);
    List<Code> selectCodes();
    List<Role> selectActorRoles(@Param("userId") String userId);
    int insertAccess(@Param("userId") String userId, @Param("target") String target);
    int insertSensitiveView(@Param("userId") String userId, @Param("target") String target);
}
