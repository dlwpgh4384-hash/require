package com.company.common.commonops.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LoginFailureDelay {
    private final Clock clock;
    private final LongConsumer sleeper;
    private final Map<String, ArrayDeque<Instant>> attempts = new LinkedHashMap<>();
    @Autowired
    public LoginFailureDelay() {
        this(Clock.systemUTC(), millis -> {
            try { Thread.sleep(millis); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
        });
    }
    public LoginFailureDelay(Clock clock, LongConsumer sleeper) {
        this.clock = clock;
        this.sleeper = sleeper;
    }
    public void failed(String ip) {
        long delay;
        synchronized (attempts) {
            Instant now = clock.instant();
            attempts.values().forEach(queue -> {
                while (!queue.isEmpty() && !queue.getFirst().isAfter(now.minusSeconds(60))) queue.removeFirst();
            });
            attempts.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            if (!attempts.containsKey(ip) && attempts.size() >= 10000)
                attempts.remove(attempts.keySet().iterator().next());
            var queue = attempts.computeIfAbsent(ip, key -> new ArrayDeque<>());
            queue.addLast(now);
            // Nine timestamps suffice to preserve the capped delay without unbounded memory.
            while (queue.size() > 9) queue.removeFirst();
            delay = Math.min(1000, Math.max(0, queue.size() - 5) * 250L);
        }
        if (delay > 0) sleeper.accept(delay);
    }
}
