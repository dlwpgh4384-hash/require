package com.company.common.developers.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.assertj.core.api.Assertions.*;

@ResourceLock("java.io.tmpdir")
class DeveloperNumberReservationsTest {
    @TempDir Path directory;
    @Test void freshReaderRetainsAllCheckedNumbersAfterRestart() throws Exception {
        String previous=System.getProperty("java.io.tmpdir");
        try {
            System.setProperty("java.io.tmpdir",directory.toString());var json=new ObjectMapper();
            Path uploads=Files.createDirectory(directory.resolve("company-common-uploads"));
            json.writeValue(uploads.resolve("12.reservations.json").toFile(),Map.of("plannedDevelopers",List.of(Map.of("rowNumber",2,"managementNumber","K-0001"))));
            assertThat(new DeveloperNumberReservations(json).numbers()).containsExactly("K-0001");
            assertThat(new DeveloperNumberReservations(new ObjectMapper()).numbers()).containsExactly("K-0001");
        } finally { System.setProperty("java.io.tmpdir",previous); }
    }
    @Test void corruptCompanionNeverSilentlyReleasesNumbers() throws Exception {
        String previous=System.getProperty("java.io.tmpdir");
        try {
            System.setProperty("java.io.tmpdir",directory.toString());Path uploads=Files.createDirectory(directory.resolve("company-common-uploads"));
            Files.writeString(uploads.resolve("13.reservations.json"),"invalid json");
            assertThatThrownBy(() -> new DeveloperNumberReservations(new ObjectMapper()).numbers()).isInstanceOf(IllegalStateException.class);
        } finally { System.setProperty("java.io.tmpdir",previous); }
    }
}
