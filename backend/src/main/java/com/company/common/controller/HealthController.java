package com.company.common.controller;

import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버 상태 확인(2026-11-12 사용자 결정): 정상이면 200, DB에 연결할 수 없으면 503.
 * 응답에 DB 주소·오류 내용 같은 내부 정보는 넣지 않는다.
 */
@RestController
public class HealthController {
    public record Liveness(boolean success, Map<String, Object> meta) {}
    public record Unavailable(boolean success, Map<String, Object> meta, String message) {}

    private final ObjectProvider<DataSource> dataSource;

    public HealthController(ObjectProvider<DataSource> dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping(value = "/api/health", produces = "application/json")
    public ResponseEntity<?> getHealth() {
        DataSource source = dataSource.getIfAvailable();
        if (source != null) {
            try (var connection = source.getConnection()) {
                if (!connection.isValid(2)) return unavailable();
            } catch (Exception failure) {
                return unavailable();
            }
        }
        return ResponseEntity.ok(new Liveness(true, Map.of()));
    }

    private static ResponseEntity<Unavailable> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new Unavailable(false, Map.of(), "잠시 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."));
    }
}
