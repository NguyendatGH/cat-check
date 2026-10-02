package com.catcheck.privacy.infrastructure.persistence;

import com.catcheck.privacy.domain.ConsentStatus;
import com.catcheck.privacy.domain.port.ConsentRecordPort;
import com.catcheck.privacy.domain.port.ConsentStatePort;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * {@link ConsentStatePort} trên {@code JdbcTemplate} — consent gate cho các module khác
 * (ví dụ bật push khi chưa đồng ý {@code HEALTH_REMINDER_PUSH} ⇒ p8
 * {@code CONSENT_REQUIRED}).
 *
 * <p>Không có consent nào được ghi thì trả {@code false} — mặc định TẮT, đúng tinh thần
 * "im lặng không phải là đồng ý" (p15 §15.3.1 C4).</p>
 */
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
