package com.company.common.projects.mapper;

import com.company.common.projects.dto.ProjectContracts.*;
import com.company.common.projects.mapper.ProjectRows.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectMapper {
    List<Project> selectProjects(@Param("today") LocalDate today, @Param("month") String month);
    Project selectProject(@Param("id") String id, @Param("today") LocalDate today, @Param("month") String month);
    String lockProject(String id);
    void lockSources();
    List<String> projectIds();
    record Customer(String id, String name, String businessNumber) {}
    List<Customer> uploadCustomers();
    List<Job> uploadParts();
    record Developer(String id, String name) {}
    Developer uploadDeveloper(String managementNumber);
    record DistributionCell(String projectId, String stage, int row, String month, String type,
        String developerId, String itemName, BigDecimal amount, String actor, long uploadId) {}
    void deleteDistribution(@Param("id") String id, @Param("stage") String stage);
    void insertDistribution(DistributionCell value);
    int insertProject(Write write);
    int updateProject(Write write);
    void insertHistory(Audit audit);
    List<History> histories(String id);
    List<SalesPart> salesParts(@Param("id") String id, @Param("month") String month);
    void insertPart(PartWrite write);
    void deleteOpenParts(@Param("id") String id, @Param("closed") String closed);
    String lastClosedMonth();
    BigDecimal allowance(String code);
    boolean eligibleCustomer(String id);
    boolean eligiblePart(String id);
    /** 근무지 지역: 행정구역 표에 있고 사용 중(USE_YN='Y')인 코드인지. */
    boolean usableArea(String code);
    String customerName(String id);
    String codeName(@Param("group") String group, @Param("code") String code);
    boolean hasBills(String id);
    Outside outside(@Param("id") String id, @Param("start") LocalDate start,
        @Param("end") LocalDate end, @Param("today") LocalDate today);
    boolean removesClosedMonth(@Param("id") String id, @Param("start") LocalDate start,
        @Param("end") LocalDate end);
    List<Assignment> assignments(String id);
    List<ActiveAssignment> lockAssignments(String id);
    List<ActiveAssignment> impactAssignments(String id);
    void assignmentHistory(AssignmentWrite write);
    void changeAssignment(AssignmentWrite write);
    void lose(LossWrite write);
    void start(@Param("id") String id, @Param("actor") String actor);
    List<String> roles(String userId);
    List<String> projectScopes(String userId);
    List<Manpower> manpower(String id);
    void savePlan(PlanWrite write);
    List<Expense> expenses(String id);
    int nextExpenseSequence(@Param("id") String id, @Param("month") String month);
    void insertExpense(ExpenseWrite write);
    int deleteExpense(ExpenseKey key);
    List<Step> steps(String id);
    List<Issue> issues(String id);
    List<Memo> memos(String id);
    /** SER-006 접근 기록(프로젝트 화면 UI-01): 목록 다운로드(DNLD). */
    int insertAccess(@org.apache.ibatis.annotations.Param("userId") String userId, @org.apache.ibatis.annotations.Param("action") String action, @org.apache.ibatis.annotations.Param("target") String target);
    BigDecimal actualProgress(String id);
    HealthSource healthSource(@Param("id") String id, @Param("today") LocalDate today);
    List<Cost> costs(@Param("id") String id, @Param("month") String month);
    List<Cell> distributionCells(String id);
    List<DistributionUpload> distributionUploads(String id);
    List<Job> distributionJobs(@Param("id") String id, @Param("developerId") String developerId);
    List<MismatchCell> mismatches(String id);
}
