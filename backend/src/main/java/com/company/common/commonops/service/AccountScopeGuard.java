package com.company.common.commonops.service;

import com.company.common.commonops.mapper.AccountAccessMapper;
import com.company.common.users.service.UserScopePolicy;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 범위 밖 ID를 주소로 직접 부르면 서버가 거부한다(명세 ⑧-2·⑦-2). 상세·변경·이력 주소의 프로젝트(또는 그 청구·배정·이슈의
 * 프로젝트)가 「내 프로젝트」이거나 현재 영업 파트가 「내 파트」일 때만 통과. 목록은 각 매퍼가 WHERE로 거른다.
 * 등록·변경처럼 대상 ID가 요청 본문에 있는 쓰기도 같은 규칙으로 거른다({@link #checkBody}).
 */
public final class AccountScopeGuard {
    public static final String OUT_OF_SCOPE = "이 건은 지금 볼 수 있는 범위 밖입니다.";
    private record Route(Pattern path, String kind) {}
    /** 이름 있는 주소(excel·candidates 등)는 ID가 아니다. */
    private static final Set<String> NAMED = Set.of("excel", "budget-rule", "unpaid-count", "candidates",
        "contract-developers", "cancel", "extension-possibility", "tentative-assignments", "close-confirm",
        "tentative-assignment-check");
    private static final List<Route> ROUTES = List.of(
        new Route(Pattern.compile("^/api/common/projects/([^/]+)(/.*)?$"), "PROJ"),
        new Route(Pattern.compile("^/api/common/project-cost-analyses/([^/]+)(/.*)?$"), "PROJ"),
        new Route(Pattern.compile("^/api/common/bills/([^/]+)(/.*)?$"), "BILL"),
        new Route(Pattern.compile("^/api/common/receipts/([^/]+)(/.*)?$"), "RCPT"),
        new Route(Pattern.compile("^/api/common/assignments/([^/]+)(/.*)?$"), "ASGN"),
        new Route(Pattern.compile("^/api/common/withdrawals/([^/]+)(/.*)?$"), "ASGN"),
        new Route(Pattern.compile("^/api/common/project-issues/([^/]+)(/.*)?$"), "ISSUE"),
        new Route(Pattern.compile("^/api/common/sales-opportunities/([^/]+)(/.*)?$"), "OPP"),
        new Route(Pattern.compile("^/api/common/ai-recommendations/([^/]+)/.*$"), "RCMD"),
        new Route(Pattern.compile("^/api/common/part-settlements/([^/]+)/.*$"), "PART"));

    private AccountScopeGuard() {}

    public static void check(AccountAccessMapper mapper, String path, UserScopePolicy.Scope scope) {
        if (scope == null || scope.all()) return;
        for (Route route : ROUTES) {
            var match = route.path().matcher(path);
            if (!match.matches()) continue;
            String id = match.group(1);
            if (NAMED.contains(id)) return;
            target(mapper, route.kind(), id, scope);
            return;
        }
    }

    /** 한 대상(종류·ID)이 범위 안인지. 없는 건은 화면 API가 404·검사 오류로 답하게 통과시킨다. */
    static void target(AccountAccessMapper mapper, String kind, String id, UserScopePolicy.Scope scope) {
        if (id == null || id.isBlank()) return;
        if (kind.equals("PART") || kind.equals("OPP")) {
            // 영업 기회는 그 기회의 영업 파트로 본다(파트장은 자기 파트만, PM은 0건 — 명세 ⑧-3).
            String part = kind.equals("PART") ? id : mapper.opportunityPart(id);
            if (part != null && !scope.partIds().contains(part)) throw denied();
            return;
        }
        if (kind.equals("RCMD")) {
            // 추천 기록은 그 대상(프로젝트 또는 영업 기회)으로 본다.
            String project = mapper.projectOf("RCMD", id);
            if (project != null) target(mapper, "PROJ", project, scope);
            else target(mapper, "OPP", mapper.recommendationOpportunity(id), scope);
            return;
        }
        String project = mapper.projectOf(kind, id);
        if (project == null || scope.projectIds().contains(project)) return;
        if (scope.partIds().isEmpty() || !mapper.projectInParts(project, scope.partIds())) throw denied();
    }

    private static final Pattern OPPORTUNITY = Pattern.compile("^/api/common/sales-opportunities/[^/]+$");

    /**
     * 대상 ID가 본문에 있는 쓰기인지(명세 ⑧-2 「범위 밖 ID를 직접 부르면 서버가 거부」). 주소에 ID가 없거나 본문으로 대상을
     * 바꾸는 쓰기만 적는다. 주소에 ID가 있는 쓰기는 {@link #check}가 이미 본다.
     */
    public static boolean readsBody(String method, String path) {
        return switch (method + " " + path) {
            case "POST /api/common/receipts", "POST /api/common/bills", "POST /api/common/bill-checks/missing",
                 "POST /api/common/bill-checks/additional", "POST /api/common/bill-checks/correction",
                 "POST /api/common/assignments", "POST /api/common/assignments/cancel",
                 "PUT /api/common/assignments/extension-possibility", "POST /api/common/project-issues",
                 "PUT /api/common/part-settlements/close-confirm", "POST /api/common/sales-opportunities",
                 "POST /api/common/ai-recommendations", "PUT /api/common/developers/use-status" -> true;
            default -> method.equals("PUT") && OPPORTUNITY.matcher(path).matches();
        };
    }

    /** 본문의 대상들을 범위로 본다. 모양이 틀린 본문은 화면 API가 400으로 답하게 그냥 둔다. */
    public static void checkBody(AccountAccessMapper mapper, String path, JsonNode body,
                                 UserScopePolicy.Scope scope, boolean developerPartOnly) {
        if (body == null || !body.isObject() || scope == null || scope.all()) return;
        if (path.equals("/api/common/developers/use-status")) {
            // 개발자 풀은 「담당 파트」 역할(전체 없음)만 자기 파트 인력으로 거른다(⑧-3). PM은 거르지 않는다.
            if (!developerPartOnly) return;
            for (JsonNode id : array(body, "developerIds")) if (id.isTextual()) {
                String part = mapper.developerPart(id.textValue());
                if (part != null && !scope.partIds().contains(part)) throw denied();
            }
            return;
        }
        switch (path) {
            case "/api/common/receipts" -> {
                for (JsonNode item : array(body, "items")) target(mapper, "BILL", text(item, "billId"), scope);
            }
            case "/api/common/bills", "/api/common/bill-checks/missing", "/api/common/bill-checks/additional",
                 "/api/common/bill-checks/correction", "/api/common/project-issues" ->
                target(mapper, "PROJ", text(body, "projectId"), scope);
            case "/api/common/assignments" -> {
                target(mapper, "PROJ", text(body, "projectId"), scope);
                for (JsonNode id : array(body, "earlyWithdrawalAssignmentIds"))
                    if (id.isTextual()) target(mapper, "ASGN", id.textValue(), scope);
            }
            case "/api/common/assignments/cancel", "/api/common/assignments/extension-possibility" -> {
                for (JsonNode item : array(body, "assignments")) target(mapper, "ASGN", text(item, "assignmentId"), scope);
            }
            case "/api/common/part-settlements/close-confirm" -> {
                for (JsonNode item : array(body, "items")) target(mapper, "PART", text(item, "partId"), scope);
            }
            case "/api/common/ai-recommendations" -> {
                String type = text(body, "targetType");
                if ("project".equals(type)) target(mapper, "PROJ", text(body, "targetId"), scope);
                else if ("opportunity".equals(type)) target(mapper, "OPP", text(body, "targetId"), scope);
            }
            default -> {
                // 영업 기회 등록·수정: 내 파트 밖으로 등록하거나 옮길 수 없다.
                if (path.equals("/api/common/sales-opportunities") || OPPORTUNITY.matcher(path).matches())
                    target(mapper, "PART", text(body, "salesPartId"), scope);
            }
        }
    }
    /**
     * 엑셀 업로드 한 줄의 대상이 범위 안인지(명세 ⑧-2를 업로드 줄에도). 거부하지 않고 참·거짓으로 답해 줄 오류로 돌린다.
     * kind: PROJ(프로젝트)·PART(영업 파트)·DEV_PART(새 개발자의 영업 파트). 개발자는 화면처럼
     * 「담당 파트」 역할(전체 없음)만 자기 파트로 거른다(⑧-3). 없는 건은 양식 검사가 오류로 답하게 통과시킨다.
     * 급여(B.1)는 줄로 거르지 않고 범위 「전체」+민감 체크가 아니면 검사·반영 자체를 403으로 막는다(CommonUploadService).
     */
    public static boolean allows(AccountAccessMapper mapper, String kind, String id, UserScopePolicy.Scope scope,
                                 boolean developerPartOnly) {
        if (scope == null || scope.all()) return true;
        try {
            switch (kind) {
                case "DEV_PART" -> { return !developerPartOnly || scope.partIds().contains(id == null ? "" : id); }
                default -> target(mapper, kind, id, scope);
            }
            return true;
        } catch (AccountAccessFailure failure) {
            return false;
        }
    }
    private static Iterable<JsonNode> array(JsonNode body, String field) {
        JsonNode value = body == null ? null : body.get(field);
        return value != null && value.isArray() ? value : List.of();
    }
    private static String text(JsonNode body, String field) {
        JsonNode value = body == null ? null : body.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static final Pattern DEVELOPER = Pattern.compile("^/api/common/developers/([^/]+)(/.*)?$");
    private static final Set<String> DEVELOPER_NAMED = Set.of("areas", "excel", "use-status");

    /**
     * 개발자 풀(UI-02)은 「담당 파트」 역할(전체 없음)만 자기 파트 인력으로 거른다(명세 ⑧-3, 시안 devInScope).
     * 다른 파트 인력의 상세·이력·상태 주소는 거부한다. 부르는 쪽이 「전체 없음 + 담당 파트」일 때만 부른다.
     */
    public static void checkDeveloper(AccountAccessMapper mapper, String path, UserScopePolicy.Scope scope) {
        var match = DEVELOPER.matcher(path);
        if (!match.matches() || DEVELOPER_NAMED.contains(match.group(1))) return;
        String part = mapper.developerPart(match.group(1));
        // 없는 개발자는 화면 API가 「개발자를 찾을 수 없습니다.」로 답한다. 파트가 없는 인력('')은 범위 밖.
        if (part != null && !scope.partIds().contains(part)) throw denied();
    }

    private static AccountAccessFailure denied() { return new AccountAccessFailure(403, "scope", OUT_OF_SCOPE); }
}
