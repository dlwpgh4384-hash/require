package com.company.common.support.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/** Shared login continuation. Implemented by the session owner, not by an MFA controller. */
public interface LoginCompletion {
    record Prepared(String nextScreenId, Map<String, Object> meta) {}

    /** Resolve current permissions and password/session metadata without establishing a session. */
    Prepared prepare(String userId, boolean passwordChangeRequired);

    /** Called after the login transaction commits and the servlet session id has rotated. */
    void establish(HttpServletRequest request, String userId, boolean passwordChangeRequired);
}
