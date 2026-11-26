package com.company.common.session.service;

import com.company.common.session.dto.SessionContracts.Remaining;
import jakarta.servlet.http.HttpServletRequest;

/** Application boundary implemented by the database-backed session policy. */
public interface SessionService {
    Remaining getCommonSessionRemaining(HttpServletRequest request);
    Remaining postCommonSessionExtend(HttpServletRequest request);
}
