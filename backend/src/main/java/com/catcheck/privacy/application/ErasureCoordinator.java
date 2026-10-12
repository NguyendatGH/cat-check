package com.catcheck.privacy.application;

import com.catcheck.privacy.domain.port.UserActivityLogPort;
import com.catcheck.privacy.spi.ErasureParticipant;
import com.catcheck.privacy.spi.UserAccountPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ErasureCoordinator {

    private final List<ErasureParticipant> participants;
    private final UserActivityLogPort activityLogPort;
    private final UserAccountPort userAccountPort;

    public ErasureCoordinator(
            List<ErasureParticipant> participants,
            UserActivityLogPort activityLogPort,
            UserAccountPort userAccountPort
    ) {
        this.participants = participants;
        this.activityLogPort = activityLogPort;
        this.userAccountPort = userAccountPort;
    }

    @Transactional
    public void erase(UUID subjectId) {
        activityLogPort.deleteByUser(subjectId);
        for (ErasureParticipant participant : participants) {
            participant.erase(subjectId);
        }
    }

    public int participantCount() {
        return participants.size();
    }
}
