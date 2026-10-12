package com.catcheck.credit.application;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ActivationCsvVault {

    static final long TTL_MINUTES = 30;

    private static final Duration TTL = Duration.ofMinutes(TTL_MINUTES);

    private record Entry(String csv, Instant expiresAt) {
    }

    private final Map<String, Entry> byBatchId = new ConcurrentHashMap<>();
    private final Clock clock;

    public ActivationCsvVault(Clock clock) {
        this.clock = clock;
    }

    public void store(String batchId, String csv) {
        purgeExpired();
        byBatchId.put(batchId, new Entry(csv, clock.instant().plus(TTL)));
    }

    public Optional<String> drain(String batchId) {
        purgeExpired();
        Entry entry = byBatchId.remove(batchId);
        if (entry == null || entry.expiresAt().isBefore(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(entry.csv());
    }

    public boolean isAvailable(String batchId) {
        purgeExpired();
        return byBatchId.containsKey(batchId);
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        byBatchId.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }
}
