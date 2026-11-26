package com.company.common.support.auth;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Servlet sessions only; account and permission data always come from the database. */
@Component
public class AccountSessions {
    public static final String ATTRIBUTE = AccountSessions.class.getName();
    public record Ended(String userId, String ip, String reason) {}
    public record Established(HttpSession session, String userId) {}
    public final class Binding implements HttpSessionBindingListener {
        public final String userId;
        public final String ip;
        public volatile String credentialHash;
        public volatile boolean passwordChangeRequired;
        private volatile String reason = "자동 로그아웃";
        private final HttpSession session;
        private Binding(HttpSession session, String userId, String hash, boolean required, String ip) {
            this.session = session;
            this.userId = userId;
            this.credentialHash = hash;
            this.passwordChangeRequired = required;
            this.ip = ip;
        }
        @Override public void valueUnbound(HttpSessionBindingEvent event) {
            if (sessions.remove(this) != null) events.publishEvent(new Ended(userId, ip, reason));
        }
    }
    private final Map<Binding, Boolean> sessions = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;
    public AccountSessions(ApplicationEventPublisher events) { this.events = events; }
    public Binding bind(HttpSession session, String id, String hash, boolean required, String ip) {
        Binding old = current(session);
        if (old != null) session.removeAttribute(ATTRIBUTE);
        var binding = new Binding(session, id, hash, required, ip);
        sessions.put(binding, true);
        session.setAttribute(ATTRIBUTE, binding);
        events.publishEvent(new Established(session, id));
        return binding;
    }
    public Binding current(HttpSession session) {
        return session != null && session.getAttribute(ATTRIBUTE) instanceof Binding value ? value : null;
    }
    public void end(HttpSession session, String reason) {
        Binding binding = current(session);
        if (binding != null) binding.reason = reason;
        try { session.invalidate(); } catch (IllegalStateException ignored) { /* already ended */ }
    }
    public void endOthers(String userId, HttpSession keep, String reason) {
        for (Binding binding : sessions.keySet()) {
            if (binding.userId.equals(userId) && binding.session != keep) end(binding.session, reason);
        }
    }
}
