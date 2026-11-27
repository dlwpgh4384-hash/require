package com.company.common.clients.service;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PaymentAccountCipherTest {
    /** 시험 전용 키(32바이트). 실제 키가 아니다. */
    static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private PaymentAccountCipher cipher() { return new PaymentAccountCipher(TEST_KEY); }
    private String account() { return "123-4567-8901"; }

    @Test void authenticatedEncryptionRoundTripsWithoutStoringPlaintext() {
        var cipher = cipher();
        String encrypted = cipher.encrypt(account());
        assertThat(encrypted).doesNotContain(account());
        assertThat(cipher.decrypt(encrypted)).isEqualTo(account());
        assertThat(Base64.getDecoder().decode(encrypted)).hasSize(12 + account().length() + 16);
    }
    @Test void samePlaintextUsesFreshIv() {
        var cipher = cipher();
        assertThat(cipher.encrypt(account())).isNotEqualTo(cipher.encrypt(account()));
    }
    @Test void anotherInstanceWithTheSameConfiguredKeyReadsEarlierValues() {
        String encrypted = cipher().encrypt(account());
        assertThat(cipher().decrypt(encrypted)).isEqualTo(account());
    }
    @Test void aDifferentKeyCannotReadTheValue() {
        String encrypted = cipher().encrypt(account());
        byte[] other = new byte[32];
        other[0] = 1;
        assertThatThrownBy(() -> new PaymentAccountCipher(Base64.getEncoder().encodeToString(other)).decrypt(encrypted))
            .isInstanceOf(IllegalStateException.class).hasMessage("계좌 정보를 읽을 수 없습니다.");
    }
    @Test void tamperedCiphertextIsRejectedWithoutSensitiveExceptionDetails() {
        var cipher = cipher();
        byte[] encoded = Base64.getDecoder().decode(cipher.encrypt(account()));
        encoded[encoded.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(encoded)))
            .isInstanceOf(IllegalStateException.class).hasMessage("계좌 정보를 읽을 수 없습니다.").hasNoCause();
    }
    @Test void malformedOrPlaintextStoredValueIsRejected() {
        assertThatThrownBy(() -> cipher().decrypt(account())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher().decrypt("AAAA")).isInstanceOf(IllegalStateException.class);
    }
    @Test void displayReturnsOnlyLastFourDigits() {
        var cipher = cipher();
        assertThat(cipher.lastFour(cipher.encrypt(account()))).isEqualTo("8901");
    }
    @Test void nullAccountRemainsNull() {
        var cipher = cipher();
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
        assertThat(cipher.lastFour(null)).isNull();
    }
    @Test void missingKeyRefusesWithAKnownError() {
        assertThatThrownBy(() -> new PaymentAccountCipher("").encrypt(account()))
            .isInstanceOfSatisfying(com.company.common.support.service.CodeFailure.class, f -> assertThat(f.getStatus()).isEqualTo(400))
            .hasMessage("계좌 암호화 키가 설정되지 않았습니다. 관리자에게 문의해 주세요.");
    }
    /** B.7 반영 500 수정(2026-11-27): 키 값은 있는데 Base64 32바이트가 아니면 400 「올바르지 않습니다」, 시작 때 WARN 한 번(키 값 없이). */
    @Test void invalidConfiguredKeyRefusesWith400AndWarnsOnceWithoutTheKey() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(PaymentAccountCipher.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
            for (String key : new String[]{"not-base64!", shortKey}) {
                appender.list.clear();
                var cipher = new PaymentAccountCipher(key);
                assertThatThrownBy(() -> cipher.encrypt(account()))
                    .isInstanceOfSatisfying(com.company.common.support.service.CodeFailure.class, f -> assertThat(f.getStatus()).isEqualTo(400))
                    .hasMessage("계좌 암호화 키가 올바르지 않습니다. 관리자에게 문의해 주세요.");
                assertThatThrownBy(() -> cipher.encrypt(account())).hasMessageContaining("올바르지 않습니다");
                assertThat(appender.list).singleElement().satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                    assertThat(event.getFormattedMessage()).contains("payment_account_key_invalid").doesNotContain(key);
                });
            }
            appender.list.clear();
            new PaymentAccountCipher(TEST_KEY);
            new PaymentAccountCipher("");
            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }
    /** 로그인(UI-22) ⑬-2: application.yml의 개발용 기본 키로 켜지면 WARN 한 번, 키 값은 로그에 없다. 다른 키는 조용하다. */
    @Test void developmentDefaultKeyWarnsOnceWithoutTheKey() throws Exception {
        String yml;
        try (var in = PaymentAccountCipher.class.getResourceAsStream("/application.yml")) {
            yml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var found = java.util.regex.Pattern.compile("\\$\\{PAYMENT_ACCOUNT_KEY:([^}]+)}").matcher(yml);
        assertThat(found.find()).as("application.yml에 개발용 기본 키가 있다(⑬-2)").isTrue();
        String developmentKey = found.group(1).trim();
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(PaymentAccountCipher.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            var cipher = new PaymentAccountCipher(developmentKey);
            assertThat(cipher.decrypt(cipher.encrypt(account()))).isEqualTo(account());
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                assertThat(event.getFormattedMessage())
                    .isEqualTo("개발용 계좌 암호화 키를 쓰고 있습니다. 운영 전에 PAYMENT_ACCOUNT_KEY를 바꾸세요.")
                    .doesNotContain(developmentKey);
            });
            appender.list.clear();
            new PaymentAccountCipher(TEST_KEY);
            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }
}
