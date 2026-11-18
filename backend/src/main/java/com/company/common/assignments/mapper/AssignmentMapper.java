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
    List<Row> listRows(ListFilter filter);
    List<Row> projectRows(@Param("projectId") String projectId);
    Row row(String id);
    Row lockRow(String id);
    List<String> ids();
    int insert(Write write);
    int update(Write write);
    List<HistoryRow> history(String id);
    List<MonthValue> monthValues(String id);
    int nextHistory(String id);
    int insertHistory(HistoryWrite write);
    HistoryRow lockHistory(@Param("id") String id, @Param("sequence") int sequence);
    int correct(Correction correction);
    List<CandidateRow> candidates();
    CandidateRow lockCandidate(String id);
    boolean indirect(@Param("id") String id);
    UploadDeveloper uploadDeveloper(@Param("managementNumber") String managementNumber);
    int confirmCandidate(@Param("id") String id, @Param("actor") String actor);
    int removeCandidate(@Param("id") String id, @Param("actor") String actor);
    Person person(@Param("id") String id, @Param("month") String month,
        @Param("start") LocalDate start, @Param("end") LocalDate end);
    List<String> closedMonths();
    List<Amount> yearMms(@Param("ids") List<String> ids, @Param("year") int year);
    List<Amount> overloads(@Param("ids") List<String> ids, @Param("year") int year);
    List<Count> historyCounts(@Param("ids") List<String> ids);
    boolean distribution(@Param("projectId") String projectId, @Param("developerId") String developerId);
    int deleteOpenMonths(OpenRange range);
    int saveMonth(MonthWrite write);
    BigDecimal latestPrice(String id);
    int insertAccess(@Param("userId") String userId,@Param("target") String target);
}
