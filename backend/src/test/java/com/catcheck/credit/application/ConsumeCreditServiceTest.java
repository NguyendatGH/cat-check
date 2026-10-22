package com.catcheck.credit.application;

import com.catcheck.credit.api.CreditConsumption;
import com.catcheck.credit.api.CreditConsumption.CreditCharge;
import com.catcheck.credit.api.CreditConsumption.CreditConsumeCommand;
import com.catcheck.credit.api.CreditConsumption.ReferenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumeCreditServiceTest {

    private CreditConsumption creditConsumption;
    private ConsumeCreditService service;

    @BeforeEach
    void setUp() {
        creditConsumption = mock(CreditConsumption.class);
        service = new ConsumeCreditService(creditConsumption);
    }

    @Test
    void consumeDispatchesCommandWithJobRefType() {
        UUID userId = UUID.randomUUID();
        when(creditConsumption.consume(eq(userId), any(CreditConsumeCommand.class)))
                .thenReturn(new CreditCharge(1, 9, List.of()));

        CreditCharge result = service.consume(userId, 1, "test-idem-key", "AI Assistant test");

        assertEquals(1, result.chargedCredits());
        assertEquals(9, result.balanceAfter());

        ArgumentCaptor<CreditConsumeCommand> captor = ArgumentCaptor.forClass(CreditConsumeCommand.class);
        verify(creditConsumption).consume(eq(userId), captor.capture());

        CreditConsumeCommand cmd = captor.getValue();
        assertEquals(1, cmd.credits());
        assertEquals("test-idem-key", cmd.idempotencyKey());
        assertEquals(ReferenceType.JOB, cmd.refType());
        assertNotNull(cmd.refId());
        assertEquals("AI Assistant test", cmd.note());
    }

    @Test
    void consumeGeneratesIdempotencyKeyIfMissing() {
        UUID userId = UUID.randomUUID();
        when(creditConsumption.consume(eq(userId), any(CreditConsumeCommand.class)))
                .thenReturn(new CreditCharge(1, 5, List.of()));

        service.consume(userId, 1, null, null);

        ArgumentCaptor<CreditConsumeCommand> captor = ArgumentCaptor.forClass(CreditConsumeCommand.class);
        verify(creditConsumption).consume(eq(userId), captor.capture());

        CreditConsumeCommand cmd = captor.getValue();
        assertNotNull(cmd.idempotencyKey());
        assertEquals("AI Chat consumption", cmd.note());
    }
}
