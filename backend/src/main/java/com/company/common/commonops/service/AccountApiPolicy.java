package com.company.common.commonops.service;

import com.company.common.commonops.mapper.AccountAccessMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/** Explicit HTTP-to-menu action inventory. No HTTP verb implies a permission. */
@Component
public class AccountApiPolicy {
    private record Rule(String method, String path, String menu, String action) {}
    private final List<Rule> rules = new ArrayList<>();
    private final AntPathMatcher matcher = new AntPathMatcher();
    public AccountApiPolicy() {
        add("M027", "INQ_YN", "GET", "users users/{userId} users/{userId}/access-logs users/change-impact users/{userId}/change-history");
        add("M027", "REG_YN", "PUT", "users");
        add("M027", "UPD_YN", "PUT", "users/{userId} users/{userId}/password-reset users/{userId}/unlock users/stop users/{userId}/resume");
        add("M027", "DNLD_YN", "GET", "users/excel");
        add("M028", "INQ_YN", "GET", "roles roles/{roleId} roles/{roleId}/change-impact roles/{roleId}/change-history");
        add("M028", "REG_YN", "POST", "roles"); add("M028", "UPD_YN", "PUT", "roles/{roleId}"); add("M028", "UPD_YN", "POST", "roles/{roleId}/stop");
        add("M029", "INQ_YN", "GET", "menu-settings"); add("M029", "UPD_YN", "PUT", "menu-settings");
        add("M030", "INQ_YN", "GET", "code-groups operating-standards code-maps code-change-history");
        add("M030", "REG_YN", "POST", "code-groups code-groups/{groupId}/codes code-maps");
        add("M030", "UPD_YN", "PUT", "code-groups/{groupId}/codes operating-standards");
        add("M030", "DEL_YN", "POST", "code-groups/{groupId}/codes/{code}/delete code-maps/delete");
        add("M002", "INQ_YN", "GET", "clients/{clientId} clients/{clientId}/assignments clients/{clientId}/change-history clients/{clientId}/payment-account parts/{partId}/standards parts/{partId}/change-history");
        add("M002", "REG_YN", "POST", "clients parts");
        add("M002", "UPD_YN", "PUT", "clients/{clientId} clients/use-status parts/{partId}/standards parts/{partId} parts/{partId}/stop parts/{partId}/resume");
        add("M002", "DNLD_YN", "GET", "clients/excel parts/excel");
        add("M003", "INQ_YN", "GET", "developers/{developerId} developers/{developerId}/assignments developers/{developerId}/statuses developers/{developerId}/change-history");
        add("M003", "REG_YN", "POST", "developers developers/{developerId}/statuses");
        add("M003", "UPD_YN", "PUT", "developers/{developerId} developers/{developerId}/statuses/{statusId}/end-date developers/use-status");
        add("M003", "UPD_YN", "POST", "developers/{developerId}/statuses/{statusId}/return-cancel developers/{developerId}/statuses/{statusId}/retirement-cancel");
        add("M003", "DNLD_YN", "GET", "developers/excel");
        add("M004", "INQ_YN", "GET", "projects/{projectId} projects/{projectId}/change-history projects/{projectId}/sales-parts projects/{projectId}/distribution projects/{projectId}/manpower-plan projects/{projectId}/direct-expenses projects/{projectId}/lose-impact projects/budget-rule projects/{projectId}/weekly-memos projects/{projectId}/steps");
        add("M004", "REG_YN", "POST", "projects projects/{projectId}/direct-expenses projects/{projectId}/weekly-memos");
        add("M004", "UPD_YN", "PUT", "projects/{projectId} projects/{projectId}/budget projects/{projectId}/sales-part projects/{projectId}/manpower-plan projects/{projectId}/weekly-memos/{memoId} projects/{projectId}/steps");
        add("M004", "UPD_YN", "POST", "projects/{projectId}/start projects/{projectId}/lose projects/{projectId}/steps/ensure projects/{projectId}/steps/{stepId}/complete");
        add("M004", "DEL_YN", "POST", "projects/{projectId}/direct-expenses/{expenseId}/delete");
        add("M004", "UPLD_YN", "GET", "projects/{projectId}/distribution/template"); add("M004", "DNLD_YN", "GET", "projects/excel");
        add("M005", "INQ_YN", "GET", "project-board project-issues"); add("M005", "DNLD_YN", "GET", "project-board/excel project-issues/excel");
        add("M005", "REG_YN", "POST", "project-issues"); add("M005", "UPD_YN", "PUT", "project-issues/{issueId}");
        add("M006", "INQ_YN", "GET", "assignments assignments/{assignmentId} assignments/candidates assignments/contract-developers assignments/{assignmentId}/history");
        add("M006", "REG_YN", "POST", "assignments"); add("M006", "DEL_YN", "POST", "assignments/candidates/{candidateId}/remove");
        add("M006", "UPD_YN", "POST", "assignments/{assignmentId}/changes assignments/{assignmentId}/history/{historyId}/corrections assignments/cancel");
        add("M006", "UPD_YN", "PUT", "assignments/extension-possibility"); add("M006", "DNLD_YN", "GET", "assignments/excel");
        add("M007", "INQ_YN", "GET", "withdrawals withdrawals/{assignmentId}/recommendations withdrawals/{assignmentId}/tentative-assignment withdrawals/tentative-assignments");
        add("M007", "UPD_YN", "PUT", "withdrawals/{assignmentId}/extension-possibility withdrawals/{assignmentId}/tentative-assignment"); add("M007", "DNLD_YN", "GET", "withdrawals/excel");
        add("M008", "INQ_YN", "GET", "manpower-comparisons"); add("M008", "DNLD_YN", "GET", "manpower-comparisons/excel");
        add("M009", "INQ_YN", "GET", "manpower-board"); add("M009", "DNLD_YN", "GET", "manpower-board/excel");
        add("M010", "INQ_YN", "GET", "manpower-forecasts"); add("M010", "DNLD_YN", "GET", "manpower-forecasts/excel");
        add("M011", "INQ_YN", "GET", "sales-pipeline sales-opportunities/{opportunityId} sales-pipeline/closed sales-pipeline/forecast");
        add("M011", "INQ_YN", "POST", "sales-opportunities/tentative-assignment-check sales-pipeline/quote sales-opportunities/{opportunityId}/tentative-assignment-check");
        add("M011", "UPD_YN", "PUT", "sales-opportunities/{opportunityId} sales-opportunities/{opportunityId}/stage sales-opportunities/{opportunityId}/tentative-assignment");
        add("M011", "REG_YN", "POST", "sales-opportunities"); add("M011", "CNFM_YN", "PUT", "sales-opportunities/{opportunityId}/approve sales-opportunities/{opportunityId}/reject");
        add("M011", "UPD_YN", "POST", "sales-opportunities/{opportunityId}/win sales-opportunities/{opportunityId}/lose sales-opportunities/{opportunityId}/stop sales-opportunities/{opportunityId}/resume");
        add("M011", "DNLD_YN", "GET", "sales-pipeline/forecast/excel");
        add("M012", "INQ_YN", "GET", "candidate-summary candidate-shortages candidates candidates/{candidateId} candidates/{candidateId}/contacts candidates/{candidateId}/matches");
        add("M012", "REG_YN", "POST", "candidates candidates/{candidateId}/contacts candidate-resume-extractions");
        add("M012", "UPD_YN", "PUT", "candidates/{candidateId}"); add("M012", "DNLD_YN", "GET", "candidates/excel");
        add("M013", "INQ_YN", "GET", "bills bills/unpaid-count bills/{billId} bill-checks bill-dunning");
        add("M013", "REG_YN", "POST", "bills receipts bill-checks/missing bill-checks/additional bills/{billId}/next-month");
        add("M013", "UPD_YN", "PUT", "bills/{billId}"); add("M013", "UPD_YN", "POST", "bills/{billId}/cancel receipts/{receiptId}/cancel bill-checks/correction bills/{billId}/dunning-resend");
        add("M013", "DNLD_YN", "GET", "bills/excel");
        add("M014", "INQ_YN", "GET", "personal-cost-profits"); add("M014", "DNLD_YN", "GET", "personal-cost-profits/excel");
        add("M015", "INQ_YN", "GET", "project-cost-analyses project-cost-analyses/{projectId}/monthly-costs project-cost-analyses/{projectId}/workforce project-cost-analyses/{projectId}/simulation project-cost-analyses/{projectId}/causes"); add("M015", "DNLD_YN", "GET", "project-cost-analyses/excel");
        add("M016", "INQ_YN", "GET", "part-settlements part-settlements/{partId}/{yearMonth} part-settlements/{partId}/{yearMonth}/trends part-settlement-close-checks");
        add("M016", "UPD_YN", "PUT", "part-settlements/{partId}/{yearMonth}/card");
        add("M016", "CNFM_YN", "PUT", "part-settlements/{partId}/{yearMonth}/confirm part-settlements/{partId}/{yearMonth}/cancel part-settlements/close-confirm"); add("M016", "DNLD_YN", "GET", "part-settlements/excel");
        add("M017", "INQ_YN", "GET", "rate-renegotiations"); add("M017", "DNLD_YN", "GET", "rate-renegotiations/excel");
        add("M018", "INQ_YN", "GET", "finance-items/{yearMonth} finance-items/{yearMonth}/previous-items"); add("M018", "UPD_YN", "PUT", "finance-items/{yearMonth}");
        add("M018", "UPD_YN", "POST", "finance-items/tax-years/{taxYear} finance-items/tax-payments/{paymentId}/confirm");
        add("M019", "INQ_YN", "GET", "profit-structures"); add("M019", "DNLD_YN", "GET", "profit-structures/excel");
        add("M020", "INQ_YN", "GET", "operating-profit-statements"); add("M020", "DNLD_YN", "GET", "operating-profit-statements/excel");
        add("M021", "INQ_YN", "GET", "net-profit-statements"); add("M021", "DNLD_YN", "GET", "net-profit-statements/excel");
        add("M022", "INQ_YN", "GET", "business-plan-comparisons");
        add("M023", "INQ_YN", "GET", "cash-flow-statements"); add("M023", "DNLD_YN", "GET", "cash-flow-statements/excel");
        add("M024", "INQ_YN", "GET", "month-closes/{yearMonth} month-closes/{yearMonth}/reopen-impact month-close-history month-close-badge");
        add("M024", "CNFM_YN", "PUT", "month-closes/{yearMonth}/confirm month-closes/{yearMonth}/reopen");
        add("M025", "INQ_YN", "GET", "ai-analysis/questions ai-analysis/requests/{requestId}"); add("M025", "REG_YN", "POST", "ai-analysis/questions"); add("M025", "DNLD_YN", "GET", "ai-analysis/questions/{questionId}/excel");
        add("M026", "REG_YN", "POST", "ai-recommendations"); add("M026", "UPD_YN", "POST", "ai-recommendations/{recommendationId}/assignment ai-recommendations/{recommendationId}/exclusions"); add("M026", "UPD_YN", "PUT", "ai-recommendations/{recommendationId}/tentative-assignment");
        add("M001", "INQ_YN", "GET", "dashboard"); add("M001", "DNLD_YN", "GET", "dashboard/excel");
        add("", "SELF", "GET", "menus user-preferences/{prefTypeCd}/{prefKey} user-options menu-options code-groups/{groupId}/codes clients parts parts/options developers developers/areas projects bill-options ai-recommendation-options my-info session/remaining notifications");
        add("", "SELF", "PUT", "user-preferences/{prefTypeCd}/{prefKey} my-password notifications/{notificationId}/read notifications/read-all");
        add("", "SELF", "POST", "menus logout session/extend");
        add("", "FORM", "GET", "uploads/templates/{formTypeCode} uploads/{uploadId} uploads/{uploadId}/errors");
        add("", "FORM", "POST", "uploads uploads/{uploadId}/apply");
    }
    private void add(String menu, String action, String method, String paths) {
        for (String path : paths.split(" ")) rules.add(new Rule(method, "/api/common/" + path, menu, action));
    }
    public void check(String method, String path, String form, List<AccountAccessMapper.Permission> permissions, List<String> roles) {
        Rule rule = rules.stream().filter(item -> item.method.equals(method) && matcher.match(item.path, path))
            .min(Comparator.comparing(item -> item.path, matcher.getPatternComparator(path))).orElseThrow(AccountApiPolicy::denied);
        if (path.endsWith("/access-logs") && !roles.contains("ADMIN")) throw denied();
        if (path.matches("/api/common/month-closes/[^/]+/reopen") && !roles.contains("ADMIN")) throw denied();
        if ((path.equals("/api/common/parts") && method.equals("POST") || path.matches("/api/common/parts/[^/]+/standards") && method.equals("PUT"))
            && roles.stream().noneMatch(role -> role.equals("ADMIN") || role.equals("FIN"))) throw denied();
        if (rule.action.equals("SELF")) return;
        String menu = rule.menu;
        String action = rule.action;
        if (action.equals("FORM")) {
            if (form == null) throw denied();
            menu = switch (form) { case "B7" -> "M002"; case "B8", "B1" -> "M003"; case "B9", "B6" -> "M004";
                case "B5" -> "M006"; case "B2" -> "M018"; case "B10" -> "M022"; case "B11" -> "M020"; default -> throw denied(); };
            if (form.equals("B11") && !roles.contains("ADMIN")) throw denied();
            action = "UPLD_YN";
        }
        String requiredMenu = menu; String requiredAction = action;
        if (permissions.stream().noneMatch(permission -> permission.menuId.equals(requiredMenu) && "Y".equals(permission.readYn)
            && "Y".equals(flag(permission, requiredAction)))) throw denied();
    }
    private String flag(AccountAccessMapper.Permission p, String action) {
        return switch (action) { case "INQ_YN" -> p.readYn; case "REG_YN" -> p.createYn; case "UPD_YN" -> p.updateYn;
            case "DEL_YN" -> p.deleteYn; case "UPLD_YN" -> p.uploadYn; case "DNLD_YN" -> p.downloadYn; case "CNFM_YN" -> p.cnfmYn; default -> "N"; };
    }
    private static AccountAccessFailure denied() { return new AccountAccessFailure(403, "permission", "권한이 없습니다."); }
}
