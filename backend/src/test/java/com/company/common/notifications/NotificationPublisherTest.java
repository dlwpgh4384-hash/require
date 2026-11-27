package com.company.common.notifications;

import com.company.common.notifications.mapper.NotificationMapper;
import com.company.common.notifications.mapper.NotificationRows.*;
import com.company.common.notifications.service.NotificationPublisher;
import com.company.common.support.service.BusinessCalendar;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationPublisherTest {
    NotificationMapper mapper;
    NotificationPublisher publisher;
    Access access;
    @BeforeEach void setup() {
        mapper = mock(NotificationMapper.class);
        publisher = new NotificationPublisher(mapper,
            new BusinessCalendar(Clock.fixed(NotificationTestData.NOW, ZoneOffset.UTC)));
        when(mapper.eligibleAccount("recipient")).thenReturn(true);
        access = new Access(); access.readAllowed = true; access.inScope = true; access.targetExists = true;
        when(mapper.selectAccess(eq("recipient"), any())).thenReturn(access);
        when(mapper.insert(any())).thenReturn(1);
    }
    @Test void stoppedRecipientNeverReceives() {
        when(mapper.eligibleAccount("recipient")).thenReturn(false);
        assertThat(publisher.cashShortage("recipient", "202611", new java.math.BigDecimal("1000000000"))).isFalse();
        verify(mapper, never()).insert(any());
    }
    @Test void revokedReadNeverReceives() {
        access.readAllowed = false;
        assertThat(publisher.cashShortage("recipient", "202611", new java.math.BigDecimal("1000000000"))).isFalse();
        verify(mapper, never()).insert(any());
    }
    @Test void outOfScopeNeverReceives() {
        access.inScope = false;
        assertThat(publisher.quoteRejected("recipient", "opportunity")).isFalse();
        verify(mapper, never()).insert(any());
    }
    @Test void cashShortageUsesCurrentPermissionNotRoleNameAndDailyLock() {
        assertThat(publisher.cashShortage("recipient", "202611", new java.math.BigDecimal("1000000000"))).isTrue();
        var order = inOrder(mapper);
        order.verify(mapper).eligibleAccount("recipient");
        order.verify(mapper).selectAccess(eq("recipient"), any());
        order.verify(mapper).lockDelivery("recipient:CASH_SHORT:202611");
        order.verify(mapper).deliveredToday(any());
        order.verify(mapper).insert(any());
    }
    @Test void sameMonthSameDayIsNotCreatedTwice() {
        when(mapper.deliveredToday(any())).thenReturn(true);
        assertThat(publisher.cashShortage("recipient", "202611", new java.math.BigDecimal("1000000000"))).isFalse();
        verify(mapper, never()).insert(any());
    }
    static final java.time.LocalDate END = java.time.LocalDate.of(2026, 12, 27);
    static final java.util.List<NotificationPublisher.WithdrawalTarget> DUE = java.util.List.of(
        new NotificationPublisher.WithdrawalTarget("a1", "dev1"), new NotificationPublisher.WithdrawalTarget("a2", "dev1"),
        new NotificationPublisher.WithdrawalTarget("a3", "dev2"));
    @Test void withdrawalCountsInScopeDevelopersWithMockupText() {
        var outside = new Access(); outside.readAllowed = true; outside.inScope = false; outside.targetExists = true;
        when(mapper.selectAccess(eq("recipient"), argThat(n -> n != null && "a3".equals(n.targetKey)))).thenReturn(outside);
        assertThat(publisher.withdrawalSummary("recipient", END, 30, DUE)).isTrue();
        var capture = ArgumentCaptor.forClass(Delivery.class);
        verify(mapper).insert(capture.capture());
        assertThat(capture.getValue().content()).isEqualTo("철수 30일 전: 1명");
        assertThat(capture.getValue().targetKey()).isEqualTo("2026-12-27");
        assertThat(capture.getValue().screenId()).isEqualTo("UI-05");
        verify(mapper).lockDelivery("recipient:WTHDR:2026-12-27");
    }
    @Test void withdrawalWithNothingInScopeCreatesNothing() {
        access.inScope = false;
        assertThat(publisher.withdrawalSummary("recipient", END, 30, DUE)).isFalse();
        assertThat(publisher.withdrawalSummary("recipient", END, 30, java.util.List.of())).isFalse();
        verify(mapper, never()).insert(any());
    }
    @Test void withdrawalDuplicateIsSuppressed() {
        when(mapper.deliveredToday(any())).thenReturn(true);
        assertThat(publisher.withdrawalSummary("recipient", END, 30, DUE)).isFalse();
        verify(mapper, never()).insert(any());
    }
    @Test void cashShortageTextFollowsMockupWithoutExpectedBalance() {
        assertThat(publisher.cashShortage("recipient", "202611", new java.math.BigDecimal("1000000000"))).isTrue();
        var capture = ArgumentCaptor.forClass(Delivery.class);
        verify(mapper).insert(capture.capture());
        assertThat(capture.getValue().content()).isEqualTo("2026-11 자금 부족 예상 (최소 운영자금 10억 원 미만)");
        assertThat(capture.getValue().type()).isEqualTo("CASH_SHORT");
    }
    @Test void canReceiveChecksStatusPermissionScopeAndTarget() {
        assertThat(publisher.canReceive("recipient", "DUN", "UI-13", "bill")).isTrue();
        access.targetExists = false;
        assertThat(publisher.canReceive("recipient", "DUN", "UI-13", "bill")).isFalse();
        access.targetExists = true;
        when(mapper.eligibleAccount("recipient")).thenReturn(false);
        assertThat(publisher.canReceive("recipient", "DUN", "UI-13", "bill")).isFalse();
        verify(mapper, never()).insert(any());
    }
    @Test void uploadReadsWarningCountFromStoredUploadAndTargetsUploaderOnly() {
        when(mapper.uploadOwner("42")).thenReturn("recipient"); when(mapper.uploadWarnings("recipient", "42")).thenReturn(7);
        assertThat(publisher.uploadCompleted("42", "UI-20", null)).isTrue();
        var capture = ArgumentCaptor.forClass(Delivery.class);
        verify(mapper).insert(capture.capture());
        assertThat(capture.getValue().recipient()).isEqualTo("recipient");
        assertThat(capture.getValue().content()).isEqualTo("업로드 완료 (경고 7건)");
        assertThat(capture.getValue().type()).isEqualTo("UPLD_DONE");
    }
    @Test void anotherUploaderCannotReceiveUploadCompletion() {
        when(mapper.uploadOwner("not-owned")).thenReturn(null);
        assertThat(publisher.uploadCompleted("not-owned", "UI-20", "투입 배정")).isFalse();
        verify(mapper, never()).selectAccess(any(), any());
        verify(mapper, never()).insert(any());
    }
    @Test void ownedUploadWithoutWarningsReceivesUploadCompletion() {
        when(mapper.uploadOwner("42")).thenReturn("recipient"); when(mapper.uploadWarnings("recipient", "42")).thenReturn(0);
        assertThat(publisher.uploadCompleted("42", "UI-20", null)).isTrue();
        var capture = ArgumentCaptor.forClass(Delivery.class);
        verify(mapper).insert(capture.capture());
        assertThat(capture.getValue().recipient()).isEqualTo("recipient");
        assertThat(capture.getValue().content()).isEqualTo("업로드 완료 (경고 0건)");
        assertThat(capture.getValue().type()).isEqualTo("UPLD_DONE");
    }
    @Test void quoteTextNeverIncludesSensitiveBusinessValues() {
        assertThat(publisher.quoteRejected("recipient", "opportunity")).isTrue();
        var capture = ArgumentCaptor.forClass(Delivery.class);
        verify(mapper).insert(capture.capture());
        assertThat(capture.getValue().content()).isEqualTo("견적이 반려되었습니다.").doesNotContainPattern("[0-9]");
        assertThat(capture.getValue().type()).isEqualTo("QUOTE_REJ");
    }
    @Test void overdueOnlyEmitsThreeApprovedStagesNotAiDone() {
        for (int stage = 1; stage <= 3; stage++) assertThat(publisher.overdue("recipient", "bill", stage)).isTrue();
        var capture = ArgumentCaptor.forClass(Delivery.class);
        verify(mapper, times(3)).insert(capture.capture());
        assertThat(capture.getAllValues()).allSatisfy(row -> assertThat(row.type()).isEqualTo("DUN"));
        assertThatThrownBy(() -> publisher.overdue("recipient", "bill", 4)).hasMessage("미수 단계를 확인해 주세요.");
    }
}
