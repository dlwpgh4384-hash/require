package com.company.common.manpowerboard.service;

import com.company.common.common.ManpowerBoardDefinitions;
import com.company.common.manpowerboard.dto.ManpowerBoardContracts.*;
import com.company.common.manpowerboard.mapper.ManpowerBoardMapper;
import com.company.common.manpowerboard.mapper.ManpowerBoardRows.*;
import com.company.common.support.service.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CommonManpowerBoardService {
    private static final String TITLE = "인력 운영 보드";
    private final ManpowerBoardMapper mapper;
    private final ManpowerBoardDefinitions definitions;
    private final BusinessCalendar calendar;
    private final ExcelExportService excel;
    private final CurrentBusinessActor actor;

    public CommonManpowerBoardService(ManpowerBoardMapper mapper, ManpowerBoardDefinitions definitions,
        BusinessCalendar calendar, ExcelExportService excel, CurrentBusinessActor actor) {
        this.mapper = mapper;
        this.definitions = definitions;
        this.calendar = calendar;
        this.excel = excel;
        this.actor = actor;
    }

    public Result getCommonManpowerBoard(Filter filter) {
        if (filter.year() == null || (filter.year() != 2026 && filter.year() != 2027)) {
            throw CodeFailure.invalid("year", "2026년 또는 2027년을 선택해 주세요.");
        }
        var codes = mapper.codes();
        validateCode(codes, "JOB", "jobCode", filter.jobCode());
        validateCode(codes, "GRADE", "gradeCode", filter.gradeCode());
        validateCode(codes, "EMP_TYPE", "employeeTypeCode", filter.employeeTypeCode());
        definitions.definitions();
        var rules = new ManpowerBoardRules(calendar.today(), mapper.assignments(filter), mapper.monthly(filter),
            mapper.history(filter), mapper.statuses(filter), mapper.tentative(filter));
        var months = new ArrayList<YearMonth>();
        for (int i = 1; i <= 12; i++) months.add(YearMonth.of(filter.year(), i));
        var people = mapper.developers(filter).stream()
            .filter(d -> !filter.onlyAvailable() || months.stream().anyMatch(m -> rules.available(d, m).signum() > 0))
            .sorted(Comparator.comparing((Developer d) -> rules.withdrawalDate(d),
                Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Developer::name).thenComparing(Developer::developerId))
            .toList();
        var items = people.stream().map(d -> new Person(d.developerId(), d.name(), d.jobName(),
            months.stream().map(m -> rules.cell(d, m)).toList())).toList();
        var now = calendar.currentMonth();
        int idle = 0, withdrawing = 0, high = 0, concurrent = 0, excess = 0;
        BigDecimal available = BigDecimal.ZERO, future = BigDecimal.ZERO;
        for (var person : people) {
            var current = rules.cell(person, now);
            available = available.add(rules.available(person, now));
            future = future.add(rules.available(person, now.plusMonths(3)));
            if ("비어 있음".equals(current.statusName())) idle++;
            if (!rules.eligible(person, now)) continue;
            var active = rules.active(person, now);
            if (active.size() >= 2) concurrent++;
            if (current.totalMm().compareTo(BigDecimal.ONE) > 0) excess++;
            if (!active.isEmpty() && active.stream().allMatch(rules::withdrawal)) {
                withdrawing++;
                if (active.stream().anyMatch(a -> "H".equals(a.extensionCode()))) high++;
            }
        }
        var totals = months.stream().map(m -> {
            if (m.isBefore(now)) return new MonthTotal(m.toString(), null, null);
            int count = (int) people.stream().filter(d -> "비어 있음".equals(rules.cell(d, m).statusName())).count();
            BigDecimal mm = people.stream().map(d -> rules.available(d, m)).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new MonthTotal(m.toString(), count, mm);
        }).toList();
        return new Result(filter.year(), new Summary(idle, available, withdrawing, high, concurrent, excess, future),
            items, totals, items.size());
    }

    private static void validateCode(List<Code> codes, String group, String field, String value) {
        if (value != null && codes.stream().noneMatch(c -> group.equals(c.groupCode()) && value.equals(c.code()))) {
            throw CodeFailure.invalid(field, "사용 가능한 공통코드를 선택해 주세요.");
        }
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public ExcelFile getCommonManpowerBoardExcel(Filter filter) {
        var result = getCommonManpowerBoard(filter);
        var headers = new ArrayList<String>(List.of("이름", "직무"));
        for (int month = 1; month <= 12; month++) headers.add(month + "월");
        List<List<String>> rows = result.items().stream().map(person -> {
            var row = new ArrayList<String>(List.of(person.name(), person.jobName()));
            for (var cell : person.cells()) {
                String projects = cell.projects().stream().map(p -> p.projectName() + " " + p.monthlyMm().toPlainString() + " MM")
                    .collect(Collectors.joining(" / "));
                row.add(cell.statusName() + (projects.isEmpty() ? "" : " · " + projects)
                    + (cell.totalMm().compareTo(BigDecimal.ONE) > 0 ? " · 합계 " + cell.totalMm().toPlainString() + " MM" : ""));
            }
            return (List<String>) row;
        }).toList();
        var now = calendar.now();
        byte[] bytes = excel.write(TITLE, headers, rows, actor.userId(), now);
        if (mapper.insertAccess(actor.userId(), TITLE + " " + filter.year() + "년 " + result.totalCount() + "건") != 1) {
            throw new IllegalStateException("다운로드 접근 기록을 저장하지 못했습니다.");
        }
        return new ExcelFile(bytes, excel.fileName(TITLE, filter.year() + "년", now), result.totalCount());
    }
}
