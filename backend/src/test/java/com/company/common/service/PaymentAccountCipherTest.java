package com.company.common.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class PaymentAccountCipherTest {
    @TempDir Path owned;

    private PaymentAccountCipher cipher() { return new PaymentAccountCipher(owned.resolve("accounts")); }
    private String account() { return "123-4567-8901"; }

    @Test void authenticatedEncryptionRoundTripsWithoutStoringPlaintext() throws Exception {
        var cipher = cipher();
        String encrypted = cipher.encrypt(account());
        assertThat(encrypted).doesNotContain(account());
        assertThat(cipher.decrypt(encrypted)).isEqualTo(account());
        assertThat(Base64.getDecoder().decode(encrypted)).hasSize(12 + account().length() + 16);
        try (var files = Files.list(owned.resolve("accounts"))) {
            for (var file : files.toList()) {
                assertThat(new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8))
                    .doesNotContain(account());
            }
        }
    }
    @Test void samePlaintextUsesFreshIv() {
        var cipher = cipher();
        assertThat(cipher.encrypt(account())).isNotEqualTo(cipher.encrypt(account()));
    }
    @Test void restartedInstanceReusesExistingLocalKey() {
        String encrypted = cipher().encrypt(account());
        assertThat(cipher().decrypt(encrypted)).isEqualTo(account());
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
    @Test void nullAccountRemainsNullWithoutCreatingAKey() {
        var cipher = cipher();
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
        assertThat(cipher.lastFour(null)).isNull();
        assertThat(owned.resolve("accounts")).doesNotExist();
    }
    @Test void keyAndDirectoryAreOwnerOnly() throws Exception {
        cipher().encrypt(account());
        assertThat(Files.getPosixFilePermissions(owned.resolve("accounts")))
            .isEqualTo(PosixFilePermissions.fromString("rwx------"));
        assertThat(Files.getPosixFilePermissions(owned.resolve("accounts/account-key.bin")))
            .isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }
    @Test void simultaneousFirstUseKeepsOneReusableKey() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        try {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 8; i++) jobs.add(pool.submit(() -> cipher().encrypt(account())));
            for (var job : jobs) assertThat(cipher().decrypt(job.get(10, TimeUnit.SECONDS))).isEqualTo(account());
        } finally { pool.shutdownNow(); }
    }
    @Test void keySymlinkIsRejectedRatherThanFollowingAnotherFile() throws Exception {
        cipher().encrypt(account());
        Path key = owned.resolve("accounts/account-key.bin");
        Path elsewhere = owned.resolve("unrelated-key");
        Files.move(key, elsewhere);
        Files.createSymbolicLink(key, elsewhere);
        assertThatThrownBy(() -> cipher().encrypt(account())).isInstanceOf(IllegalStateException.class);
    }
    @Test void corruptExistingKeyIsNotReplaced() throws Exception {
        cipher().encrypt(account());
        Path key = owned.resolve("accounts/account-key.bin");
        Files.write(key, new byte[]{1, 2});
        assertThatThrownBy(() -> cipher().encrypt(account())).isInstanceOf(IllegalStateException.class);
        assertThat(Files.size(key)).isEqualTo(2);
    }
}
