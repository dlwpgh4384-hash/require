package com.company.common.salesopportunities.mapper;

import com.company.common.salesopportunities.dto.SalesOpportunityContracts.ApprovalHistory;
import com.company.common.salesopportunities.mapper.SalesOpportunityRows.*;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SalesOpportunityMapper {
    Header select(@Param("id") String id);
    Header lock(@Param("id") String id);
    List<String> lockOpportunities();
    List<String> lockDevelopers();
    List<NeedRow> needs(@Param("id") String id);
    List<ApprovalHistory> history(@Param("id") String id);
    List<Reservation> reservations(@Param("id") String id);
    List<Reservation> developerReservations(@Param("developerId") String developerId);
    boolean customer(@Param("id") String id);
    boolean part(@Param("id") String id);
    boolean developer(@Param("id") String id);
    BigDecimal defaultPrice(@Param("jobCode") String jobCode);
    BigDecimal monthlyCost(@Param("jobCode") String jobCode, @Param("gradeCode") String gradeCode);
    BigDecimal confirmedMm(@Param("developerId") String developerId, @Param("month") String month);
    int insert(Write write);
    int update(Write write);
    int insertNeed(NeedWrite write);
    int updateNeed(NeedWrite write);
    int removeNeed(@Param("id") String id, @Param("needSn") int needSn);
    int historicalNeed(@Param("id") String id, @Param("needSn") int needSn);
    int deleteReleasedNeedReservations(@Param("id") String id, @Param("needSn") int needSn);
    int releaseNeed(@Param("id") String id, @Param("needSn") int needSn, @Param("actor") String actor);
    int release(@Param("tentativeId") long tentativeId, @Param("actor") String actor);
    int releaseAll(@Param("id") String id, @Param("actor") String actor);
    int touch(@Param("id") String id, @Param("actor") String actor);
    int tentative(TentativeWrite write);
    int stage(StageWrite write);
    int approval(ApprovalWrite write);
    int appendHistory(ApprovalWrite write);
    int notifyOwner(Notification notification);
    int result(ResultWrite write);
    int linkProject(ProjectLink link);
    int manpower(ProjectLink link);
    int convert(@Param("id") String id, @Param("actor") String actor);
}
