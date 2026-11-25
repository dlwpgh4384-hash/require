package com.company.common.common;

import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Local operational receipts, not a replacement for the transaction advisory lock or committed DB snapshots. */
@Component
public class AiRecommendationExecutionJournal {
    private static final Object MONITOR = new Object();
    private static final int MAX_BYTES = 1_000_000;
    private final ObjectMapper json;
    private final Path directory;

    public AiRecommendationExecutionJournal(ObjectMapper json, @Value("${java.io.tmpdir}") String temporaryDirectory) {
        this.json = json;
        this.directory = Path.of(temporaryDirectory, "company-common-ai-recommendations").toAbsolutePath().normalize();
    }

    public record Receipt(String recommendationId, JsonNode condition, JsonNode result) {}

    public static String fingerprint(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw CodeFailure.invalid("requestId", "추천 요청 식별자를 확인해 주세요.");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(requestId.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /** Keep the DB advisory lock until commit. Reconcile pending receipts with DB before discarding or completing. */
    public <T> T locked(String requestId, Function<Entry, T> work) {
        String fingerprint = fingerprint(requestId);
        synchronized (MONITOR) {
            try {
                prepareDirectory();
                Path lockPath = directory.resolve(fingerprint + ".lock");
                try (var channel = FileChannel.open(lockPath,
                    Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                     var lock = channel.lock()) {
                    Files.setPosixFilePermissions(lockPath, PosixFilePermissions.fromString("rw-------"));
                    var entry = new Entry(fingerprint);
                    try {
                        return work.apply(entry);
                    } finally {
                        entry.open = false;
                    }
                }
            } catch (IOException | SecurityException exception) {
                throw unavailable();
            }
        }
    }

    private void prepareDirectory() throws IOException {
        Files.createDirectories(directory,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid journal directory");
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
    }

    public final class Entry {
        private final String fingerprint;
        private final Path path;
        private boolean open = true;

        private Entry(String fingerprint) {
            this.fingerprint = fingerprint;
            this.path = directory.resolve(fingerprint + ".json");
        }

        public Receipt read() {
            requireOpen();
            try {
                if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
                try (var channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                    if (channel.size() > MAX_BYTES) throw unavailable();
                    var buffer = ByteBuffer.allocate((int) channel.size());
                    while (buffer.hasRemaining()) {
                        if (channel.read(buffer) < 0) throw unavailable();
                    }
                    JsonNode root = json.reader()
                        .with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                        .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .readTree(buffer.array());
                    if (root == null || !root.isObject() || !root.path("v").isIntegralNumber()
                        || root.path("v").intValue() != 1
                        || !fingerprint.equals(root.path("f").asText())
                        || !root.path("id").isTextual() || !validId(root.path("id").textValue())
                        || !root.path("condition").isObject()
                        || !root.has("result") || !(root.get("result").isNull() || root.get("result").isObject())) {
                        throw unavailable();
                    }
                    return new Receipt(root.get("id").textValue(), root.get("condition"),
                        root.get("result").isNull() ? null : root.get("result"));
                }
            } catch (IOException | SecurityException exception) {
                throw unavailable();
            }
        }

        public Receipt matching(JsonNode normalizedCondition) {
            requireOpen();
            if (normalizedCondition == null || !normalizedCondition.isObject()) throw unavailable();
            Receipt receipt = read();
            if (receipt != null && !receipt.condition().equals(normalizedCondition)) {
                throw CodeFailure.invalid("requestId", "같은 추천 요청의 조건이 다릅니다. 새 요청으로 실행해 주세요.");
            }
            return receipt;
        }

        public void pending(String recommendationId, JsonNode normalizedCondition) {
            requireOpen();
            if (!validId(recommendationId) || normalizedCondition == null || !normalizedCondition.isObject()) {
                throw unavailable();
            }
            if (read() != null) throw CodeFailure.conflict();
            write(new Receipt(recommendationId, normalizedCondition, null));
        }

        /** Call only after the corresponding DB transaction has committed (zero results have no candidate DB rows). */
        public void complete(JsonNode result) {
            requireOpen();
            Receipt receipt = read();
            if (receipt == null || result == null || !result.isObject()) throw unavailable();
            if (receipt.result() != null) {
                // JSON round-trips can turn integral BigDecimal scores into integer nodes.
                boolean unchanged = receipt.result().equals((left, right) ->
                    left.isNumber() && right.isNumber()
                        ? left.decimalValue().compareTo(right.decimalValue())
                        : (left.equals(right) ? 0 : 1), result);
                if (!unchanged) throw CodeFailure.conflict();
                return;
            }
            if (!receipt.recommendationId().equals(result.path("recommendationId").textValue())
                || !receipt.condition().equals(result.get("condition"))
                || !result.path("items").isArray() || result.path("items").size() > 5) {
                throw CodeFailure.conflict();
            }
            write(new Receipt(receipt.recommendationId(), receipt.condition(), result));
        }

        /** The caller must first prove that the pending execution has no committed candidate rows. */
        public void discardPending() {
            requireOpen();
            Receipt receipt = read();
            if (receipt != null && receipt.result() != null) throw CodeFailure.conflict();
            try {
                Files.deleteIfExists(path);
            } catch (IOException | SecurityException exception) {
                throw unavailable();
            }
        }

        private void write(Receipt receipt) {
            Path staged = null;
            try {
                var root = json.createObjectNode().put("v", 1).put("f", fingerprint)
                    .put("id", receipt.recommendationId());
                root.set("condition", receipt.condition());
                root.set("result", receipt.result() == null ? json.getNodeFactory().nullNode() : receipt.result());
                byte[] bytes = json.writeValueAsBytes(root);
                if (bytes.length > MAX_BYTES) throw unavailable();
                staged = Files.createTempFile(directory, fingerprint + "-", ".part",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                try (var channel = FileChannel.open(staged, StandardOpenOption.WRITE)) {
                    var buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
                Files.move(staged, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                try (var channel = FileChannel.open(directory, StandardOpenOption.READ)) {
                    channel.force(true);
                }
            } catch (IOException | SecurityException exception) {
                throw unavailable();
            } finally {
                if (staged != null) {
                    try {
                        Files.deleteIfExists(staged);
                    } catch (IOException exception) {
                        // A failed pending-file cleanup must never replace the original failure.
                    }
                }
            }
        }

        private void requireOpen() {
            if (!open) throw new IllegalStateException("Journal entry is no longer locked");
        }
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[1-9][0-9]{0,11}");
    }

    private static CodeFailure unavailable() {
        return CodeFailure.invalid("requestId", "추천 실행 기록을 확인할 수 없습니다. 다시 시도해 주세요.");
    }
}
