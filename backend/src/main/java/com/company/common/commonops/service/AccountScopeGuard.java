package com.company.common.commonops.service;

import com.company.common.commonops.mapper.AccountAccessMapper;
import com.company.common.users.service.UserScopePolicy;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 범위 밖 ID를 주소로 직접 부르면 서버가 거부한다(명세 ⑧-2·⑦-2). 상세·변경·이력 주소의 프로젝트(또는 그 청구·배정·이슈의
 * 프로젝트)가 「내 프로젝트」이거나 현재 영업 파트가 「내 파트」일 때만 통과. 목록은 각 매퍼가 WHERE로 거른다.
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
        new Route(Pattern.compile("^/api/common/part-settlements/([^/]+)/.*$"), "PART"));

    private AccountScopeGuard() {}

    public static void check(AccountAccessMapper mapper, String path, UserScopePolicy.Scope scope) {
        if (scope == null || scope.all()) return;
        for (Route route : ROUTES) {
            var match = route.path().matcher(path);
            if (!match.matches()) continue;
            String id = match.group(1);
            if (NAMED.contains(id)) return;
            if (route.kind().equals("PART") || route.kind().equals("OPP")) {
                // 영업 기회는 그 기회의 영업 파트로 본다(파트장은 자기 파트만, PM은 0건 — 명세 ⑧-3).
                String part = route.kind().equals("PART") ? id : mapper.opportunityPart(id);
                if (part != null && !scope.partIds().contains(part)) throw denied();
                return;
            }
            String project = mapper.projectOf(route.kind(), id);
            // 없는 건은 화면 API가 404·검사 오류로 답한다.
            if (project == null || scope.projectIds().contains(project)) return;
            if (scope.partIds().isEmpty() || !mapper.projectInParts(project, scope.partIds())) throw denied();
            return;
        }
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
