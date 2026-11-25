package com.company.common.withdrawals.mapper;

import com.company.common.withdrawals.mapper.WithdrawalRows.*;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WithdrawalMapper {
    List<Source> list(ListFilter filter);
    List<Source> shared(SharedFilter filter);
    Source source(@Param("id") String id);
    Source lockSource(@Param("id") String id);
    String lockDeveloper(@Param("id") String id);
    List<Need> needs();
    List<String> lockNeeds();
    int touchOpportunity(@Param("id") String id, @Param("actor") String actor);
    List<Reservation> reservations(@Param("developerId") String developerId);
    /** 여러 사람의 현재 가배정(ACT·CNVT)을 한 번에 읽는다. 빈 목록으로는 부르지 않는다. */
    List<Reservation> reservationsFor(@Param("developerIds") Collection<String> developerIds);
    /** 여러 사람의 확정 투입(ACT, 취소 제외)을 시작일 순으로 한 번에 읽는다. 빈 목록으로는 부르지 않는다. */
    List<FollowingRow> followingAssignments(@Param("developerIds") Collection<String> developerIds);
    /** 여러 배정의 조기 철수 이력을 한 번에 읽는다. 빈 목록으로는 부르지 않는다. */
    List<EarlyRow> earlyHistory(@Param("assignmentIds") Collection<String> assignmentIds);
    List<String> skills(@Param("developerId") String developerId);
    int release(@Param("id") String id, @Param("actor") String actor);
    int insertReservation(ReservationWrite write);
    List<SupplyRow> supply(MonthFilter filter);
    List<ExtensionRow> extensions(MonthFilter filter);
    List<OpportunityDemandRow> opportunityDemand(MonthFilter filter);
    List<ProjectDemandRow> projectDemand(MonthFilter filter);
    /** 엑셀 다운로드 접근 기록(DNLD, SFR-011-03). */
    int insertAccess(@org.apache.ibatis.annotations.Param("userId") String userId, @org.apache.ibatis.annotations.Param("target") String target);
}
