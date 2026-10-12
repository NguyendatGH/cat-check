package com.catcheck.export.infrastructure.persistence;

import com.catcheck.export.domain.port.SubjectSnapshotPort;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcSubjectSnapshotAdapterTest {

    @Test
    @SuppressWarnings("unchecked")
    void softDeletedCatIsNotExported() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID catId = UUID.randomUUID();
        SubjectSnapshotPort.CatProfile softDeleted = new SubjectSnapshotPort.CatProfile(
                catId, UUID.randomUUID(), "Miu", null, "UNKNOWN", null, null, null, null);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(catId))).thenReturn(List.of(softDeleted));
        when(jdbc.query(contains("deleted_at IS NULL"), any(RowMapper.class), eq(catId))).thenReturn(List.of());

        assertThat(new JdbcSubjectSnapshotAdapter(jdbc).findCatProfile(catId)).isEmpty();
    }
}
