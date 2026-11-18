package com.company.common.withdrawals.mapper;

import com.company.common.withdrawals.mapper.WithdrawalRows.*;
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
    List<Reservation> reservations(@Param("developerId") String developerId);
    int release(@Param("id") String id, @Param("actor") String actor);
    int insertReservation(ReservationWrite write);
    List<SupplyRow> supply(MonthFilter filter);
    List<ExtensionRow> extensions(MonthFilter filter);
    List<OpportunityDemandRow> opportunityDemand(MonthFilter filter);
    List<ProjectDemandRow> projectDemand(MonthFilter filter);
}
