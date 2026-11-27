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
import com.company.common.support.service.CodeFailure;
import org.slf4j.LoggerFactory;
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
    static final String MISSING = "계좌 암호화 키가 설정되지 않았습니다. 관리자에게 문의해 주세요.";
    static final String INVALID = "계좌 암호화 키가 올바르지 않습니다. 관리자에게 문의해 주세요.";
    private final byte[] key;
    /** 키 값은 있는데 Base64 32바이트가 아니다. */
    private final boolean invalid;

    public PaymentAccountCipher(@Value("${payment.account-key:}") String base64Key) {
        boolean present = base64Key != null && !base64Key.isBlank();
        byte[] decoded;
        try {
            decoded = present ? Base64.getDecoder().decode(base64Key.trim()) : new byte[0];
        } catch (IllegalArgumentException exception) {
            decoded = new byte[0];
        }
        this.key = decoded.length == 32 ? decoded.clone() : null;
        this.invalid = present && key == null;
        Arrays.fill(decoded, (byte) 0);
        // 빈은 하나라 시작할 때 한 번만 남는다. 키 값·길이는 로그에 남기지 않는다.
        if (invalid) LoggerFactory.getLogger(PaymentAccountCipher.class)
            .warn("event=payment_account_key_invalid reason=not_base64_32_bytes");
        // 로그인(UI-22) ⑬-2: application.yml의 개발용 기본 키로 켜지면 WARN 한 번(키 값은 쓰지 않는다).
        if (key != null && developmentDefault(base64Key.trim())) LoggerFactory.getLogger(PaymentAccountCipher.class)
            .warn("개발용 계좌 암호화 키를 쓰고 있습니다. 운영 전에 PAYMENT_ACCOUNT_KEY를 바꾸세요.");
    }

    /** application.yml 개발용 기본 키의 SHA-256(16진). 코드에 키 값을 두지 않으려고 지문만 둔다. */
    static final String DEVELOPMENT_KEY_SHA256 = "e67338e12c53116b7c0c8c1bd4b49c6725ccc706adfb1f181cd20a0b5d5a5640";

    static boolean developmentDefault(String base64Key) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(base64Key.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest).equals(DEVELOPMENT_KEY_SHA256);
        } catch (GeneralSecurityException exception) {
            return false;
        }
    }

    /**
     * 키가 없거나 틀리면 500 대신 알려진 오류(400)로 답한다(B.7 반영·거래처 저장).
     * 목록의 끝 4자리는 CommonClientService가 이 오류도 「확인 필요」로 바꿔 목록 전체가 실패하지 않는다.
     */
    private byte[] key() {
        if (key == null) throw CodeFailure.invalid("paymentAccount", invalid ? INVALID : MISSING);
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
