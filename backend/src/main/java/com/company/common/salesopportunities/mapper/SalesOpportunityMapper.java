package com.company.common.salesopportunities.mapper;

import com.company.common.salesopportunities.dto.SalesOpportunityContracts.ApprovalHistory;
import com.company.common.salesopportunities.mapper.SalesOpportunityRows.*;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SalesOpportunityMapper {
    Header select(@Param("id") String id);
    Header lock(@Param("id") String id);
    /** 고칠 기회 줄만 ID 순서로 잠근다. 빈 목록으로는 부르지 않는다. */
    List<String> lockOpportunities(@Param("ids") Collection<String> ids);
    /** 가배정에 들어가는 사람 줄만 ID 순서로 잠근다. 빈 목록으로는 부르지 않는다. */
    List<String> lockDevelopers(@Param("ids") Collection<String> ids);
    List<NeedRow> needs(@Param("id") String id);
    List<ApprovalHistory> history(@Param("id") String id);
    List<Reservation> reservations(@Param("id") String id);
    List<Reservation> developerReservations(@Param("developerId") String developerId);
    /** 여러 사람의 ACT 가배정을 한 번에 읽는다(잠글 기회를 정할 때). 빈 목록으로는 부르지 않는다. */
    List<Reservation> developersReservations(@Param("ids") Collection<String> ids);
    /** 수주로 투입 배정 후보(CNVT)가 된 가배정. */
    List<Reservation> developerConversions(@Param("developerId") String developerId);
    /** 수주 때 직무별로 만들 인원 계획 MM(인원 × 월 MM × 개월 합). */
    List<BigDecimal> manpowerPlans(@Param("id") String id);
    boolean customer(@Param("id") String id);
    boolean part(@Param("id") String id);
    /** 근무지 지역: 행정구역 표에 있고 사용 중(USE_YN='Y')인 코드인지(2026-11-25). */
    boolean area(@Param("code") String code);
    boolean developer(@Param("id") String id);
    BigDecimal defaultPrice(@Param("jobCode") String jobCode);
    BigDecimal monthlyCost(@Param("jobCode") String jobCode, @Param("gradeCode") String gradeCode);
    BigDecimal confirmedMm(@Param("developerId") String developerId, @Param("month") String month);
    /** 이미 있는 O-nnnn 영업기회ID 중 가장 큰 번호 + 1(없으면 1). 형식이 다른 ID는 건너뛴다. */
    int nextOpportunityNumber();
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
