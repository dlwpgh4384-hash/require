package com.company.common.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class PaymentAccountCipher {
    private static final Object KEY_CREATION = new Object();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Path directory;

    public PaymentAccountCipher() {
        this(Path.of(System.getProperty("java.io.tmpdir"), "company-payment-accounts"));
    }

    public PaymentAccountCipher(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public String encrypt(String accountNumber) {
        if (accountNumber == null) return null;
        if (!accountNumber.matches("[0-9-]{8,20}")) {
            throw new IllegalArgumentException("계좌번호 형식을 확인해 주세요.");
        }
        byte[] key = localKey();
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(accountNumber.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("계좌 정보를 암호화할 수 없습니다.");
        } finally { Arrays.fill(key, (byte) 0); }
    }

    public String decrypt(String encryptedAccount) {
        if (encryptedAccount == null) return null;
        if (encryptedAccount.length() > 200) throw unreadable();
        byte[] encoded;
        try { encoded = Base64.getDecoder().decode(encryptedAccount); }
        catch (IllegalArgumentException exception) { throw unreadable(); }
        if (encoded.length < 36 || encoded.length > 48) throw unreadable();
        byte[] key = localKey();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, Arrays.copyOf(encoded, 12)));
            return new String(cipher.doFinal(encoded, 12, encoded.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw unreadable();
        } finally { Arrays.fill(key, (byte) 0); }
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

    private byte[] localKey() {
        synchronized (KEY_CREATION) {
            try {
                try {
                    Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")));
                } catch (FileAlreadyExistsException exists) {
                    requirePrivate(directory, true);
                }
                Path lockPath = directory.resolve("account-key.lock");
                try {
                    Files.createFile(lockPath, PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")));
                } catch (FileAlreadyExistsException exists) {
                    requirePrivate(lockPath, false);
                }
                try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                     var lock = channel.lock()) {
                    Path keyPath = directory.resolve("account-key.bin");
                    if (!Files.exists(keyPath, LinkOption.NOFOLLOW_LINKS)) {
                        byte[] generated = new byte[32];
                        RANDOM.nextBytes(generated);
                        Path pending = Files.createTempFile(directory, "account-key-", ".pending",
                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                        try {
                            try (var output = FileChannel.open(pending, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                                ByteBuffer bytes = ByteBuffer.wrap(generated);
                                while (bytes.hasRemaining()) output.write(bytes);
                                output.force(true);
                            }
                            Files.move(pending, keyPath, StandardCopyOption.ATOMIC_MOVE);
                        } finally {
                            Arrays.fill(generated, (byte) 0);
                            Files.deleteIfExists(pending);
                        }
                    }
                    requirePrivate(keyPath, false);
                    if (Files.size(keyPath) != 32) throw new IOException("Invalid key file");
                    return Files.readAllBytes(keyPath);
                }
            } catch (IOException | UnsupportedOperationException exception) {
                throw new IllegalStateException("계좌 암호화 저장소를 사용할 수 없습니다.");
            }
        }
    }

    private static void requirePrivate(Path path, boolean isDirectory) throws IOException {
        boolean correctKind = isDirectory ? Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
            : Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
        var expected = PosixFilePermissions.fromString(isDirectory ? "rwx------" : "rw-------");
        if (!correctKind || !Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).equals(expected)) {
            throw new IOException("Unsafe key storage");
        }
    }
}
