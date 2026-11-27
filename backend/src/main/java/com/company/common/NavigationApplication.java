package com.company.common;

import com.company.common.dailybatch.service.DailyBatch;
import com.company.common.schema.SchemaPreparation;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

/**
 * 명령 선택지(웹 서버를 켜지 않고 한 번 돌고 끝남):
 * <ul>
 *   <li>{@code --prepare-schema}: 없는 표만 만들고 빈 표에만 처음 값을 넣는다.</li>
 *   <li>{@code --run-daily-batch[=YYYY-MM-DD]}: 02:00 일 배치(PER-004 ①~⑧)를 지금 한 번 돌린다(시험·운영용, HTTP API 없음).
 *       날짜를 주면 그날을 기준일로 본다(없으면 오늘 한국 날짜). 단계 결과를 출력하고, 실패한 단계가 있으면 끝 코드 1.</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"com.company.common", "navigation"})
public class NavigationApplication {
    static final String DAILY_BATCH = "--run-daily-batch";

    public static void main(String[] args) {
        // 서버 시각은 UTC로 다룬다(2026-11-12 결정). 서버 기본 시간대에 기대지 않는다.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        SpringApplication application = new SpringApplication(NavigationApplication.class);
        if (Arrays.asList(args).contains("--prepare-schema")) {
            application.setWebApplicationType(WebApplicationType.NONE);
            try (var context = application.run(args)) {
                context.getBean(SchemaPreparation.class).prepare();
            } catch (Exception exception) {
                System.err.println("Schema preparation refused: " + SchemaPreparation.diagnostic(exception));
                System.exit(1);
            }
        } else if (dailyBatchRequested(args)) {
            Optional<LocalDate> day;
            try { day = dailyBatchDay(args); }
            catch (IllegalArgumentException invalid) {
                System.err.println("Daily batch refused: " + invalid.getMessage());
                System.exit(2);
                return;
            }
            application.setWebApplicationType(WebApplicationType.NONE);
            boolean succeeded;
            try (var context = application.run(args)) {
                var batch = context.getBean(DailyBatch.class);
                var report = day.isPresent() ? batch.run(day.get()) : batch.run();
                report.steps().forEach(step -> System.out.println("daily-batch " + report.day() + " step "
                    + step.number() + " " + (step.succeeded() ? "OK" : "FAILED") + " " + step.name() + ": " + step.detail()));
                succeeded = report.succeeded();
            } catch (Exception exception) {
                System.err.println("Daily batch refused: " + exception.getClass().getSimpleName());
                succeeded = false;
            }
            System.exit(succeeded ? 0 : 1);
        } else {
            application.run(args);
        }
    }

    static boolean dailyBatchRequested(String[] args) {
        return Arrays.stream(args).anyMatch(arg -> arg.equals(DAILY_BATCH) || arg.startsWith(DAILY_BATCH + "="));
    }

    /** {@code --run-daily-batch} → 빈 값(오늘), {@code --run-daily-batch=2026-11-27} → 그날. 형식이 틀리면 거부. */
    static Optional<LocalDate> dailyBatchDay(String[] args) {
        for (String arg : args) {
            if (arg.equals(DAILY_BATCH)) return Optional.empty();
            if (arg.startsWith(DAILY_BATCH + "=")) {
                String value = arg.substring(DAILY_BATCH.length() + 1);
                if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
                    throw new IllegalArgumentException("date must be YYYY-MM-DD");
                try { return Optional.of(LocalDate.parse(value)); }
                catch (java.time.format.DateTimeParseException invalid) {
                    throw new IllegalArgumentException("date must be YYYY-MM-DD");
                }
            }
        }
        return Optional.empty();
    }
}
