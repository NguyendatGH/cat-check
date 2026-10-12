package com.catcheck.privacy.application;

import com.catcheck.privacy.domain.UserActivityLog;
import com.catcheck.privacy.domain.port.ConsentRecordPort;
import com.catcheck.privacy.domain.port.UserActivityLogPort;
import com.catcheck.privacy.spi.UserAccountPort;
import com.catcheck.privacy.spi.UserAccountSnapshot;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ActivityLogService {

    public static final String PURPOSE_PRODUCT_ANALYTICS = "PRODUCT_ANALYTICS";

    private final UserActivityLogPort logPort;
    private final ConsentRecordPort consentPort;
    private final UserAccountPort userAccountPort;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public ActivityLogService(
            UserActivityLogPort logPort,
            ConsentRecordPort consentPort,
            UserAccountPort userAccountPort,
            UuidV7 uuidV7,
            Clock clock
    ) {
        this.logPort = logPort;
        this.consentPort = consentPort;
        this.userAccountPort = userAccountPort;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    @Transactional
    public boolean record(UUID userId, String eventCode, Map<String, Object> props) {
        UserAccountSnapshot snapshot = userAccountPort.snapshot(userId);
        if (snapshot.isProcessingRestricted() || snapshot.isAnonymized()) {
            return false;
        }
        boolean granted = consentPort.findLatest(userId, PURPOSE_PRODUCT_ANALYTICS)
                .map(record -> record.status() == com.catcheck.privacy.domain.ConsentStatus.GRANTED)
                .orElse(false);
        if (!granted) {
            return false;
        }
        Instant now = clock.instant();
        logPort.append(new UserActivityLog(uuidV7.generate(), userId, eventCode, props, now, now));
        return true;
    }

    @Transactional
    public int eraseByUser(UUID userId) {
        return logPort.deleteByUser(userId);
    }
}
