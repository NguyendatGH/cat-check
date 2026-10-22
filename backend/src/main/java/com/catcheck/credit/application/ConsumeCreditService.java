package com.catcheck.credit.application;

import com.catcheck.credit.api.CreditConsumption;
import com.catcheck.credit.api.CreditConsumption.CreditCharge;
import com.catcheck.credit.api.CreditConsumption.CreditConsumeCommand;
import com.catcheck.credit.api.CreditConsumption.ReferenceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ConsumeCreditService {

    private final CreditConsumption creditConsumption;

    public ConsumeCreditService(CreditConsumption creditConsumption) {
        this.creditConsumption = creditConsumption;
    }

    @Transactional
    public CreditCharge consume(UUID userId, int credits, String idempotencyKey, String note) {
        String key = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey
                : UUID.randomUUID().toString();
        String usageNote = (note != null && !note.isBlank()) ? note : "AI Chat consumption";

        return creditConsumption.consume(
                userId,
                new CreditConsumeCommand(credits, key, ReferenceType.JOB, UUID.randomUUID(), usageNote)
        );
    }
}
