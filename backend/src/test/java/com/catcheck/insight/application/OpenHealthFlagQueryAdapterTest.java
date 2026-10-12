package com.catcheck.insight.application;

import com.catcheck.insight.api.OpenHealthFlagQuery;
import com.catcheck.insight.domain.HealthFlag;
import com.catcheck.insight.domain.HealthFlagSeverity;
import com.catcheck.insight.domain.port.HealthFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OpenHealthFlagQueryAdapterTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CAT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-09T09:00:00Z");

    private RecordingHealthFlagRepository repository;
    private OpenHealthFlagQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = new RecordingHealthFlagRepository();
        adapter = new OpenHealthFlagQueryAdapter(repository);
    }

    @Test
    @DisplayName("Map rule_code/severity/triggeredAt thô, lọc theo acknowledged = false qua đúng userId/catId")
    void mapsOpenFlagsAndForwardsOwnershipFilter() {
        repository.page = new HealthFlagRepository.Page(List.of(
                new HealthFlag(UUID.randomUUID(), CAT_ID, "REPEATED_OUT_OF_RANGE", null,
                        HealthFlagSeverity.ATTENTION, NOW.minusSeconds(100), NOW, "key",
                        Map.of(), "giai thich", "dedupe-1", NOW)),
                null);

        List<OpenHealthFlagQuery.OpenFlag> flags = adapter.openFlagsFor(USER_ID, CAT_ID, 10);

        assertThat(flags).hasSize(1);
        assertThat(flags.getFirst().ruleCode()).isEqualTo("REPEATED_OUT_OF_RANGE");
        assertThat(flags.getFirst().severity()).isEqualTo("ATTENTION");
        assertThat(flags.getFirst().triggeredAt()).isEqualTo(NOW);
        assertThat(repository.lastUserId).isEqualTo(USER_ID);
        assertThat(repository.lastCatId).isEqualTo(CAT_ID);
        assertThat(repository.lastAcknowledged).isFalse();
    }

    @Test
    @DisplayName("limit được kẹp trong [1, 100] trước khi gọi repository")
    void boundsLimitBeforeDelegating() {
        repository.page = new HealthFlagRepository.Page(List.of(), null);

        adapter.openFlagsFor(USER_ID, CAT_ID, 0);
        assertThat(repository.lastLimit).isEqualTo(1);

        adapter.openFlagsFor(USER_ID, CAT_ID, 1000);
        assertThat(repository.lastLimit).isEqualTo(100);
    }

    private static final class RecordingHealthFlagRepository implements HealthFlagRepository {

        private HealthFlagRepository.Page page = new HealthFlagRepository.Page(List.of(), null);
        private UUID lastUserId;
        private UUID lastCatId;
        private Boolean lastAcknowledged;
        private int lastLimit;

        @Override
        public Optional<HealthFlag> saveIfNotDuplicate(HealthFlag flag) {
            throw new UnsupportedOperationException("not used by OpenHealthFlagQueryAdapter");
        }

        @Override
        public Optional<HealthFlag> findById(UUID id) {
            throw new UnsupportedOperationException("not used by OpenHealthFlagQueryAdapter");
        }

        @Override
        public Page findByFilter(UUID userId, UUID catId, Boolean acknowledged, String severity,
                                  String cursor, int limit) {
            this.lastUserId = userId;
            this.lastCatId = catId;
            this.lastAcknowledged = acknowledged;
            this.lastLimit = limit;
            return page;
        }

        @Override
        public HealthFlag save(HealthFlag flag) {
            throw new UnsupportedOperationException("not used by OpenHealthFlagQueryAdapter");
        }

        @Override
        public List<UUID> deleteByTriggerScanId(UUID scanId) {
            throw new UnsupportedOperationException("not used by OpenHealthFlagQueryAdapter");
        }

        @Override
        public boolean isCatOwnedByUser(UUID catId, UUID userId) {
            throw new UnsupportedOperationException("not used by OpenHealthFlagQueryAdapter");
        }

        @Override
        public List<HealthFlag> findByCatIdAndTriggeredAtBetween(UUID catId, Instant from, Instant to) {
            throw new UnsupportedOperationException("not used by OpenHealthFlagQueryAdapter");
        }
    }
}
