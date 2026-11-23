package com.company.common.commonops.service;

import com.company.common.commonops.dto.CandidateOverviewContracts.*;
import com.company.common.commonops.mapper.CandidateOverviewMapper;
import java.math.BigDecimal;
import java.time.LocalDate;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CandidateResumeExtractionService {
    private static final Map<String, String> LABELS = Map.of(
        "NAME", "name", "TEL", "phone", "MAIL", "email", "ADDR", "homeAddress",
        "SKILL", "skills", "WANT", "desiredMonthlyRate", "FROM", "availableDate", "VNDR", "companyId");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+");
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])0[0-9]{1,2}[- .]?[0-9]{3,4}[- .]?[0-9]{4}(?![0-9])");
    private final CandidateOverviewMapper mapper;
    private final CandidateResumeDocumentReader documents;

    public CandidateResumeExtractionService(CandidateOverviewMapper mapper, CandidateResumeDocumentReader documents) {
        this.mapper = mapper;
        this.documents = documents;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResumeExtraction postCommonCandidateResumeExtraction(MultipartFile file) {
        String text = documents.read(file);
        var fields = new LinkedHashMap<String, ResumeField>();
        // 긴 이름표부터 본다(「기술스택」을 「기술」보다, 「소속업체」를 「소속」보다 먼저).
        var labels = mapper.dictionary("RSM_LABEL").stream()
            .filter(label -> LABELS.containsKey(label.target()) && label.name() != null && !label.name().isBlank())
            .sorted(Comparator.comparingInt((CandidateOverviewMapper.Dictionary label) -> label.name().strip().length()).reversed())
            .toList();
        var names = labels.stream().map(label -> label.name().strip()).toList();
        for (var label : labels) {
            String field = LABELS.get(label.target());
            // 이름표는 줄 처음이나 표 칸(탭) 처음에 있고, 바로 뒤에 글자·숫자가 붙지 않아야 한다. 값은 그 칸(탭 전)까지.
            var pattern = Pattern.compile("(?imu)(?:^|\\t)[\\h&&[^\\t]]*" + Pattern.quote(label.name().strip())
                + "(?![\\p{L}\\p{N}])\\h*[:：=]?\\h*([^\\t\\r\\n]*)");
            var match = pattern.matcher(text);
            while (match.find()) {
                String value = match.group(1).strip();
                String source = match.group().strip();
                // 값 칸이 비어 다음 칸의 이름표를 값으로 잡은 경우(「성명 | (빈칸) | 연락처」)는 이 줄을 쓰지 않는다.
                if (isLabel(value, names)) continue;
                // 이름표 칸에 값이 없으면(표에서 값이 다음 줄·다음 칸) 다음 빈칸 아닌 조각을 쓴다. 그 조각이 다른 이름표면 쓰지 않는다.
                if (value.isEmpty()) {
                    value = following(text, match.end(), names);
                    source = source + " " + value;
                }
                if (value.isEmpty()) continue;
                add(fields, field, value, source.strip());
                break;
            }
        }
        if (!fields.containsKey("phone")) {
            var phone = PHONE.matcher(text);
            if (phone.find()) add(fields, "phone", phone.group(), phone.group());
        }
        if (!fields.containsKey("email")) {
            var email = EMAIL.matcher(text);
            if (email.find()) add(fields, "email", email.group(), email.group());
        }
        var jobs = mapper.dictionary("JOB");
        for (var keyword : mapper.dictionary("RSM_JOB_KW")) {
            if (keyword.name() == null || keyword.name().isBlank() || keyword.target() == null
                || jobs.stream().noneMatch(job -> job.code().equals(keyword.target()))) continue;
            var match = keywordPattern(keyword.name().strip()).matcher(text);
            if (match.find()) {
                String field = !fields.containsKey("primaryJobCode") ? "primaryJobCode" : "secondaryJobCode";
                if (fields.values().stream().noneMatch(row -> row.field().endsWith("JobCode") && row.value().equals(keyword.target())))
                    fields.putIfAbsent(field, new ResumeField(field, keyword.target(), true, snippet(text, match.start(), match.end())));
            }
        }
        for (var employee : mapper.dictionary("EMP_TYPE")) {
            if (employee.name() != null && !employee.name().isBlank() && Set.of("프리랜서", "외주").contains(employee.name())) {
                var match = Pattern.compile(Pattern.quote(employee.name())).matcher(text);
                if (match.find()) fields.putIfAbsent("employeeTypeCode", new ResumeField("employeeTypeCode", employee.code(), true,
                    snippet(text, match.start(), match.end())));
            }
        }
        BigDecimal career = null;
        var years = Pattern.compile("경력\\h*[:：]?\\h*([0-9]{1,2}(?:\\.[0-9])?)\\h*년").matcher(text);
        if (years.find()) career = new BigDecimal(years.group(1));
        return new ResumeExtraction(CandidateResumeDocumentReader.safeName(file.getOriginalFilename()),
            List.copyOf(fields.values()), career);
    }

    /** 영문·숫자로 시작하거나 끝나는 단어(PM·PL 등)는 앞뒤에 영문·숫자가 붙으면 다른 낱말로 본다(development·deploy·template). */
    static Pattern keywordPattern(String keyword) {
        String start = keyword.isEmpty() || !isAsciiWord(keyword.charAt(0)) ? "" : "(?<![A-Za-z0-9])";
        String end = keyword.isEmpty() || !isAsciiWord(keyword.charAt(keyword.length() - 1)) ? "" : "(?![A-Za-z0-9])";
        return Pattern.compile("(?iu)" + start + Pattern.quote(keyword) + end);
    }

    private static boolean isAsciiWord(char c) {
        return c < 128 && Character.isLetterOrDigit(c);
    }

    /** 이름표 뒤 다음 조각(탭·줄바꿈으로 나뉜 첫 빈칸 아닌 것). 다른 이름표로 시작하면 빈 값. */
    private static String following(String text, int from, List<String> labels) {
        for (String part : text.substring(from).split("[\\t\\r\\n]+", 8)) {
            String value = part.strip();
            if (value.isEmpty()) continue;
            return isLabel(value, labels) ? "" : value;
        }
        return "";
    }

    /** value가 사전의 이름표로 시작하고 바로 뒤에 글자·숫자가 붙지 않으면 이름표로 본다. */
    private static boolean isLabel(String value, List<String> labels) {
        String lower = value.toLowerCase(Locale.ROOT);
        return labels.stream().map(name -> name.toLowerCase(Locale.ROOT)).anyMatch(name -> lower.startsWith(name)
            && (lower.length() == name.length() || !Character.isLetterOrDigit(lower.codePointAt(name.length()))));
    }

    private void add(Map<String, ResumeField> fields, String field, String raw, String source) {
        if (fields.containsKey(field)) return;
        String value = raw;
        boolean estimated = false;
        switch (field) {
            case "name" -> { if (raw.isBlank() || raw.length() > 50) return; }
            case "phone" -> {
                value = phone(raw);
                if (!value.matches("[0-9-]{1,20}")) return;
            }
            case "email" -> {
                value = raw.toLowerCase(Locale.ROOT);
                if (value.length() > 100 || !EMAIL.matcher(value).matches()) return;
            }
            case "desiredMonthlyRate" -> {
                var money = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)\\h*(만원|만|원)?(?:/월)?").matcher(raw);
                if (!money.matches()) return;
                BigDecimal number = new BigDecimal(money.group(1).replace(",", ""));
                if (money.group(2) != null && money.group(2).startsWith("만")) number = number.movePointRight(4);
                number = number.stripTrailingZeros();
                if (number.scale() > 0 || number.precision() - number.scale() > 15) return;
                value = number.toPlainString();
            }
            case "skills" -> {
                var skills = new LinkedHashSet<String>();
                for (String skill : raw.split("[,，·;]")) {
                    skill = skill.strip();
                    if (!skill.isEmpty() && skill.length() <= 50) skills.add(skill);
                }
                if (skills.isEmpty() || skills.size() > 999) return;
                value = String.join(",", skills);
            }
            case "availableDate" -> {
                try { value = LocalDate.parse(raw.replace('.', '-').replace('/', '-')).toString(); }
                catch (java.time.DateTimeException invalid) { return; }
                estimated = true;
            }
            case "companyId" -> {
                var matches = mapper.companies().stream().filter(company -> company.name().equals(raw)).toList();
                if (matches.size() != 1) return;
                value = matches.get(0).id(); estimated = true;
            }
            case "homeAddress" -> {
                var matches = mapper.areas().stream().filter(area -> prefix(raw, area.sido(), area.district())
                    || prefix(raw, area.sidoFull(), area.district())).toList();
                if (matches.size() != 1) return;
                var area = matches.get(0);
                value = area.sido() + " " + area.district();
                if (value.length() > 50) return;
                fields.putIfAbsent("homeAreaCode", new ResumeField("homeAreaCode", area.code(), false, null));
            }
            default -> { return; }
        }
        fields.put(field, new ResumeField(field, value, estimated, estimated ? bounded(source) : null));
    }

    private static boolean prefix(String text, String sido, String district) {
        if (sido == null || district == null) return false;
        return Pattern.compile("^" + Pattern.quote(sido) + "\\h+" + Pattern.quote(district) + "(?:\\h|$)").matcher(text).find();
    }

    static String phone(String input) {
        String digits = input.replaceAll("[ .]", "");
        String plain = digits.replace("-", "");
        if (plain.matches("01[016789][0-9]{7,8}")) return plain.replaceFirst("^(01[016789])([0-9]{3,4})([0-9]{4})$", "$1-$2-$3");
        if (plain.matches("02[0-9]{7,8}")) return plain.replaceFirst("^(02)([0-9]{3,4})([0-9]{4})$", "$1-$2-$3");
        if (plain.matches("0[3-6][1-5][0-9]{7,8}")) return plain.replaceFirst("^(0[3-6][1-5])([0-9]{3,4})([0-9]{4})$", "$1-$2-$3");
        return digits;
    }

    private static String snippet(String text, int start, int end) {
        int from = text.lastIndexOf('\n', start) + 1;
        int through = text.indexOf('\n', end);
        return bounded(text.substring(from, through < 0 ? text.length() : through).strip());
    }
    private static String bounded(String source) { return source.substring(0, Math.min(source.length(), 200)); }
}
