package com.catcheck.privacy.application.privacy;

import com.catcheck.privacy.domain.port.UserActivityLogPort;
import com.catcheck.privacy.spi.ErasureParticipant;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PrivacyErasureParticipant implements ErasureParticipant {

    public static final String NAME = "privacy";

    private final UserActivityLogPort activityLogPort;

    public PrivacyErasureParticipant(UserActivityLogPort activityLogPort) {
        this.activityLogPort = activityLogPort;
    }

    @Override
    public String participantName() {
        return NAME;
    }

    @Override
    public void erase(UUID subjectId) {
        activityLogPort.deleteByUser(subjectId);
    }
}
