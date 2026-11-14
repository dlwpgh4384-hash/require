package com.company.common.clients.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * SER-005 거래처 계좌번호 암호화: AES-256-GCM, 값마다 새 IV.
 * 키는 설정 값 payment.account-key(Base64 32바이트)에서 읽는다. 환경 변수 PAYMENT_ACCOUNT_KEY가 있으면 그것을 쓴다.
 * 지금 application.yml의 값은 개발용 임시 키다(2026-11-14 사용자 결정). 운영 전에 환경 변수로 옮기고 파일의 값은 지운다.
 */
@Service
public class PaymentAccountCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] key;

    public PaymentAccountCipher(@Value("${payment.account-key:}") String base64Key) {
        byte[] decoded;
        try {
            decoded = base64Key == null || base64Key.isBlank() ? new byte[0] : Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException exception) {
            decoded = new byte[0];
        }
        this.key = decoded.length == 32 ? decoded.clone() : null;
        Arrays.fill(decoded, (byte) 0);
    }

    private byte[] key() {
        if (key == null) throw new IllegalStateException("계좌 암호화 키가 설정되지 않았습니다. 관리자에게 문의해 주세요.");
        return key;
    }

    public String encrypt(String accountNumber) {
        if (accountNumber == null) return null;
        if (!accountNumber.matches("[0-9-]{8,20}")) {
            throw new IllegalArgumentException("계좌번호 형식을 확인해 주세요.");
        }
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(accountNumber.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("계좌 정보를 암호화할 수 없습니다.");
        }
    }

    public String decrypt(String encryptedAccount) {
        if (encryptedAccount == null) return null;
        if (encryptedAccount.length() > 200) throw unreadable();
        byte[] encoded;
        try { encoded = Base64.getDecoder().decode(encryptedAccount); }
        catch (IllegalArgumentException exception) { throw unreadable(); }
        if (encoded.length < 36 || encoded.length > 48) throw unreadable();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"),
                new GCMParameterSpec(128, Arrays.copyOf(encoded, 12)));
            return new String(cipher.doFinal(encoded, 12, encoded.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw unreadable();
        }
    }

    public String lastFour(String encryptedAccount) {
        String account = decrypt(encryptedAccount);
        if (account == null) return null;
        String digits = account.replace("-", "");
        return digits.substring(Math.max(0, digits.length() - 4));
    }

    private static IllegalStateException unreadable() {
        return new IllegalStateException("계좌 정보를 읽을 수 없습니다.");
    }
}
