package com.company.common.developers.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Reads the retained upload companion documents while the caller owns the DB management lock. */
@Component
public class DeveloperNumberReservations {
    private final ObjectMapper json;
    public DeveloperNumberReservations(ObjectMapper json) { this.json = json; }
    public Set<String> numbers() {
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "company-common-uploads");
        Set<String> result = new HashSet<>();
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return result;
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IllegalStateException("업로드 예약 경로를 확인해 주세요.");
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(p -> p.getFileName().toString().matches("[0-9]+(?:[.-][A-Za-z-]+)?\\.json")).toList()) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 4_000_000)
                    throw new IOException("Invalid reservation document");
                collect(json.readTree(Files.readAllBytes(path)), result);
            }
            return result;
        } catch (IOException failure) { throw new IllegalStateException("업로드 번호 예약을 읽을 수 없습니다.", failure); }
    }
    private void collect(JsonNode node, Set<String> result) {
        if (node == null) return;
        if (node.isObject() && node.has("managementNumber")) {
            String number = node.path("managementNumber").asText();
            if (!number.matches("[A-Za-z0-9-]{1,20}")) throw new IllegalStateException("업로드 예약 번호를 확인해 주세요.");
            result.add(number);
        }
        if (node.isContainerNode()) node.elements().forEachRemaining(child -> collect(child, result));
    }
}
