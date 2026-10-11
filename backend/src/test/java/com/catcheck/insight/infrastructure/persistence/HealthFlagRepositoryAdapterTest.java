package com.catcheck.insight.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthFlagRepositoryAdapterTest {

    @Test
    void softDeletedCatIsNotTreatedAsOwnedWhenAcknowledgingOrViewingAFlag() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID catId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(jdbc.queryForObject(contains("deleted_at IS NULL"), eq(Long.class), eq(catId), eq(userId)))
                .thenReturn(0L);
        HealthFlagRepositoryAdapter repository = new HealthFlagRepositoryAdapter(
                mock(HealthFlagJpaRepository.class), jdbc, null,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        assertFalse(repository.isCatOwnedByUser(catId, userId));
    }
}
