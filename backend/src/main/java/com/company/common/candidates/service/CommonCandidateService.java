package com.company.common.candidates.service;

import com.company.common.support.service.CurrentBusinessActor;

import com.company.common.candidates.dto.CandidateContracts.*;
import com.company.common.candidates.mapper.CandidateMapper;
import com.company.common.candidates.mapper.CandidateRows.*;
import com.company.common.common.CandidateCodes;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommonCandidateService {
    private final CandidateMapper mapper;
    private final CandidateCodes codes;
    private final BusinessCalendar calendar;
    private final CandidateCalculationRules calculations;
    public CommonCandidateService(CandidateMapper mapper, CandidateCodes codes,
        BusinessCalendar calendar, CandidateCalculationRules calculations) {
        this.mapper = mapper;
        this.codes = codes;
        this.calendar = calendar;
        this.calculations = calculations;
    }
    public Instant now() { return calendar.now(); }
    /** 후보 목록 엑셀을 받으면 접근 기록(DNLD)을 남긴다(SFR-011-03, 다른 목록 엑셀과 같음). */
    @Transactional
    public void recordDownload(String userId, int count) { mapper.insertAccess(userId, "외부 인력 후보 " + count + "건"); }
    private CandidateRow require(String id, boolean lock) {
        if (id == null || id.isBlank() || id.length() > 20) throw CandidateRules.invalid("candidateId");
        var row = lock ? mapper.lockCandidate(id) : mapper.selectCandidate(id);
        if (row == null) throw new CodeFailure(404, "candidateId", "후보를 찾을 수 없습니다.");
        return row;
    }
    private void selectable(String group, String value, String field) {
        if (value != null && codes.selectable(group).stream().noneMatch(c -> value.equals(c.code)))
            throw CandidateRules.invalid(field);
    }
    private Input prepare(Input in, CandidateRow old) {
        selectable("EMP_TYPE", in.employeeTypeCode(), "employeeTypeCode");
        selectable("JOB", in.primaryJobCode(), "primaryJobCode");
        selectable("JOB", in.secondaryJobCode(), "secondaryJobCode");
        selectable("GRADE", in.gradeCode(), "gradeCode");
        selectable("CAND_SRC", in.sourceCode(), "sourceCode");
        if ("OS".equals(in.employeeTypeCode()) && !mapper.selectableCompany(in.companyId()))
            throw CandidateRules.invalid("companyId");
        var area = in.homeAreaCode() == null ? null : mapper.selectArea(in.homeAreaCode());
        boolean sameArea = old != null && in.homeAreaCode() != null && in.homeAreaCode().equals(old.homeAreaCode());
        if (in.homeAreaCode() != null && area == null && !sameArea) throw CandidateRules.invalid("homeAreaCode");
        String address = area != null ? area.address() : sameArea ? old.homeAddress() : null;
        if (address != null && address.codePointCount(0, address.length()) > 50) throw CandidateRules.invalid("homeAddress");
        // 민감 체크가 없으면 희망 단가는 받지 않고 저장된 값을 그대로 둔다(명세 ⑨-2).
        var rate = old != null && !com.company.common.support.auth.RequestAccess.sensitiveVisible()
            ? old.desiredMonthlyRate() : in.desiredMonthlyRate();
        return new Input(in.name(), rate, in.employeeTypeCode(), in.companyId(),
            in.phone(), in.email(), in.privacyAgreement(), in.primaryJobCode(), in.secondaryJobCode(),
            in.skills(), in.gradeCode(), in.availableDate(), in.homeAreaCode(), address, in.sourceCode(), in.memo());
    }
    private String name(String group, String code) { return code == null ? null : codes.name(group, code); }
    private Candidate detail(CandidateRow row) {
        LocalDate last = row.lastContactDate() != null ? row.lastContactDate()
            : row.registeredAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
        int days = Math.toIntExact(Math.max(0, ChronoUnit.DAYS.between(last, calendar.today())));
        boolean overdue = days > 30 && !Set.of("HOLD", "CTRT").contains(row.contactStatusCode());
        return new Candidate(row.candidateId(), row.name(), row.desiredMonthlyRate(), row.employeeTypeCode(),
            row.companyId(), row.phone(), row.email(), "Y".equals(row.privacyAgreementYn()), row.primaryJobCode(),
            row.secondaryJobCode(), mapper.selectSkills(row.candidateId()), row.gradeCode(), row.availableDate(),
            row.homeAreaCode(), row.homeAddress(), row.sourceCode(), row.memo(), name("EMP_TYPE", row.employeeTypeCode()),
            row.companyName(), name("JOB", row.primaryJobCode()), name("JOB", row.secondaryJobCode()),
            name("GRADE", row.gradeCode()), name("CAND_SRC", row.sourceCode()), row.contactStatusCode(),
            name("CNTC_STAT", row.contactStatusCode()), last, row.modifiedAt(), row.privacyAgreementDate(),
            row.privacyAgreementUserId(), row.privacyAgreementUserName(), days, overdue);
    }
    public Candidate getCommonCandidate(String id) { return detail(require(id, false)); }
    public Items<Candidate> getCommonCandidates(Filter filter) {
        var f = new Filter(CandidateRules.optional(filter.jobCode(), "jobCode"),
            CandidateRules.optional(filter.gradeCode(), "gradeCode"),
            CandidateRules.optional(filter.employeeTypeCode(), "employeeTypeCode"),
            CandidateRules.optional(filter.contactStatusCode(), "contactStatusCode"), filter.availabilityWindow());
        selectable("JOB", f.jobCode(), "jobCode");
        selectable("GRADE", f.gradeCode(), "gradeCode");
        selectable("EMP_TYPE", f.employeeTypeCode(), "employeeTypeCode");
        selectable("CNTC_STAT", f.contactStatusCode(), "contactStatusCode");
        LocalDate through = null;
        if (f.availabilityWindow() != null && !f.availabilityWindow().isBlank()) {
            through = switch (f.availabilityWindow()) {
                case "next-month" -> calendar.currentMonth().plusMonths(1).atEndOfMonth();
                case "within-three-months" -> calendar.currentMonth().plusMonths(2).atEndOfMonth();
                default -> throw CandidateRules.invalid("availabilityWindow");
            };
        }
        return new Items<>(mapper.selectCandidates(new Query(f, through)).stream().map(this::detail).toList());
    }
    private void skills(String id, Input in) {
        mapper.deleteSkills(id);
        int sort = 1;
        for (String skill : in.skills()) mapper.insertSkill(id, skill, sort++);
    }
    @Transactional
    public Candidate postCommonCandidate(Input request) {
        Input in = prepare(request, null);
        String id;
        do {
            id = "C" + UUID.randomUUID().toString().replace("-", "").substring(0, 19);
        } while (mapper.insertCandidate(new Write(id, in, calendar.today())) == 0);
        skills(id, in);
        mapper.insertContact(new ContactWrite(id, "BEFORE", "등록", calendar.today(), "SYSTEM"));
        return detail(require(id, false));
    }
    @Transactional
    public Candidate putCommonCandidate(String id, Input request, Instant modifiedAt) {
        var old = require(id, true);
        if (!Objects.equals(old.modifiedAt(), modifiedAt)) throw CodeFailure.conflict();
        Input in = prepare(request, old);
        mapper.updateCandidate(new Write(id, in, calendar.today()));
        skills(id, in);
        mapper.insertContact(new ContactWrite(id, old.contactStatusCode(), "정보 수정", calendar.today(), "SYSTEM"));
        return detail(require(id, false));
    }
    public Items<Contact> getCommonCandidateContacts(String id) {
        require(id, false);
        return new Items<>(mapper.selectContacts(id).stream().map(row -> new Contact(row.contactStatusCode(),
            name("CNTC_STAT", row.contactStatusCode()), row.memo(), row.recordedAt(), row.actor(),
            "SYSTEM".equals(row.actor()))).toList());
    }
    @Transactional
    public Candidate postCommonCandidateContact(String id, ContactInput in) {
        var old = require(id, true);
        if (!Objects.equals(old.modifiedAt(), in.modifiedAt())) throw CodeFailure.conflict();
        selectable("CNTC_STAT", in.contactStatusCode(), "contactStatusCode");
        var write = new ContactWrite(id, in.contactStatusCode(), in.memo(), calendar.today(), CurrentBusinessActor.id());
        mapper.insertContact(write);
        mapper.updateContact(write);
        return detail(require(id, false));
    }
    private record Ranked(Match match, int distance, java.math.BigDecimal sequence) {}
    public Items<Match> getCommonCandidateMatches(String id) {
        var candidate = require(id, false);
        var terms = calculations.terms("OS".equals(candidate.employeeTypeCode()), calendar.currentMonth());
        var grades = codes.selectable("GRADE").stream().map(c -> c.code).toList();
        int ownGrade = grades.indexOf(candidate.gradeCode());
        var available = YearMonth.from(candidate.availableDate());
        var ranked = new ArrayList<Ranked>();
        for (var seat : mapper.selectSeats(candidate.primaryJobCode())) {
            if (!candidate.primaryJobCode().equals(seat.jobCode()) || seat.slotMonthlyRate() == null
                || seat.slotMonthlyRate().signum() <= 0 || seat.durationMonths().signum() <= 0) continue;
            var start = calendar.fromDatabaseMonth(seat.startMonth());
            if (start.plusMonths(seat.durationMonths().longValueExact() - 1).isBefore(available)) continue;
            var profit = terms.profit(seat.slotMonthlyRate(), candidate.desiredMonthlyRate());
            int grade = grades.indexOf(seat.gradeCode());
            var match = new Match(seat.opportunityId(), seat.opportunityName(), seat.winningProbability(),
                name("JOB", seat.jobCode()), Objects.requireNonNullElse(name("GRADE", seat.gradeCode()), "–"),
                start.toString(), seat.slotMonthlyRate(), profit, terms.lowMargin(profit), terms.lowMarginRate());
            ranked.add(new Ranked(match, ownGrade < 0 || grade < 0 ? Integer.MAX_VALUE : Math.abs(ownGrade - grade), seat.sequence()));
        }
        ranked.sort(Comparator.comparingInt(Ranked::distance)
            .thenComparing(r -> r.match().expectedProfitRate(), Comparator.reverseOrder())
            .thenComparing(r -> r.match().opportunityId()).thenComparing(Ranked::sequence));
        return new Items<>(ranked.stream().limit(4).map(Ranked::match).toList());
    }
}
