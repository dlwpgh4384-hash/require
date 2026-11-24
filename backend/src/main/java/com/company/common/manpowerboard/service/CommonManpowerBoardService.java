package com.company.common.manpowerboard.service;

import com.company.common.common.ManpowerBoardDefinitions;
import com.company.common.manpowerboard.dto.ManpowerBoardContracts.*;
import com.company.common.manpowerboard.mapper.ManpowerBoardMapper;
import com.company.common.manpowerboard.mapper.ManpowerBoardRows.*;
import com.company.common.support.service.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.LocalDate;
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

    public Result getCommonManpowerBoard(Filter filter) { return board(filter).result(); }

    /** 화면 결과와 그 계산에 쓴 판정(엑셀 가배정 기회명을 같은 판정에서 읽는다). */
    private record Board(Result result, ManpowerBoardRules rules, List<Developer> people) {}

    private Board board(Filter filter) {
        if (filter.year() == null || (filter.year() != 2026 && filter.year() != 2027)) {
            throw CodeFailure.invalid("year", "2026년 또는 2027년을 선택해 주세요.");
        }
        var codes = mapper.codes();
        validateCode(codes, "JOB", "jobCode", filter.jobCode());
        validateCode(codes, "GRADE", "gradeCode", filter.gradeCode());
        validateCode(codes, "EMP_TYPE", "employeeTypeCode", filter.employeeTypeCode());
        definitions.definitions();
        var now = calendar.currentMonth();
        var yearStart = LocalDate.of(filter.year(), 1, 1);
        var yearEnd = LocalDate.of(filter.year(), 12, 31);
        var from = now.atDay(1).isBefore(yearStart) ? now.atDay(1) : yearStart;
        var to = now.plusMonths(3).atEndOfMonth().isAfter(yearEnd) ? now.plusMonths(3).atEndOfMonth() : yearEnd;
        var period = new Period(from, to);
        var rules = new ManpowerBoardRules(calendar.today(), mapper.assignments(period), mapper.monthly(period),
            mapper.history(period), mapper.statuses(period), mapper.tentative(period));
        var months = new ArrayList<YearMonth>();
        for (int i = 1; i <= 12; i++) months.add(YearMonth.of(filter.year(), i));
        var roster = mapper.developers();
        var filtered = roster.stream()
            .filter(d -> filter.jobCode() == null || filter.jobCode().equals(d.jobCode()))
            .filter(d -> filter.gradeCode() == null || filter.gradeCode().equals(d.gradeCode()))
            .filter(d -> filter.employeeTypeCode() == null || filter.employeeTypeCode().equals(d.employeeTypeCode()))
            .toList();
        // 시안 정렬: 마지막 투입 종료일 빠른 순(투입이 없으면 맨 앞 '0'), 그다음 이름(한국어 순).
        var korean = Collator.getInstance(Locale.KOREAN);
        var people = filtered.stream()
            .filter(d -> !filter.onlyAvailable() || months.stream().anyMatch(m -> rules.available(d, m).signum() > 0))
            .sorted(Comparator.comparing(Developer::lastAssignmentEndDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Developer::name, korean).thenComparing(Developer::developerId))
            .toList();
        var items = people.stream().map(d -> new Person(d.developerId(), d.managementNumber(), d.name(), d.jobName(),
            months.stream().map(m -> rules.cell(d, m)).toList())).toList();
        // 비어 있는 인원·남는 MM은 조건(직무·등급·직원구분)에 맞는 사람, 철수 예정·연장 높음·동시 투입·1.0 초과는
        // 직접 인력 전체로 센다(시안 cnt·free는 ps, 나머지는 PEOPLE).
        int idle = 0, withdrawing = 0, high = 0, concurrent = 0, excess = 0;
        BigDecimal available = BigDecimal.ZERO, future = BigDecimal.ZERO;
        for (var person : filtered) {
            if ("idle".equals(rules.cell(person, now).statusCode())) idle++;
            available = available.add(rules.available(person, now));
            future = future.add(rules.available(person, now.plusMonths(3)));
        }
        for (var person : roster) {
            var current = rules.cell(person, now);
            if (current.projects().size() >= 2 && !current.tentative()) concurrent++;
            if (current.totalMm().compareTo(BigDecimal.ONE) > 0) excess++;
            if ("end".equals(current.statusCode()) && rules.assignedFromToday(person, now)) {
                withdrawing++;
                if (rules.highExtension(person, now)) high++;
            }
        }
        var totals = months.stream().map(m -> {
            if (m.isBefore(now)) return new MonthTotal(m.toString(), null, null);
            int count = (int) filtered.stream().filter(d -> "idle".equals(rules.cell(d, m).statusCode())).count();
            BigDecimal mm = filtered.stream().map(d -> rules.available(d, m)).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new MonthTotal(m.toString(), count, mm);
        }).toList();
        return new Board(new Result(filter.year(), new Summary(idle, available, withdrawing, high, concurrent, excess, future),
            items, totals, items.size()), rules, people);
    }

    private static void validateCode(List<Code> codes, String group, String field, String value) {
        if (value != null && codes.stream().noneMatch(c -> group.equals(c.groupCode()) && value.equals(c.code()))) {
            throw CodeFailure.invalid(field, "사용 가능한 공통코드를 선택해 주세요.");
        }
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public ExcelFile getCommonManpowerBoardExcel(Filter filter) {
        var board = board(filter);
        var result = board.result();
        var headers = new ArrayList<String>(List.of("이름", "직무"));
        for (int month = 1; month <= 12; month++) headers.add(month + "월");
        List<List<String>> rows = new ArrayList<>();
        for (int index = 0; index < result.items().size(); index++) {
            var person = result.items().get(index);
            var developer = board.people().get(index);
            var row = new ArrayList<String>(List.of(person.name(), person.jobName()));
            for (var cell : person.cells()) {
                if (cell.tentative()) {
                    // 가배정은 MM 없이 「가배정 · 기회명 (n%)」.
                    row.add("가배정 · " + board.rules().tentatives(developer, YearMonth.parse(cell.month())).stream()
                            .map(Tentative::projectName).distinct().collect(Collectors.joining(" / "))
                        + (cell.winningProbability() == null ? ""
                            : " (" + cell.winningProbability().stripTrailingZeros().toPlainString() + "%)"));
                    continue;
                }
                String projects = cell.projects().stream().map(p -> p.projectName() + " " + p.monthlyMm().toPlainString() + " MM")
                    .collect(Collectors.joining(" / "));
                row.add(cell.statusName() + (projects.isEmpty() ? "" : " · " + projects)
                    + (cell.totalMm().compareTo(BigDecimal.ONE) > 0 ? " · 합계 " + cell.totalMm().toPlainString() + " MM" : ""));
            }
            rows.add(row);
        }
        // 합계 줄(시안 tfoot): 비는 인원 n명, 남는 MM 소수 첫째 자리. 지난 달은 「–」.
        var idleRow = new ArrayList<String>(List.of("비는 인원", ""));
        var freeRow = new ArrayList<String>(List.of("남는 MM", "일부 투입 포함"));
        for (var total : result.monthTotals()) {
            idleRow.add(total.idleCount() == null ? "–" : total.idleCount() + "명");
            freeRow.add(total.availableMm() == null ? "–" : total.availableMm().setScale(1, RoundingMode.HALF_UP).toPlainString());
        }
        rows.add(idleRow);
        rows.add(freeRow);
        var now = calendar.now();
        byte[] bytes = excel.write(TITLE, headers, rows, actor.userId(), now);
        if (mapper.insertAccess(actor.userId(), TITLE + " " + filter.year() + "년 " + result.totalCount() + "건") != 1) {
            throw new IllegalStateException("다운로드 접근 기록을 저장하지 못했습니다.");
        }
        return new ExcelFile(bytes, excel.fileName(TITLE, filter.year() + "년", now), result.totalCount());
    }
}
