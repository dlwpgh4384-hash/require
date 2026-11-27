package com.company.common.session.controller;

import com.company.common.session.dto.SessionContracts.Response;
import com.company.common.session.service.SessionPolicy;
import com.company.common.session.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommonSessionController {
    private final SessionService service;

    public CommonSessionController(SessionPolicy policy) { this.service = policy; }

    @GetMapping(value = "/api/common/session/remaining", produces = "application/json")
    public Response getCommonSessionRemaining(HttpServletRequest request) {
        return Response.of(service.getCommonSessionRemaining(request));
    }

    @PostMapping(value = "/api/common/session/extend", produces = "application/json")
    public Response postCommonSessionExtend(HttpServletRequest request) {
        return Response.of(service.postCommonSessionExtend(request));
    }
}
