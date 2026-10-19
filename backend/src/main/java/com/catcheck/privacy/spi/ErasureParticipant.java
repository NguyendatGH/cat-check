package com.catcheck.privacy.spi;

import java.util.UUID;

public interface ErasureParticipant {

    String participantName();

    void erase(UUID subjectId);
}
