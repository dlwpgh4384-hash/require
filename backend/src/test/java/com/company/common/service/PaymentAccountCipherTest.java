package com.company.common.service;

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
    @Test void missingOrWrongSizedKeyRefusesToEncrypt() {
        for (String key : new String[]{"", "not-base64!", Base64.getEncoder().encodeToString(new byte[16])}) {
            assertThatThrownBy(() -> new PaymentAccountCipher(key).encrypt(account()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("계좌 암호화 키가 설정되지 않았습니다. 관리자에게 문의해 주세요.");
        }
    }
}
