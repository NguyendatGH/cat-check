package com.catcheck.shared.job;

import java.time.Instant;
import java.util.UUID;

public record JobContext(UUID runId, String jobName, Instant startedAt, boolean dryRun) {

    public JobContext {
        if (runId == null || jobName == null || startedAt == null) {
            throw new IllegalArgumentException("jobContext thiếu trường bắt buộc");
        }
    }
}
