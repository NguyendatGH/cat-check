package com.catcheck.admin.application;

import com.catcheck.admin.domain.SystemStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

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
