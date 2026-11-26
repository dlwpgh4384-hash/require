package com.company.common.session.service;

import com.company.common.session.dto.SessionContracts.Remaining;
import com.company.common.session.mapper.SessionMapper;
import com.company.common.support.auth.AccountSessions;
import com.company.common.support.service.BusinessCalendar;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SessionPolicy implements SessionService {
    private static final String STATE = SessionPolicy.class.getName() + ".state";
    public static final String RESPONSE = SessionPolicy.class.getName() + ".response";
    private static final String RESPONSE_SAMPLE = SessionPolicy.class.getName() + ".responseSample";
    private final SessionMapper mapper;
    private final BusinessCalendar calendar;

    private static final class State implements Serializable {
        private static final long serialVersionUID = 1L;
        final String userId;
        Instant deadline;
        int minutes;

        State(String userId, Instant started, int minutes) {
            this.userId = userId;
            this.minutes = minutes;
            this.deadline = started.plusSeconds(minutes * 60L);
        }
    }

    public SessionPolicy(SessionMapper mapper, BusinessCalendar calendar) {
        this.mapper = mapper;
        this.calendar = calendar;
    }

    @org.springframework.context.event.EventListener
    public void established(AccountSessions.Established event) {
        initialize(event.session(), event.userId());
    }

    public static boolean authenticated() {
        Authentication actor = SecurityContextHolder.getContext().getAuthentication();
        return actor != null && actor.isAuthenticated() && !(actor instanceof AnonymousAuthenticationToken);
    }

    private int minutes() {
        BigDecimal value = mapper.selectSessionMinutes(calendar.toDatabaseMonth(calendar.currentMonth()));
        try {
            int minutes = value == null ? 0 : value.intValueExact();
            if (minutes <= 0 || minutes > Integer.MAX_VALUE / 60) throw new ArithmeticException();
            return minutes;
        } catch (ArithmeticException invalid) {
            throw new SessionFailure(400, "세션 운영 기준값을 확인해 주세요.");
        }
    }

    /** Trusted login continuation only, after password/MFA and transaction commit. Never creates a session. */
    public Remaining initialize(HttpSession session, String userId) {
        if (session == null || userId == null || userId.isBlank() || userId.length() > 30)
            throw unauthorized(session);
        synchronized (session) {
            if (!"USE".equals(mapper.selectAccountStatus(userId))) throw unauthorized(session);
            Instant now = calendar.now();
            var state = new State(userId, now, minutes());
            session.setAttribute(STATE, state);
            session.setMaxInactiveInterval(state.minutes * 60);
            return remaining(state, now);
        }
    }

    /** Servlet security has already checked CSRF before MVC invokes this method. */
    public Remaining onRequest(HttpServletRequest request, boolean refresh) {
        HttpSession session = request.getSession(false);
        if (!authenticated() || session == null) throw unauthorized(session);
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        if (userId == null || userId.isBlank() || userId.length() > 30) throw unauthorized(session);
        synchronized (session) {
            Instant now = calendar.now();
            State state = (State) session.getAttribute(STATE);
            if (state == null) {
                // Existing authenticated sessions are bounded by creation, not servlet last-access time.
                state = new State(userId, Instant.ofEpochMilli(session.getCreationTime()), minutes());
            }
            if (!state.userId.equals(userId) || !now.isBefore(state.deadline)
                || !"USE".equals(mapper.selectAccountStatus(userId))) throw unauthorized(session);
            String path = request.getRequestURI();
            if ("Y".equals(mapper.selectTemporaryPassword(userId))
                && !("PUT".equals(request.getMethod()) && "/api/common/my-password".equals(path))
                && !("POST".equals(request.getMethod()) && "/api/common/logout".equals(path))) {
                throw new SessionFailure(403, "비밀번호를 변경한 뒤 이용해 주세요.");
            }
            if (refresh) {
                state.minutes = minutes();
                state.deadline = now.plusSeconds(state.minutes * 60L);
            }
            session.setAttribute(STATE, state);
            session.setMaxInactiveInterval(state.minutes * 60);
            Remaining result = remaining(state, now);
            request.setAttribute(RESPONSE, result);
            request.setAttribute(RESPONSE_SAMPLE, (java.util.function.Supplier<Remaining>) () -> {
                synchronized (session) {
                    if (!(session.getAttribute(STATE) instanceof State current)) return null;
                    return remaining(current, calendar.now());
                }
            });
            return result;
        }
    }

    @Override
    public Remaining getCommonSessionRemaining(HttpServletRequest request) {
        return response(request, false);
    }

    @Override
    public Remaining postCommonSessionExtend(HttpServletRequest request) {
        return response(request, true);
    }

    public Remaining response(HttpServletRequest request, boolean refresh) {
        var value = request.getAttribute(RESPONSE);
        return value instanceof Remaining result ? result : onRequest(request, refresh);
    }

    public static Remaining responded(HttpServletRequest request) {
        if (!(request.getAttribute(RESPONSE_SAMPLE) instanceof java.util.function.Supplier<?> sample)) return null;
        try {
            if (!(sample.get() instanceof Remaining result)) return null;
            request.setAttribute(RESPONSE, result);
            return result;
        } catch (IllegalStateException ended) {
            return null;
        }
    }

    private Remaining remaining(State state, Instant now) {
        long millis = Math.max(0, Duration.between(now, state.deadline).toMillis());
        return new Remaining((millis + 999) / 1000, state.minutes, now);
    }

    private SessionFailure unauthorized(HttpSession session) {
        if (session != null) {
            try { session.invalidate(); } catch (IllegalStateException alreadyInvalid) { /* Already ended. */ }
        }
        SecurityContextHolder.clearContext();
        return new SessionFailure(401, "다시 로그인해 주세요.");
    }
}
