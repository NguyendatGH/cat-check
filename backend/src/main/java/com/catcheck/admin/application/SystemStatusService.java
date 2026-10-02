package com.catcheck.admin.application;

import com.catcheck.admin.domain.SystemStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Ví dụ minh hoạ tầng "application": điều phối domain + port, không tự gọi
 * {@code Instant.now()} (dùng {@link Clock} inject qua constructor — ArchUnit R13), không field
 * injection (constructor injection — ArchUnit R11).
 */
@Service
public class SystemStatusService {

    private final DatabaseHealthPort databaseHealthPort;
    private final Clock clock;

    public SystemStatusService(DatabaseHealthPort databaseHealthPort, Clock clock) {
        this.databaseHealthPort = databaseHealthPort;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SystemStatus currentStatus() {
        return new SystemStatus(databaseHealthPort.isDatabaseReachable(), clock.instant());
    }
}
