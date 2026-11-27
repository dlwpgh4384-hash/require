package com.company.common.notifications.controller;

import com.company.common.notifications.dto.NotificationContracts.*;
import com.company.common.notifications.service.NotificationService;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = "application/json")
public class CommonNotificationController {
    private final NotificationService service;

    public CommonNotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/api/common/notifications")
    public Response<Listing> getCommonNotifications(Principal principal) {
        return Response.of(service.getCommonNotifications(principal));
    }

    @PutMapping("/api/common/notifications/read-all")
    public ResponseEntity<Void> putCommonNotificationsReadAll(Principal principal) {
        service.putCommonNotificationsReadAll(principal);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/common/notifications/{notificationId}/read")
    public Response<Read> putCommonNotificationRead(Principal principal,
        @PathVariable("notificationId") long notificationId) {
        return service.putCommonNotificationRead(principal, notificationId);
    }
}
