package com.company.common.uploads.service;

import com.company.common.support.service.CodeFailure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class UploadFileStoreTest {
    @TempDir Path owned;
    private UploadFileStore store() { return new UploadFileStore(owned.resolve("originals")); }

    @Test void retainedOriginalSurvivesNewStoreInstanceAndIsByteIdentical() {
        byte[] bytes = {1, 2, 3, 4};
        store().retain(1, bytes);
        assertThat(store().read(1)).isEqualTo(bytes);
    }
    @Test void existingOriginalIsNeverOverwritten() {
        store().retain(1, new byte[]{1});
        assertThatThrownBy(() -> store().retain(1, new byte[]{2})).isInstanceOf(IllegalStateException.class);
        assertThat(store().read(1)).containsExactly((byte) 1);
    }
    @Test void compensationRemovesOnlyNewOriginal() {
        store().retain(1, new byte[]{1});
        store().retain(2, new byte[]{2});
        store().discard(1);
        assertThatThrownBy(() -> store().read(1)).isInstanceOf(IllegalStateException.class);
        assertThat(store().read(2)).containsExactly((byte) 2);
    }
    @Test void nonPositiveIdentifierIsRejectedBeforeFilesystemAccess() {
        assertThatThrownBy(() -> store().retain(-1, new byte[]{1})).isInstanceOf(CodeFailure.class);
        assertThat(owned.resolve("originals")).doesNotExist();
    }
    @Test void symlinkOriginalIsNeverRead() throws Exception {
        store().retain(1, new byte[]{1});
        Path original = owned.resolve("originals/1.xlsx");
        Path external = owned.resolve("other");
        Files.move(original, external);
        Files.createSymbolicLink(original, external);
        assertThatThrownBy(() -> store().read(1)).isInstanceOf(IllegalStateException.class);
    }
    @Test void concurrentRetentionPublishesExactlyOneCompleteImmutableOriginal() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> retain(new byte[]{1, 1, 1}));
            var second = pool.submit(() -> retain(new byte[]{2, 2, 2}));
            assertThat(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS)).isTrue();
            byte[] saved = store().read(1);
            assertThat(java.util.Arrays.equals(saved, new byte[]{1, 1, 1})
                || java.util.Arrays.equals(saved, new byte[]{2, 2, 2})).isTrue();
        } finally { pool.shutdownNow(); }
    }
    private boolean retain(byte[] bytes) {
        try { store().retain(1, bytes); return true; }
        catch (IllegalStateException conflict) { return false; }
    }
}
