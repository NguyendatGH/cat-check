package com.catcheck.privacy.infrastructure.persistence;

import com.catcheck.privacy.domain.ConsentStatus;
import com.catcheck.privacy.domain.port.ConsentRecordPort;
import com.catcheck.privacy.domain.port.ConsentStatePort;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JdbcConsentStateAdapter implements ConsentStatePort {

    private final ConsentRecordPort recordPort;

    public JdbcConsentStateAdapter(ConsentRecordPort recordPort) {
        this.recordPort = recordPort;
    }

    @Override
    public boolean isGranted(UUID userId, String purposeCode) {
        return recordPort.findLatest(userId, purposeCode)
                .map(record -> record.status() == ConsentStatus.GRANTED)
                .orElse(false);
    }
}
