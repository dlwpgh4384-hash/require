package com.company.common.common;

import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class AiRecommendationExecutionJournalTest {
    @TempDir Path owned;
    private final ObjectMapper json = new ObjectMapper();

    private AiRecommendationExecutionJournal journal() {
        return new AiRecommendationExecutionJournal(json, owned.toString());
    }

    private JsonNode condition() {
        return json.createObjectNode().put("targetType", "opportunity").put("targetId", "owned-target")
            .put("jobCode", "BE").put("startMonth", "2026-12").put("gradeCode", "HI");
    }

    private JsonNode result(String id) {
        var result = json.createObjectNode().put("recommendationId", id);
        result.set("condition", condition());
        result.putArray("items");
        return result;
    }

    private Path receipt(String requestId) {
        return owned.resolve("company-common-ai-recommendations")
            .resolve(AiRecommendationExecutionJournal.fingerprint(requestId) + ".json");
    }

    @Test
    void fingerprintPreservesTheOriginalStringAndNeverUsesRequestAsPath() {
        String first = AiRecommendationExecutionJournal.fingerprint("../request/한글");
        assertThat(first).matches("[a-f0-9]{64}");
        assertThat(first).isEqualTo(AiRecommendationExecutionJournal.fingerprint("../request/한글"));
        assertThat(first).isNotEqualTo(AiRecommendationExecutionJournal.fingerprint(" ../request/한글"));
    }

    @Test
    void blankRequestIsRejectedBeforeCreatingFiles() {
        assertThatThrownBy(() -> journal().locked(" ", entry -> entry.read())).isInstanceOf(CodeFailure.class);
        assertThat(owned.resolve("company-common-ai-recommendations")).doesNotExist();
    }

    @Test
    void zeroCandidateReceiptSurvivesRestartAndReplaysTheExactExecution() {
        journal().locked("same-event", entry -> {
            assertThat(entry.read()).isNull();
            entry.pending("123", condition());
            entry.complete(result("123"));
            return null;
        });
        var replay = journal().locked("same-event", entry -> entry.read());
        assertThat(replay.recommendationId()).isEqualTo("123");
        assertThat(replay.condition()).isEqualTo(condition());
        assertThat(replay.result()).isEqualTo(result("123"));
        assertThat(replay.result().path("items")).isEmpty();
    }

    @Test
    void separateRequestForSameTargetGetsAnIndependentReceipt() {
        journal().locked("first-event", entry -> { entry.pending("123", condition()); return null; });
        journal().locked("new-event", entry -> { entry.pending("124", condition()); return null; });
        assertThat(journal().locked("first-event", entry -> entry.read()).recommendationId()).isEqualTo("123");
        assertThat(journal().locked("new-event", entry -> entry.read()).recommendationId()).isEqualTo("124");
    }

    @Test
    void sameRequestWithDifferentNormalizedConditionCannotReplayOrOverwriteTheReceipt() {
        journal().locked("event", entry -> { entry.pending("123", condition()); return null; });
        var changed = condition().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) changed).put("gradeCode", "different-grade");
        assertThatThrownBy(() -> journal().locked("event", entry -> entry.matching(changed)))
            .isInstanceOfSatisfying(CodeFailure.class,
                failure -> assertThat(failure.getField()).isEqualTo("requestId"));
        assertThat(journal().locked("event", entry -> entry.matching(condition())).recommendationId()).isEqualTo("123");
    }

    @Test
    void completionMustBelongToTheReservedExecution() {
        journal().locked("event", entry -> { entry.pending("123", condition()); return null; });
        assertThatThrownBy(() -> journal().locked("event", entry -> {
            entry.complete(result("124"));
            return null;
        })).isInstanceOf(CodeFailure.class);
        assertThat(journal().locked("event", entry -> entry.read()).result()).isNull();
    }

    @Test
    void pendingReceiptSurvivesFailureForExplicitDatabaseReconciliation() {
        assertThatThrownBy(() -> journal().locked("interrupted", entry -> {
            entry.pending("123", condition());
            throw new IllegalStateException("injected rollback");
        })).isInstanceOf(IllegalStateException.class);
        var recovered = journal().locked("interrupted", entry -> entry.read());
        assertThat(recovered.recommendationId()).isEqualTo("123");
        assertThat(recovered.result()).isNull();
        assertThat(recovered.condition()).isEqualTo(condition());
    }

    @Test
    void pendingCannotBeOverwrittenWithoutExplicitReconciliation() {
        journal().locked("event", entry -> { entry.pending("123", condition()); return null; });
        assertThatThrownBy(() -> journal().locked("event", entry -> {
            entry.pending("124", condition());
            return null;
        })).isInstanceOf(CodeFailure.class);
        assertThat(journal().locked("event", entry -> entry.read()).recommendationId()).isEqualTo("123");
    }

    @Test
    void rolledBackPendingReceiptCanBeDiscardedBeforeReservingANewExecution() {
        journal().locked("event", entry -> {
            entry.pending("123", condition());
            entry.discardPending();
            entry.pending("124", condition());
            entry.complete(result("124"));
            return null;
        });
        assertThat(journal().locked("event", entry -> entry.read()).result()).isEqualTo(result("124"));
    }

    @Test
    void committedReceiptCannotBeDiscardedOrReplaced() {
        journal().locked("event", entry -> {
            entry.pending("123", condition());
            entry.complete(result("123"));
            entry.complete(result("123"));
            assertThatThrownBy(entry::discardPending).isInstanceOf(CodeFailure.class);
            assertThatThrownBy(() -> entry.complete(result("124"))).isInstanceOf(CodeFailure.class);
            return null;
        });
        assertThat(journal().locked("event", entry -> entry.read()).result()).isEqualTo(result("123"));
    }

    @Test
    void completedScoresReplayAcrossNumericNodeTypesButChangedScoresStillConflict() {
        var original = result("123");
        ((com.fasterxml.jackson.databind.node.ArrayNode) original.path("items"))
            .addObject().put("totalScore", new BigDecimal("62"))
            .put("fractionalScore", new BigDecimal("3.50"));
        journal().locked("scored-event", entry -> {
            entry.pending("123", condition());
            entry.complete(original);
            return null;
        });

        journal().locked("scored-event", entry -> {
            assertThat(entry.read().result().path("items").path(0).path("totalScore").isIntegralNumber())
                .isTrue();
            entry.complete(original);
            var changed = original.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) changed.path("items").path(0))
                .put("totalScore", new BigDecimal("63"));
            assertThatThrownBy(() -> entry.complete(changed)).isInstanceOf(CodeFailure.class);
            assertThat(entry.read().result().path("items").path(0).path("totalScore").intValue())
                .isEqualTo(62);
            return null;
        });
    }

    @Test
    void concurrentSameEventHasOneReservationAcrossJournalInstances() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> reserveIfAbsent("123"));
            var second = pool.submit(() -> reserveIfAbsent("124"));
            assertThat(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(journal().locked("same-event", entry -> entry.read()).recommendationId()).isIn("123", "124");
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean reserveIfAbsent(String id) {
        return journal().locked("same-event", entry -> {
            if (entry.read() != null) return false;
            entry.pending(id, condition());
            return true;
        });
    }

    @Test
    void corruptReceiptFailsClosedRatherThanGeneratingDuplicateExecution() throws Exception {
        journal().locked("event", entry -> { entry.pending("123", condition()); return null; });
        Files.writeString(receipt("event"), "{broken");
        assertThatThrownBy(() -> journal().locked("event", entry -> entry.read()))
            .isInstanceOfSatisfying(CodeFailure.class, failure -> {
                assertThat(failure.getField()).isEqualTo("requestId");
                assertThat(failure.getMessage()).doesNotContain(owned.toString(), "{broken");
            });
    }

    @Test
    void symlinkReceiptCannotReadAnotherFile() throws Exception {
        journal().locked("event", entry -> { entry.pending("123", condition()); return null; });
        Path other = owned.resolve("other.json");
        Files.move(receipt("event"), other);
        Files.createSymbolicLink(receipt("event"), other);
        assertThatThrownBy(() -> journal().locked("event", entry -> entry.read())).isInstanceOf(CodeFailure.class);
    }

    @Test
    void receiptAndDirectoryAreOwnerOnlyAndAtomicWritesLeaveNoPartialFiles() throws Exception {
        journal().locked("event", entry -> {
            entry.pending("123", condition());
            entry.complete(result("123"));
            return null;
        });
        assertThat(Files.getPosixFilePermissions(receipt("event")))
            .isEqualTo(PosixFilePermissions.fromString("rw-------"));
        assertThat(Files.getPosixFilePermissions(receipt("event").getParent()))
            .isEqualTo(PosixFilePermissions.fromString("rwx------"));
        try (var files = Files.list(receipt("event").getParent())) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                .noneMatch(name -> name.endsWith(".part"));
        }
    }

    @Test
    void unlockedEntryCannotBeUsedAfterTheCallback() {
        var entry = journal().locked("event", value -> value);
        assertThatThrownBy(entry::read).isInstanceOf(IllegalStateException.class);
    }
}
