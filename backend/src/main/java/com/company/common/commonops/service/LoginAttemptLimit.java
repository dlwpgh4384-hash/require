package com.company.common.commonops.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 같은 접속 IP의 로그인 실패를 서버 메모리에서 센다. 기다리게 하지 않고 한도를 넘으면 바로 429로 돌려준다.
 * 아이디마다 세면 5번째 「계정 잠김」 문구가 가려지므로 IP만 보고, 한 계정 잠금(5번)보다 넉넉하게 둔다. */
@Component
public class LoginAttemptLimit {
    // 「제안」 명세에 문구가 없어 정한 문구.
    public static final String TOO_MANY = "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.";
    /** 직전 60초 안 같은 IP 실패가 이 횟수에 닿으면 다음 시도부터 막는다(「제안」 20번·60초). */
    static final int LIMIT = 20;
    static final long WINDOW_SECONDS = 60;
    private static final int MAX_KEYS = 10_000;
    private final Clock clock;
    private final Map<String, ArrayDeque<Instant>> failures = new LinkedHashMap<>();
    @Autowired
    public LoginAttemptLimit() { this(Clock.systemUTC()); }
    public LoginAttemptLimit(Clock clock) { this.clock = clock; }
    private static String key(String ip) { return ip == null ? "" : ip; }
    private Instant prune() {
        Instant now = clock.instant();
        Instant cutoff = now.minusSeconds(WINDOW_SECONDS);
        failures.values().forEach(queue -> {
            while (!queue.isEmpty() && !queue.getFirst().isAfter(cutoff)) queue.removeFirst();
        });
        failures.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return now;
    }
    public boolean blocked(String ip) {
        synchronized (failures) {
            prune();
            var queue = failures.get(key(ip));
            return queue != null && queue.size() >= LIMIT;
        }
    }
    public void failed(String ip) {
        synchronized (failures) {
            Instant now = prune();
            String key = key(ip);
            if (!failures.containsKey(key) && failures.size() >= MAX_KEYS)
                failures.remove(failures.keySet().iterator().next());
            var queue = failures.computeIfAbsent(key, value -> new ArrayDeque<>());
            queue.addLast(now);
            while (queue.size() > LIMIT) queue.removeFirst();
        }
    }
    public void succeeded(String ip) {
        synchronized (failures) { failures.remove(key(ip)); }
    }
}
