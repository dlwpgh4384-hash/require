package com.company.common.commonops.service;

import com.company.common.commonops.mapper.AccountAccessMapper;
import com.company.common.support.auth.AccountSessions;
import com.company.common.support.auth.LoginCompletion;
import com.company.common.support.service.BusinessCalendar;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class AccountLoginCompletion implements LoginCompletion {
    private final AccountAccessMapper mapper;
    private final AccountSessions sessions;
    private final BusinessCalendar calendar;
    private final com.company.common.users.mapper.UserMapper users;
    public AccountLoginCompletion(AccountAccessMapper mapper, AccountSessions sessions, BusinessCalendar calendar,
        com.company.common.users.mapper.UserMapper users) {
        this.mapper = mapper;
        this.sessions = sessions;
        this.calendar = calendar;
        this.users = users;
    }
    public java.time.Instant now() { return calendar.now(); }
    public java.util.List<String> parts(String id) { return users.selectParts(id); }
    public int standard(String code) {
        Integer value = mapper.standard(code);
        if (value == null || value <= 0) throw new IllegalStateException("운영 기준값을 확인해 주세요.");
        return value;
    }
    @Override public Prepared prepare(String id, boolean required) {
        var row = mapper.account(id);
        if (row == null || !"USE".equals(row.status)) throw AccountAccessFailure.unauthorized();
        var permissions = mapper.permissions(id);
        String first = permissions.stream().filter(p -> "Y".equals(p.readYn) && "UI-16".equals(p.screenId))
            .map(p -> p.screenId).findFirst().orElseGet(() -> permissions.stream()
                .filter(p -> "Y".equals(p.readYn)).map(p -> p.screenId).findFirst().orElse("UI-34"));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("modifiedAt", row.modifiedAt);
        meta.put("passwordChangedAt", row.passwordChangedAt);
        meta.put("passwordChangeDays", standard("PWD_CHG_DAY"));
        meta.put("serverRespondedAt", calendar.now());
        int minutes = standard("SESSION_MIN");
        meta.put("sessionMinutes", minutes);
        meta.put("remainingSeconds", minutes * 60);
        meta.put("permissions", permissions);
        meta.put("sensitive", mapper.sensitive(id));
        var parts = java.util.Set.copyOf(mapper.scopeParts(id));
        var projects = java.util.Set.copyOf(mapper.scopeProjects(id));
        meta.put("scope", com.company.common.users.service.UserScopePolicy.union(mapper.scopeCodes(id).stream()
            .map(code -> new com.company.common.users.service.UserScopePolicy.Grant(code, parts, projects)).toList()));
        meta.put("passwordChangeRequired", required);
        return new Prepared(required ? "UI-40" : first, meta);
    }
    @Override public void establish(HttpServletRequest request, String id, boolean required) {
        var row = mapper.account(id);
        if (row == null || !"USE".equals(row.status)) throw AccountAccessFailure.unauthorized();
        var session = request.getSession();
        session.setMaxInactiveInterval(standard("SESSION_MIN") * 60);
        sessions.bind(session, id, row.hash, required, request.getRemoteAddr());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(id, null,
            mapper.roles(id).stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()));
        SecurityContextHolder.setContext(context);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}
