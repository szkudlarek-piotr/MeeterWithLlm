package org.example.meeter.auth;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LoginThrottle {
    private record Attempts(int count, Instant reset) {}
    private final Map<String, Attempts> attempts = new HashMap<>();

    public synchronized void check(String username, String address) {
        Instant now = Instant.now();
        attempts.entrySet().removeIf(entry -> entry.getValue().reset().isBefore(now));
        if (attempts.size() >= 10000) reject();
        increment("ip:" + address, 100, now);
        increment("user:" + username, 5, now);
    }
    private void increment(String key, int limit, Instant now) {
        var previous = attempts.getOrDefault(key, new Attempts(0, now.plusSeconds(900)));
        if (previous.count() >= limit) reject();
        attempts.put(key, new Attempts(previous.count() + 1, previous.reset()));
    }
    public synchronized void success(String username) { attempts.remove("user:" + username); }
    private static void reject() { throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Za dużo prób. Spróbuj ponownie za 15 minut."); }
}
