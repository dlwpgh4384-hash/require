package com.company.common.assignments.mapper;

import com.company.common.assignments.mapper.AssignmentRows.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssignmentMapper {
    List<Row> rows();
    Row row(String id);
    Row lockRow(String id);
    List<String> ids();
    int insert(Write write);
    int update(Write write);
    List<HistoryRow> history(String id);
    int nextHistory(String id);
    int insertHistory(HistoryWrite write);
    HistoryRow lockHistory(@Param("id") String id, @Param("sequence") int sequence);
    int correct(Correction correction);
    List<CandidateRow> candidates();
    CandidateRow lockCandidate(String id);
    int removeCandidate(@Param("id") String id, @Param("actor") String actor);
    Person person(@Param("id") String id, @Param("month") String month,
        @Param("start") LocalDate start, @Param("end") LocalDate end);
    List<String> closedMonths();
    BigDecimal yearMm(@Param("id") String id, @Param("year") int year);
    BigDecimal overload(@Param("developerId") String developerId, @Param("year") int year);
    boolean distribution(@Param("projectId") String projectId, @Param("developerId") String developerId);
    int deleteOpenMonths(OpenRange range);
    int deleteOpenCosts(OpenRange range);
    int saveMonth(MonthWrite write);
    int saveCost(CostWrite write);
    BigDecimal labor(@Param("id") String id, @Param("month") String month);
    BigDecimal latestPrice(String id);
    int insertAccess(@Param("userId") String userId,@Param("target") String target);
}
