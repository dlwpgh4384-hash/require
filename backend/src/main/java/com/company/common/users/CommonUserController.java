package com.company.common.users;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import static com.company.common.users.UserContracts.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonUserController {
    private final CommonUserService service;
    public CommonUserController(CommonUserService service) { this.service = service; }

    @GetMapping("/api/common/users")
    public Response<Page<ListItem>> getCommonUsers(
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(required = false) String roleCd, @RequestParam(required = false) String userStatCd,
        @RequestParam(required = false) String keyword) {
        return Response.of(service.getCommonUsers(UserRequests.filter(page, pageSize, roleCd, userStatCd, keyword)));
    }
    @GetMapping("/api/common/users/change-impact")
    public Response<Impact> getCommonUserChangeImpact(@RequestParam List<String> userIds,
        @RequestParam String action, @RequestParam(required = false) List<String> roleCds) {
        return Response.of(service.getCommonUserChangeImpact(userIds, action, roleCds));
    }
    @GetMapping("/api/common/users/{userId}")
    public Response<Account> getCommonUser(@PathVariable String userId) {
        return Response.of(service.getCommonUser(userId));
    }
    @GetMapping("/api/common/users/{userId}/access-logs")
    public Response<Page<AccessLog>> getCommonUserAccessLogs(@PathVariable String userId,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return Response.of(service.getCommonUserAccessLogs(userId, UserRequests.filter(page, pageSize, null, null, null)));
    }
    @PutMapping(value = "/api/common/users", consumes = "application/json")
    public Response<Save> putCommonUsers(@RequestBody JsonNode body) {
        return Response.of(service.putCommonUsers(UserRequests.create(body)));
    }
    @PutMapping(value = "/api/common/users/stop", consumes = "application/json")
    public Response<StopResult> putCommonUserStop(@RequestBody JsonNode body) {
        return Response.of(service.putCommonUserStop(UserRequests.stop(body)));
    }
    @PutMapping(value = "/api/common/users/{userId}", consumes = "application/json")
    public Response<Save> putCommonUser(@PathVariable String userId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonUser(userId, UserRequests.update(body)));
    }
    @PutMapping(value = "/api/common/users/{userId}/password-reset", consumes = "application/json")
    public Response<PasswordResult> putCommonUserPasswordReset(@PathVariable String userId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonUserPasswordReset(userId, UserRequests.version(body)));
    }
    @PutMapping(value = "/api/common/users/{userId}/resume", consumes = "application/json")
    public Response<Account> putCommonUserResume(@PathVariable String userId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonUserResume(userId, UserRequests.version(body)));
    }
    @PutMapping(value = "/api/common/users/{userId}/unlock", consumes = "application/json")
    public Response<UnlockResult> putCommonUserUnlock(@PathVariable String userId, @RequestBody JsonNode body) {
        return Response.of(service.putCommonUserUnlock(userId, UserRequests.version(body)));
    }
}
