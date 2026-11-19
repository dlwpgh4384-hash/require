package com.company.common.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

public final class ProjectBoardStageCalendar {
    private ProjectBoardStageCalendar() {}

    public record Definition(String code, String name, BigDecimal weight, String contractTypes, int sortOrder) {}
    public record Plan(String code, String name, BigDecimal weight, LocalDate plannedStartDate,
                       LocalDate plannedEndDate) {}

    public static List<Plan> allocate(String contractType, LocalDate start, LocalDate end,
                                      List<Definition> definitions) {
        int count = switch (contractType) {
            case "TK", "IN" -> 7;
            case "MO", "DP" -> 5;
            default -> throw new IllegalArgumentException("계약유형을 확인해 주세요.");
        };
        if (start == null || end == null || end.isBefore(start))
            throw new IllegalArgumentException("계약기간을 확인해 주세요.");
        var selected = definitions.stream()
            .filter(row -> row.contractTypes() != null && Arrays.stream(row.contractTypes().split(","))
                .map(String::trim).anyMatch(contractType::equals))
            .sorted(Comparator.comparingInt(Definition::sortOrder).thenComparing(Definition::code)).toList();
        var codes = new HashSet<String>();
        BigDecimal total = BigDecimal.ZERO;
        for (var row : selected) {
            if (row.code() == null || row.code().isBlank() || !codes.add(row.code())
                    || row.name() == null || row.name().isBlank() || row.weight() == null
                    || row.weight().signum() <= 0)
                throw new IllegalStateException("PROJ_STEP 공통코드 정의를 확인해 주세요.");
            total = total.add(row.weight());
        }
        if (selected.size() != count || total.compareTo(BigDecimal.valueOf(100)) != 0)
            throw new IllegalStateException("PROJ_STEP 단계 수와 비중 합계를 확인해 주세요.");
        long duration = ChronoUnit.DAYS.between(start, end) + 1;
        long allocated = 0;
        BigDecimal cumulative = BigDecimal.ZERO;
        var result = new ArrayList<Plan>();
        for (int index = 0; index < count; index++) {
            var row = selected.get(index);
            cumulative = cumulative.add(row.weight());
            long boundary = BigDecimal.valueOf(duration).multiply(cumulative)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
            long firstDay;
            if (duration >= count) {
                boundary = Math.max(allocated + 1, Math.min(boundary, duration - (count - index - 1)));
                firstDay = allocated;
            } else {
                boundary = Math.max(Math.max(1, allocated), Math.min(boundary, duration));
                firstDay = Math.min(allocated, boundary - 1);
            }
            result.add(new Plan(row.code(), row.name(), row.weight(), start.plusDays(firstDay),
                start.plusDays(boundary - 1)));
            allocated = boundary;
        }
        return List.copyOf(result);
    }
}
