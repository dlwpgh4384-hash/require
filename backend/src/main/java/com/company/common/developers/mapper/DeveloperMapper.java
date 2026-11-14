package com.company.common.developers.mapper;

import com.company.common.developers.dto.DeveloperContracts.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeveloperMapper {
    void lockSources();
    List<DeveloperRows.Developer> selectDevelopers(@Param("filter") Filter filter, @Param("month") String month);
    DeveloperRows.Developer selectDeveloper(@Param("id") String id, @Param("month") String month);
    DeveloperRows.Developer lockDeveloper(@Param("id") String id, @Param("month") String month);
    List<String> selectManagementNumbers();
    List<DeveloperRows.Code> selectCodes(String groupId);
    boolean selectableOrganization(String id);
    boolean selectablePart(String id);
    int insertDeveloper(Write write);
    int updateDeveloper(Write write);
    void deleteSkills(String id);
    void insertSkill(@Param("id") String id, @Param("name") String name, @Param("order") int order);
    List<String> selectSkills(String id);
    void savePrice(PriceWrite write);
    void closePreviousPrice(PriceWrite write);
    List<DeveloperRows.Area> selectAreas(String parentAreaCode);
    DeveloperRows.Address selectAddress(String areaCode);
    List<DeveloperRows.Assignment> selectAssignments(String id);
    boolean hasAssignmentHistory();
    BigDecimal selectAssignmentMm(@Param("id") String id, @Param("date") LocalDate date);
    List<DeveloperRows.State> selectStatuses(String id);
    int insertStatus(DeveloperRows.State row);
    int updateStatus(DeveloperRows.State row);
    int deleteStatus(@Param("id") String id, @Param("sequence") int sequence);
    boolean closedMonth(String month);
    void updateDerived(@Param("id") String id, @Param("retirement") LocalDate retirement,
        @Param("code") String code, @Param("resume") boolean resume, @Param("today") LocalDate today);
    int updateUse(@Param("id") String id, @Param("useYn") String useYn, @Param("actor") String actor);
    void insertHistory(Audit audit);
    List<DeveloperRows.Change> selectChanges(String id);
    void insertAccess(@Param("actor") String actor, @Param("count") int count);
}
