package com.company.common.uploads.service;

import com.company.common.support.service.CodeFailure;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import org.springframework.stereotype.Service;

/** DB upload ID로만 접근하는 불변 원본 저장소. DB 검사 기록 실패 시 discard로 보상한다. */
@Service
public class UploadFileStore {
    private final Path directory;
    /** 리눅스식 권한(posix)을 아는 파일 시스템인지. 리눅스·도커는 true, Windows 개발 PC는 false. */
    private final boolean posix;

    public UploadFileStore() {
        this(Path.of(System.getProperty("java.io.tmpdir"), "company-common-uploads"));
    }
    public UploadFileStore(Path directory) {
        this(directory, directory.getFileSystem().supportedFileAttributeViews().contains("posix"));
    }
    /** 시험용: posix=false로 Windows처럼 권한 지시 없이 저장하는 길을 리눅스에서도 확인한다. */
    UploadFileStore(Path directory, boolean posix) {
        this.directory = directory.toAbsolutePath().normalize();
        this.posix = posix;
    }

    public void retain(long uploadId, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 10 * 1024 * 1024) {
            throw CodeFailure.invalid("file", "10MB 이하의 엑셀 파일을 선택해 주세요.");
        }
        Path target = original(uploadId);
        try {
            privateDirectory();
            // Windows는 posix 권한 지시를 받지 않으므로 권한 없이 만든다(Windows 임시 폴더는 본인만 쓴다).
            Path pending = posix
                ? Files.createTempFile(directory, "upload-", ".pending",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                : Files.createTempFile(directory, "upload-", ".pending");
            try {
                try (var channel = FileChannel.open(pending, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
                // Linking publishes a complete file atomically and never replaces an existing original.
                Files.createLink(target, pending);
            } finally { Files.deleteIfExists(pending); }
        } catch (IOException exception) {
            throw new IllegalStateException("업로드 원본을 보관할 수 없습니다.");
        }
    }

    public byte[] read(long uploadId) {
        Path target = original(uploadId);
        try {
            privateDirectory();
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.size(target) > 10 * 1024 * 1024) {
                throw new IOException("Invalid original file");
            }
            return Files.readAllBytes(target);
        } catch (IOException exception) {
            throw new IllegalStateException("업로드 원본을 읽을 수 없습니다.");
        }
    }

    public void discard(long uploadId) {
        Path target = original(uploadId);
        try {
            privateDirectory();
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new IllegalStateException("업로드 원본을 정리할 수 없습니다.");
        }
    }

    private Path original(long uploadId) {
        if (uploadId <= 0) throw CodeFailure.invalid("uploadId", "업로드 식별자를 확인해 주세요.");
        return directory.resolve(Long.toString(uploadId) + ".xlsx");
    }

    private void privateDirectory() throws IOException {
        if (!posix) {
            // Windows: 권한 지시 없이 만들고, 진짜 폴더인지(바로가기 아님)만 확인한다.
            try {
                Files.createDirectory(directory);
            } catch (FileAlreadyExistsException exists) {
                if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe original directory");
            }
            return;
        }
        try {
            Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rwx------")));
        } catch (FileAlreadyExistsException exists) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS)
                    .equals(PosixFilePermissions.fromString("rwx------"))) {
                throw new IOException("Unsafe original directory");
            }
        }
    }
}
