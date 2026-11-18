package com.company.common.manpowercomparisons.service;

import com.company.common.manpowercomparisons.dto.ManpowerComparisonContracts.*;
import com.company.common.manpowercomparisons.mapper.ManpowerComparisonMapper;
import com.company.common.projects.service.ProjectRules;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.support.service.ExcelExportService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommonManpowerComparisonService {
    private static final String SCREEN = "인원 계획 대비 투입";
    private final ManpowerComparisonMapper mapper;
    private final ManpowerComparisonRules rules;
    private final ExcelExportService excel;
    private final BusinessCalendar calendar;
    private final CurrentBusinessActor actor;

    /** 엑셀 파일 한 개: 내용, 파일 이름, 조회 결과 건수. */
    public record ExcelFile(byte[] bytes, String fileName, int count) {}

    public CommonManpowerComparisonService(ManpowerComparisonMapper mapper, ManpowerComparisonRules rules,
        ExcelExportService excel, BusinessCalendar calendar, CurrentBusinessActor actor) {
        this.mapper = mapper;
        this.rules = rules;
        this.excel = excel;
        this.calendar = calendar;
        this.actor = actor;
    }

    public Result getCommonManpowerComparisons(Filter filter) {
        if (filter.year() == null || filter.year() < 2025 || filter.year() > 2027) {
            throw CodeFailure.invalid("year", "2025년부터 2027년까지 선택해 주세요.");
        }
        if (filter.projectId() != null) ProjectRules.id(filter.projectId());
        if (filter.differenceStatus() != null
            && !Set.of("부족", "초과", "맞음").contains(filter.differenceStatus())) {
            throw CodeFailure.invalid("differenceStatus", "부족, 초과, 맞음 중 선택해 주세요.");
        }
        rules.requireDefinitions();
        var items = mapper.selectSources(filter).stream()
            .map(source -> rules.compare(source, filter.year()))
            .filter(row -> filter.differenceStatus() == null || filter.differenceStatus().equals(row.differenceStatus()))
            .sorted(Comparator.comparing(Row::differenceMm).thenComparing(Row::projectId).thenComparing(Row::jobCode))
            .toList();
        var planned = BigDecimal.ZERO;
        var actual = BigDecimal.ZERO;
        var difference = BigDecimal.ZERO;
        for (var row : items) {
            planned = planned.add(row.plannedMm());
            actual = actual.add(row.actualMm());
            difference = difference.add(row.differenceMm());
        }
        return new Result(items, new Totals(planned, actual, difference));
    }

    /**
     * SFR-011: 목록과 같은 조건의 전체 결과를 화면 열(프로젝트·직무·계획 MM·실제·예정 MM·차이) 그대로 xlsx로 만든다.
     * 파일 1행에 받은 사람·한국 시각, 이름은 「인원 계획 대비 투입_{해}년_다운로드일시.xlsx」, 접근 기록 DNLD.
     */
    @Transactional
    public ExcelFile getCommonManpowerComparisonsExcel(Filter filter) {
        var items = getCommonManpowerComparisons(filter).items();
        List<List<String>> rows = items.stream().map(row -> List.of(row.projectName(), row.jobName(),
            mm(row.plannedMm()), mm(row.actualMm()), difference(row))).toList();
        var now = calendar.now();
        byte[] bytes = excel.write(SCREEN, List.of("프로젝트", "직무", "계획 MM", "실제·예정 MM", "차이"),
            rows, actor.userId(), now);
        mapper.insertAccess(actor.userId(), SCREEN + " " + filter.year() + "년 " + items.size() + "건");
        return new ExcelFile(bytes, excel.fileName(SCREEN, filter.year() + "년", now), items.size());
    }

    /** 화면 숫자와 같게 소수 둘째 자리(시안 mmf). */
    private static String mm(BigDecimal value) {
        return value == null ? "–" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 화면 차이 배지와 같은 글자: 부족 1.50 · 초과 0.40 · 맞음. */
    private static String difference(Row row) {
        if ("맞음".equals(row.differenceStatus())) return "맞음";
        return row.differenceStatus() + " " + mm(row.differenceMm().abs());
    }
}
