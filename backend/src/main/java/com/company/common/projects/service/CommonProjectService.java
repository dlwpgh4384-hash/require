package com.company.common.projects.service;

import com.company.common.common.ProjectAmountRules;
import com.company.common.projects.dto.ProjectContracts.*;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.projects.mapper.ProjectRows.*;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.support.service.ExcelExportService;
import com.company.common.users.service.UserScopePolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommonProjectService {
    private final ProjectMapper mapper;
    private final BusinessCalendar calendar;
    private final CurrentBusinessActor actor;
    private final ProjectAmountRules amounts;
    private final ExcelExportService excel;
    private final ProjectProgressRules progressRules;

    public CommonProjectService(ProjectMapper mapper, BusinessCalendar calendar, CurrentBusinessActor actor,
            ProjectAmountRules amounts, ExcelExportService excel, ProjectProgressRules progressRules) {
        this.mapper = mapper;
        this.calendar = calendar;
        this.actor = actor;
        this.amounts = amounts;
        this.excel = excel;
        this.progressRules = progressRules;
    }
    private String currentMonth() { return db(calendar.currentMonth()); }
    private static String db(YearMonth m) { return m.toString().replace("-", ""); }
    public ProjectList getCommonProjects() {
        var rows = mapper.selectProjects(calendar.today(), currentMonth());
        rows.forEach(this::decorate);
        return new ProjectList(rows, rows.stream().filter(p -> !internal(p) && p.salesPartId == null).count());
    }
    public Project getCommonProject(String id) {
        Project p = require(id);
        decorate(p);
        p.assignments = mapper.assignments(id);
        p.issues = mapper.issues(id);
        return p;
    }
    private Project require(String id) {
        Project p = mapper.selectProject(ProjectRules.id(id), calendar.today(), currentMonth());
        if (p == null) throw CodeFailure.invalid("projectId", "프로젝트를 찾을 수 없습니다.");
        return p;
    }
    private Project locked(String id) {
        ProjectRules.id(id);
        // Same lock order for every command, including upload apply.
        mapper.lockSources();
        if (mapper.lockProject(id) == null) throw CodeFailure.invalid("projectId", "프로젝트를 찾을 수 없습니다.");
        return require(id);
    }
    private boolean currentYear(Project p) {
        int year = calendar.today().getYear();
        return !p.startDate.isAfter(LocalDate.of(year, 12, 31)) && !p.endDate.isBefore(LocalDate.of(year, 1, 1));
    }
    private boolean planPermission(String id) {
        Set<String> roles = new HashSet<>(mapper.roles(actor.userId()));
        // Current HTTP context is explicitly admin, never request-supplied roles.
        roles.addAll(actor.roles());
        var grants = List.of(new UserScopePolicy.Grant("PROJ", Set.of(), new HashSet<>(mapper.projectScopes(actor.userId()))));
        return ProjectBusinessPolicy.canSaveManpower(roles, grants, id);
    }
    private void decorate(Project p) {
        p.progress = progressRules.progress(p.contractTypeCode, mapper.steps(p.projectId),
            mapper.actualProgress(p.projectId), calendar.today());
        p.trafficLight = progressRules.trafficLight(p, mapper.healthSource(p.projectId, calendar.today()),
            projectCosts(p), calendar.today());
        boolean editable = currentYear(p) && !"LOSE".equals(p.statusCode);
        p.canEdit = editable;
        p.canChangeBudget = editable && !internal(p);
        p.canChangeSalesPart = editable && !internal(p);
        p.canSaveManpowerPlan = editable && planPermission(p.projectId);
        p.canLose = editable && "PRE".equals(p.statusCode);
        p.canStart = p.canLose;
        p.preContractPeopleCount = "PRE".equals(p.statusCode) ? p.assignedPeopleCount : 0;
        if ("TK".equals(p.contractTypeCode)) {
            var cells = mapper.distributionCells(p.projectId);
            BigDecimal sum = cells.stream().map(c -> c.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            p.distributionTotalWarning = sum.compareTo(p.contractAmount) != 0;
            p.distributionMismatchCount = mapper.mismatches(p.projectId).size();
        }
    }
    public List<Cost> getProjectCosts(String id) {
        return projectCosts(require(id));
    }
    private List<Cost> projectCosts(Project p) {
        return progressRules.classifyCosts(mapper.costs(p.projectId, currentMonth()), p.statusCode);
    }
    private static boolean internal(Project p) { return "IN".equals(p.contractTypeCode); }
    private void writable(Project p) {
        if (!currentYear(p)) throw CodeFailure.invalid("projectId", "지난 해 프로젝트는 읽기 전용입니다. 올해에 이어지는 프로젝트는 올해 화면에서 고쳐 주세요.");
        if ("LOSE".equals(p.statusCode)) throw CodeFailure.invalid("projectId", "실주 프로젝트는 읽기 전용입니다.");
    }
    private String code(String group, String value, String field) {
        if (value == null || mapper.codeName(group, value) == null) throw ProjectRules.invalid(field);
        return value;
    }
    private void references(Project p, String part) {
        code("CTRT_TYPE", p.contractTypeCode, "contractTypeCode");
        if (!internal(p)) {
            if (p.customerId == null || !mapper.eligibleCustomer(p.customerId)) throw ProjectRules.invalid("customerId");
            if (part != null && !mapper.eligiblePart(part)) throw ProjectRules.invalid("salesPartId");
            if (p.contractAmount == null) throw ProjectRules.invalid("contractAmount");
        }
    }
    private Project values(Input input, Project old) {
        var p = new Project();
        p.projectId = old == null ? null : old.projectId;
        p.contractTypeCode = old == null ? input.contractTypeCode() : old.contractTypeCode;
        p.statusCode = old == null ? input.statusCode() : old.statusCode;
        p.projectName = input.projectName();
        p.startDate = input.startDate(); p.endDate = input.endDate();
        p.customerId = internal(p) ? null : input.customerId();
        p.workplace = internal(p) ? null : input.workplace();
        p.estimateAmount = input.estimateAmount();
        p.contractAmount = input.contractAmount();
        if (internal(p) && p.contractAmount == null) p.contractAmount = BigDecimal.ZERO;
        if (!internal(p) && p.contractAmount == null) throw ProjectRules.invalid("contractAmount");
        p.feature = input.feature();
        p.teamExpenseAmount = input.teamExpenseAmount() == null ? mapper.allowance("TEAM_FEE") : input.teamExpenseAmount();
        p.projectExpenseAmount = input.projectExpenseAmount() == null ? mapper.allowance("PEXP_FEE") : input.projectExpenseAmount();
        if (p.teamExpenseAmount == null) throw ProjectRules.invalid("teamExpenseAmount");
        if (p.projectExpenseAmount == null) throw ProjectRules.invalid("projectExpenseAmount");
        if (old == null || !mapper.hasBills(old.projectId)) p.originalContractAmount = p.contractAmount;
        else p.originalContractAmount = old.originalContractAmount;
        p.budgetManualYn = old == null ? "N" : old.budgetManualYn;
        p.budgetAmount = old == null ? null : old.budgetAmount;
        if (internal(p)) { p.budgetAmount = null; p.budgetManualYn = "N"; }
        else if (input.budgetAmount() != null) { p.budgetAmount = input.budgetAmount(); p.budgetManualYn = "Y"; }
        else if (old == null || !"Y".equals(old.budgetManualYn)
                && !equalAmount(old.contractAmount, p.contractAmount)) {
            p.budgetAmount = amounts.automaticBudget(p.contractAmount, calendar.currentMonth());
        }
        if (!"LOSE".equals(p.statusCode) && p.endDate.isBefore(calendar.today())) p.statusCode = "END";
        else if (old != null && "END".equals(old.statusCode) && p.endDate.isAfter(calendar.today())) p.statusCode = "ING";
        return p;
    }
    public void validateRegistration(Input input) {
        registration(input);
    }
    private Project registration(Input input) {
        if (!Set.of("ING", "PRE").contains(input.statusCode())) throw ProjectRules.invalid("statusCode");
        Project p = values(input, null);
        if (!internal(p) && input.salesPartId() == null) throw ProjectRules.invalid("salesPartId");
        references(p, input.salesPartId());
        return p;
    }
    @Transactional
    public Project postCommonProject(Input input) {
        mapper.lockSources();
        return create(input, null);
    }
    /** B9 apply uses the same invariant inside the upload engine's transaction. */
    @Transactional
    public Project create(Input input, String explicitId) {
        mapper.lockSources();
        Project p = registration(input);
        var ids = new HashSet<>(mapper.projectIds());
        if (explicitId == null) {
            int number = 1;
            while (ids.contains(String.format(java.util.Locale.ROOT, "P-%04d", number))) number++;
            p.projectId = String.format(java.util.Locale.ROOT, "P-%04d", number);
        } else {
            ProjectRules.id(explicitId);
            if (ids.contains(explicitId)) throw CodeFailure.invalid("projectId", "이미 등록된 프로젝트ID입니다.");
            p.projectId = explicitId;
        }
        mapper.insertProject(new Write(p, actor.userId()));
        mapper.insertPart(new PartWrite(p.projectId, db(YearMonth.from(p.startDate)),
            internal(p) ? null : input.salesPartId(), null, actor.userId()));
        history(p.projectId, "CTRT", "계약", null, p.projectName);
        return getCommonProject(p.projectId);
    }
    @Transactional
    public Project putCommonProject(String id, Input input) {
        var old = locked(id); writable(old);
        var p = values(input, old); references(p, old.salesPartId);
        if (!old.startDate.equals(p.startDate) || !old.endDate.equals(p.endDate)) {
            var outside = mapper.outside(id, p.startDate, p.endDate, calendar.today());
            if (outside.assignments + outside.distributionMonths + outside.expenses > 0) {
                throw CodeFailure.invalid("endDate", "새 계약기간(" + p.startDate + "~" + p.endDate + ") 밖에 투입 "
                    + outside.assignments + "건, 배분표 금액이 있는 달 " + outside.distributionMonths
                    + "개, 추가 직접 경비 " + outside.expenses + "건이 있습니다. 먼저 고친 뒤 바꿔 주세요.");
            }
            if (outside.started > 0 || mapper.removesClosedMonth(id, p.startDate, p.endDate))
                throw CodeFailure.invalid("startDate", "마감월과 이미 시작한 배정의 투입일은 변경할 수 없습니다.");
        }
        change(id, "ETC", "프로젝트명", old.projectName, p.projectName);
        change(id, "ETC", "고객사", old.customerName, internal(p) ? "(자체)" : mapper.customerName(p.customerId));
        change(id, "PERIOD", "계약기간", old.startDate + "~" + old.endDate, p.startDate + "~" + p.endDate);
        change(id, "LOC", "근무지", old.workplace, p.workplace);
        change(id, "ETC", "견적 금액", old.estimateAmount, p.estimateAmount);
        change(id, "AMT", "계약금액", old.contractAmount, p.contractAmount);
        change(id, "BUDGET", "예산(목표원가)", old.budgetAmount, p.budgetAmount);
        change(id, "TEAM", "팀비", old.teamExpenseAmount, p.teamExpenseAmount);
        change(id, "PEXP", "프로젝트경비", old.projectExpenseAmount, p.projectExpenseAmount);
        change(id, "ETC", "특징", old.feature, p.feature);
        mapper.updateProject(new Write(p, actor.userId()));
        return getCommonProject(id);
    }
    private static boolean equalAmount(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }
    private void change(String id, String code, String name, Object before, Object after) {
        if (before instanceof BigDecimal a && after instanceof BigDecimal b && a.compareTo(b) == 0) return;
        if (!Objects.equals(before, after)) history(id, code, name, before, after);
    }
    private void history(String id, String code, String name, Object before, Object after) {
        mapper.insertHistory(new Audit(id, code, name, before == null ? null : before.toString(),
            after == null ? null : after.toString(), actor.userId()));
    }
    public Items<History> getCommonProjectChangeHistory(String id) { require(id); return new Items<>(mapper.histories(id)); }
    public Items<SalesPart> getCommonProjectSalesParts(String id) { require(id); return new Items<>(mapper.salesParts(id, currentMonth())); }
    @Transactional
    public Project putCommonProjectBudget(String id, BigDecimal budget) {
        var p = locked(id); writable(p);
        if (internal(p)) throw ProjectRules.invalid("budgetAmount");
        if (budget == null || budget.signum() < 0 || budget.stripTrailingZeros().scale() > 0) throw ProjectRules.invalid("budgetAmount");
        history(id, "BUDGET", "예산(목표원가)", p.budgetAmount, budget);
        p.budgetAmount = budget; p.budgetManualYn = "Y";
        mapper.updateProject(new Write(p, actor.userId()));
        return getCommonProject(id);
    }
    private void open(String month, String field) {
        String closed = mapper.lastClosedMonth();
        if (closed != null && month.compareTo(closed) <= 0) throw CodeFailure.invalid(field, "마감월 다음달부터 입력할 수 있습니다.");
    }
    @Transactional
    public Items<SalesPart> putCommonProjectSalesPart(String id, PartInput input) {
        var p = locked(id); writable(p);
        if (internal(p) || !mapper.eligiblePart(input.salesPartId())) throw ProjectRules.invalid("salesPartId");
        var month = ProjectRules.month(input.effectiveMonth(), "effectiveMonth");
        if (month.isBefore(YearMonth.from(p.startDate)) || month.isAfter(YearMonth.from(p.endDate))) throw ProjectRules.invalid("effectiveMonth");
        open(db(month), "effectiveMonth");
        var rows = mapper.salesParts(id, currentMonth());
        if (rows.isEmpty()) throw CodeFailure.invalid("salesPartId", "최초 파트 이력이 없습니다.");
        String closed = mapper.lastClosedMonth();
        String boundary = closed == null ? "000000" : closed;
        SalesPart original = rows.get(0);
        for (var row : rows) if (db(YearMonth.parse(row.effectiveMonth)).compareTo(boundary) <= 0) original = row;
        boolean cancel = Objects.equals(original.salesPartId, input.salesPartId());
        if (!cancel && !month.isAfter(YearMonth.parse(rows.get(0).effectiveMonth))) {
            throw CodeFailure.invalid("effectiveMonth", "최초 파트 이력은 보존됩니다. 최초 적용월 다음달부터 변경해 주세요.");
        }
        mapper.deleteOpenParts(id, boundary);
        if (!cancel) mapper.insertPart(new PartWrite(id, db(month), input.salesPartId(), input.reason(), actor.userId()));
        String targetName = cancel ? original.salesPartName : mapper.salesParts(id, currentMonth()).stream()
            .filter(r -> Objects.equals(r.salesPartId, input.salesPartId())).map(r -> r.salesPartName).findFirst().orElse(input.salesPartId());
        history(id, "PART", "영업파트", original.salesPartName,
            targetName + " (" + input.effectiveMonth() + (cancel ? " 변경 취소)" : " 적용)"));
        return getCommonProjectSalesParts(id);
    }
    public ManpowerList getCommonProjectManpowerPlan(String id) {
        var p = require(id);
        return new ManpowerList(mapper.manpower(id), currentYear(p) && !"LOSE".equals(p.statusCode) && planPermission(id));
    }
    @Transactional
    public ManpowerList putCommonProjectManpowerPlan(String id, ManpowerInput input) {
        var p = locked(id); writable(p);
        if (!planPermission(id)) throw new CodeFailure(403, "jobs", "담당 PM과 관리자만 인원 계획을 고칠 수 있습니다.");
        for (var job : input.jobs()) code("JOB", job.jobCode(), "jobs");
        for (var job : input.jobs()) mapper.savePlan(new PlanWrite(id, job, actor.userId()));
        return getCommonProjectManpowerPlan(id);
    }
    public Items<Expense> getCommonProjectDirectExpenses(String id) { require(id); return new Items<>(mapper.expenses(id)); }
    @Transactional
    public Expense postCommonProjectDirectExpense(String id, ExpenseInput input) {
        var p = locked(id); writable(p);
        if (internal(p)) throw ProjectRules.invalid("projectId");
        var month = ProjectRules.month(input.yearMonth(), "yearMonth");
        if (month.isBefore(YearMonth.from(p.startDate)) || month.isAfter(YearMonth.from(p.endDate))) throw ProjectRules.invalid("yearMonth");
        open(db(month), "yearMonth"); code("DRCT_EXPS_ITEM", input.itemCode(), "itemCode");
        int sequence = mapper.nextExpenseSequence(id, db(month));
        if (sequence > 99999) throw ProjectRules.invalid("expenseId");
        var key = new ExpenseKey(id, db(month), sequence);
        mapper.insertExpense(new ExpenseWrite(key, input, actor.userId()));
        return mapper.expenses(id).stream().filter(e -> e.expenseId.equals(key.month() + ":" + sequence))
            .findFirst().orElseThrow(() -> new IllegalStateException("저장한 경비를 읽을 수 없습니다."));
    }
    public static ExpenseKey expenseKey(String id, String expenseId) {
        if (expenseId == null || !expenseId.matches("[0-9]{4}(0[1-9]|1[0-2]):[1-9][0-9]{0,4}")) throw ProjectRules.invalid("expenseId");
        return new ExpenseKey(ProjectRules.id(id), expenseId.substring(0, 6), Integer.parseInt(expenseId.substring(7)));
    }
    @Transactional
    public DeletedExpense postCommonProjectDirectExpenseDelete(String id, String expenseId) {
        var key = expenseKey(id, expenseId);
        var p = locked(id); writable(p); open(key.month(), "expenseId");
        if (mapper.deleteExpense(key) != 1) throw ProjectRules.invalid("expenseId");
        return new DeletedExpense(expenseId, true);
    }
    public LoseImpact getCommonProjectLoseImpact(String id) {
        var p = require(id);
        if (!"PRE".equals(p.statusCode)) throw ProjectRules.invalid("projectId");
        return impact(mapper.impactAssignments(id));
    }
    private LoseImpact impact(List<ActiveAssignment> rows) {
        var active = new HashSet<String>(); var future = new HashSet<String>();
        LocalDate today = calendar.today();
        for (var row : rows) {
            if (row.startDate.isAfter(today)) future.add(row.developerId);
            else if (!row.endDate.isBefore(today)) active.add(row.developerId);
        }
        return new LoseImpact(active.size(), future.size());
    }
    @Transactional
    public Project postCommonProjectStart(String id) {
        var p = locked(id); writable(p);
        if (!"PRE".equals(p.statusCode)) throw ProjectRules.invalid("projectId");
        mapper.start(id);
        return getCommonProject(id);
    }
    @Transactional
    public Project postCommonProjectLose(String id, LoseInput input) {
        var p = locked(id); writable(p);
        if (!"PRE".equals(p.statusCode)) throw ProjectRules.invalid("projectId");
        code("LOSE_RSN", input.reasonCode(), "reasonCode");
        String memo = "프로젝트 실주: " + mapper.codeName("LOSE_RSN", input.reasonCode());
        if (memo.length() > 200) throw ProjectRules.invalid("reasonCode");
        LocalDate today = calendar.today();
        for (var a : mapper.lockAssignments(id)) {
            if (a.endDate.isBefore(today)) continue;
            String kind = a.startDate.isAfter(today) ? "CNCL" : "EARLY";
            var write = new AssignmentWrite(a.assignmentId, kind, today, memo, actor.userId());
            mapper.assignmentHistory(write);
            mapper.changeAssignment(write);
        }
        mapper.lose(new LossWrite(id, input, today, actor.userId()));
        return getCommonProject(id);
    }
    public Distribution getCommonProjectDistribution(String id) {
        var p = getCommonProject(id);
        if (!"TK".equals(p.contractTypeCode)) throw ProjectRules.invalid("projectId");
        var groups = new LinkedHashMap<Integer, List<Cell>>();
        for (var c : mapper.distributionCells(id)) groups.computeIfAbsent(c.rowNumber, k -> new ArrayList<>()).add(c);
        var rows = new ArrayList<DistributionRow>();
        for (var cells : groups.values()) {
            var first = cells.get(0);
            var months = new LinkedHashMap<String, BigDecimal>();
            for (YearMonth m = YearMonth.from(p.startDate); !m.isAfter(YearMonth.from(p.endDate)); m = m.plusMonths(1))
                months.put(m.toString(), BigDecimal.ZERO);
            cells.forEach(c -> months.put(c.yearMonth, c.amount));
            var jobs = first.developerId == null ? List.<String>of()
                : mapper.distributionJobs(id, first.developerId).stream().map(j -> j.name).toList();
            rows.add(new DistributionRow(first.rowTypeCode, first.managementNumber, first.name, jobs,
                first.itemName, months, months.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
        }
        var mismatches = mapper.mismatches(id).stream()
            .map(c -> new Mismatch(c.name, c.kind, List.of(c.yearMonth))).toList();
        return new Distribution(p, rows, mapper.distributionUploads(id), mismatches, p.distributionTotalWarning);
    }
    public byte[] getCommonProjectDistributionTemplate(String id) {
        var p = require(id);
        if (!"TK".equals(p.contractTypeCode)) throw ProjectRules.invalid("projectId");
        var headers = new ArrayList<>(List.of("구분", "관리번호", "이름", "항목명"));
        for (YearMonth m = YearMonth.from(p.startDate); !m.isAfter(YearMonth.from(p.endDate)); m = m.plusMonths(1)) headers.add(m.toString());
        headers.add("합계");
        return excel.write("배분표", headers, List.of(), actor.userId(), calendar.now());
    }
}
