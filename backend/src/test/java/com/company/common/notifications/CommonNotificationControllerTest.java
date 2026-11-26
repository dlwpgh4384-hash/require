package com.company.common.notifications;

import com.company.common.notifications.controller.CommonNotificationController;
import com.company.common.notifications.controller.NotificationExceptionHandler;
import com.company.common.notifications.mapper.NotificationMapper;
import com.company.common.notifications.service.NotificationService;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.service.BusinessCalendar;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.company.common.notifications.NotificationTestData.NOW;
import static com.company.common.support.controller.OpenApiResponseContract.response;

@WebMvcTest(CommonNotificationController.class)
@ContextConfiguration(classes = {CommonNotificationController.class, NotificationService.class,
    NotificationExceptionHandler.class, ApiExceptionHandler.class, NotificationSecurityConfiguration.class})
class CommonNotificationControllerTest {
    @Autowired MockMvc mvc;
    @MockBean NotificationMapper mapper;
    @MockBean BusinessCalendar calendar;
    NotificationTestData data;
    MockHttpSession session;

    @BeforeEach void setup() throws Exception {
        String contract = new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8);
        assertThat(contract).contains("operationId: getCommonNotifications", "operationId: putCommonNotificationsReadAll",
            "operationId: putCommonNotificationRead");
        when(calendar.now()).thenReturn(NOW);
        data = new NotificationTestData(mapper);
        session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
            new UsernamePasswordAuthenticationToken("reader", null, List.of())));
    }

    private MockHttpServletRequestBuilder write(String path) {
        return put(path).session(session).cookie(new Cookie("XSRF-TOKEN", "notification-test-csrf"))
            .header("X-XSRF-TOKEN", "notification-test-csrf");
    }
    private void list() throws Exception {
        mvc.perform(get("/api/common/notifications").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap());
    }

    @Test void listIsOwnerOnlyNewestFirstAndZeroRoleAccessible() throws Exception {
        mvc.perform(get("/api/common/notifications").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].notificationId").value(2))
            .andExpect(jsonPath("$.data.items[1].notificationId").value(1))
            .andExpect(jsonPath("$.data.unreadCount").value(2));
    }
    @Test void anonymousListIs401() throws Exception {
        mvc.perform(get("/api/common/notifications")).andExpect(status().isUnauthorized());
        verify(mapper, never()).selectNotifications(any());
    }
    @Test void cutoffIncludesExactThirtyDayBoundary() throws Exception {
        data.add(4, "reader", NOW.minusSeconds(30L * 86400));
        data.add(5, "reader", NOW.minusSeconds(30L * 86400).minusNanos(1));
        mvc.perform(get("/api/common/notifications").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(3));
    }
    @Test void revokedContentIsMaskedButUnreadCountRemains() throws Exception {
        data.access.get(2L).readAllowed = false;
        mvc.perform(get("/api/common/notifications").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].content").value(NotificationService.HIDDEN))
            .andExpect(jsonPath("$.data.items[0].targetKey").doesNotExist())
            .andExpect(jsonPath("$.data.unreadCount").value(2));
    }
    @Test void outOfScopeContentIsMasked() throws Exception {
        data.access.get(2L).inScope = false;
        mvc.perform(get("/api/common/notifications").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].content").value(NotificationService.HIDDEN));
    }
    @Test void readAllHappyRereadsBothRowsAndZeroUnread() throws Exception {
        mvc.perform(write("/api/common/notifications/read-all")).andExpect(status().isNoContent());
        mvc.perform(get("/api/common/notifications").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(0))
            .andExpect(jsonPath("$.data.items[0].readYn").value("Y"))
            .andExpect(jsonPath("$.data.items[1].readYn").value("Y"));
    }
    @Test void anonymousReadAllDoesNotWrite() throws Exception {
        mvc.perform(put("/api/common/notifications/read-all")).andExpect(status().isUnauthorized());
        assertThat(data.rows.values()).allSatisfy(row -> assertThat(row.readYn).isEqualTo("N"));
        verify(mapper, never()).readAll(any());
    }
    @Test void missingCsrfReadAllIs403AndDoesNotWrite() throws Exception {
        mvc.perform(put("/api/common/notifications/read-all").session(session))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
        verify(mapper, never()).readAll(any());
        assertThat(data.rows.get(1L).readYn).isEqualTo("N");
    }
    @Test void mismatchedCsrfDoesNotWrite() throws Exception {
        mvc.perform(put("/api/common/notifications/read-all").session(session)
            .cookie(new Cookie("XSRF-TOKEN", "notification-test-csrf")).header("X-XSRF-TOKEN", "wrong"))
            .andExpect(status().isForbidden());
        verify(mapper, never()).readAll(any());
    }
    @Test
    @DisplayName("business condition: 본인 미읽음만 변경·타인 READ_YN·READ_DTTM 보존")
    void readAllNeverChangesOtherOwner() throws Exception {
        mvc.perform(put("/api/common/notifications/read-all").session(session)
            .cookie(new Cookie("XSRF-TOKEN", "notification-test-csrf"))
            .header("X-XSRF-TOKEN", "notification-test-csrf"))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(data.rows.get(3L).readYn).isEqualTo("N");
        assertThat(data.rows.get(3L).readAt).isNull();
        assertThat(data.rows.get(1L).readYn).isEqualTo("Y");
        verify(mapper).readAll(argThat(window -> window.actor().equals("reader")));
    }
    @Test
    @DisplayName("happy side-effect condition: 모두 읽음은 본인 READ_DTTM 설정·본문 없음·Location 이동 없음")
    void readAllSetsReadTimestampWithoutRedirect() throws Exception {
        mvc.perform(put("/api/common/notifications/read-all").session(session)
            .cookie(new Cookie("XSRF-TOKEN", "notification-test-csrf"))
            .header("X-XSRF-TOKEN", "notification-test-csrf")).andExpect(status().isNoContent())
            .andExpect(response("PUT", "/api/common/notifications/read-all"))
            .andExpect(content().string(""))
            .andExpect(header().doesNotExist("Location"));
        assertThat(data.rows.get(1L).readAt).isEqualTo(NOW);
        assertThat(data.rows.get(2L).readAt).isEqualTo(NOW);
    }
    @Test
    @DisplayName("happy side-effect condition: READ_YN=Y·READ_DTTM 저장 후 미읽음수 감소·자동상세 없음")
    void readOwnNotificationReturnsCommittedTimestamp() throws Exception {
        mvc.perform(put("/api/common/notifications/1/read").session(session)
            .cookie(new Cookie("XSRF-TOKEN", "notification-test-csrf"))
            .header("X-XSRF-TOKEN", "notification-test-csrf"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.readYn").value("Y"))
            .andExpect(response("PUT", "/api/common/notifications/{notificationId}/read"))
            .andExpect(jsonPath("$.data.readAt").value(NOW.toString()))
            .andExpect(jsonPath("$.meta.navigation.targetKey").doesNotExist());
        mvc.perform(get("/api/common/notifications").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(1))
            .andExpect(jsonPath("$.data.items[1].readYn").value("Y"));
        assertThat(data.rows.get(1L).readYn).isEqualTo("Y");
        assertThat(data.rows.get(1L).readAt).isEqualTo(NOW);
        verify(mapper).read(argThat(selection -> selection.actor().equals("reader") && selection.notificationId() == 1));
    }
    @Test void anotherOwnerIdIsForbiddenAndUnchanged() throws Exception {
        mvc.perform(write("/api/common/notifications/3/read"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
        assertThat(data.rows.get(3L).readYn).isEqualTo("N");
        verify(mapper, never()).read(any());
    }
    @Test void nonIntegerIdIsFieldErrorWithoutWrite() throws Exception {
        mvc.perform(write("/api/common/notifications/not-an-integer/read"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("notificationId"));
        verify(mapper, never()).read(any());
    }
    @Test void absentNotificationIs400WithoutWrite() throws Exception {
        mvc.perform(write("/api/common/notifications/999/read"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("notificationId"));
        verify(mapper, never()).read(any());
    }
    @Test void anonymousSingleReadDoesNotWrite() throws Exception {
        mvc.perform(put("/api/common/notifications/1/read")).andExpect(status().isUnauthorized());
        verify(mapper, never()).read(any());
    }
    @Test void missingCsrfSingleReadDoesNotWrite() throws Exception {
        mvc.perform(put("/api/common/notifications/1/read").session(session)).andExpect(status().isForbidden());
        assertThat(data.rows.get(1L).readYn).isEqualTo("N");
        verify(mapper, never()).read(any());
    }
    @Test void inactiveOrRestrictedAccountDoesNotReadOrWrite() throws Exception {
        when(mapper.activeAccount("reader")).thenReturn(false);
        mvc.perform(write("/api/common/notifications/1/read")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/common/notifications").session(session)).andExpect(status().isUnauthorized());
        verify(mapper, never()).read(any());
        verify(mapper, never()).selectNotifications(any());
    }
    @Test
    @DisplayName("business condition: 권한 회수 알림은 읽음 처리·DENIED·화면 이동 없음·원문 가림")
    void revokedPermissionStillMarksReadAndReturnsDeniedWithoutOriginalText() throws Exception {
        data.access.get(1L).readAllowed = false;
        mvc.perform(put("/api/common/notifications/1/read").session(session)
            .cookie(new Cookie("XSRF-TOKEN", "notification-test-csrf"))
            .header("X-XSRF-TOKEN", "notification-test-csrf"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.navigation.status").value("DENIED"))
            .andExpect(jsonPath("$.meta.navigation.screenId").doesNotExist());
        assertThat(data.rows.get(1L).readYn).isEqualTo("Y");
        mvc.perform(get("/api/common/notifications").session(session))
            .andExpect(jsonPath("$.data.items[1].content").value(NotificationService.HIDDEN));
    }
    @Test void readDecreasesCountAndReturnsOnlyScreenNotDetail() throws Exception {
        mvc.perform(write("/api/common/notifications/1/read"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.navigation.screenId").value("UI-13"))
            .andExpect(jsonPath("$.meta.navigation.targetKey").doesNotExist());
        mvc.perform(get("/api/common/notifications").session(session))
            .andExpect(jsonPath("$.data.unreadCount").value(1));
    }
    @Test void outOfScopeReadReturnsScreenNavigationStatus() throws Exception {
        data.access.get(1L).inScope = false;
        mvc.perform(write("/api/common/notifications/1/read"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.navigation.status").value("OUT_OF_SCOPE"));
    }
    @Test void missingTargetReadReturnsMissingStatus() throws Exception {
        data.access.get(1L).targetExists = false;
        mvc.perform(write("/api/common/notifications/1/read"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.navigation.status").value("MISSING"));
    }
    @Test void pcOnlyMetadataUsesCurrentMenu() throws Exception {
        data.access.get(1L).pcOnly = true;
        mvc.perform(write("/api/common/notifications/1/read"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.meta.navigation.pcOnly").value(true));
    }
    @Test void readingTwicePreservesFirstReadTime() throws Exception {
        mvc.perform(write("/api/common/notifications/1/read")).andExpect(status().isOk());
        var first = data.rows.get(1L).readAt;
        mvc.perform(write("/api/common/notifications/1/read")).andExpect(status().isOk());
        assertThat(data.rows.get(1L).readAt).isEqualTo(first);
    }
}
