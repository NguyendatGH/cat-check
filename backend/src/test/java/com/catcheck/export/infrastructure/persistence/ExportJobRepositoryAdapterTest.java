package com.catcheck.export.infrastructure.persistence;

import com.catcheck.export.domain.ExportJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExportJobRepositoryAdapterTest {

    @Test
    @DisplayName("save flush ngay nên vi phạm unique I25 nổ bên trong try của request()")
    void saveSurfacesUniqueViolationImmediately() {
        ExportJobJpaRepository jpa = mock(ExportJobJpaRepository.class);
        when(jpa.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jpa.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("uq_export_job_one_active_per_user"));
        ExportJobRepositoryAdapter adapter = new ExportJobRepositoryAdapter(jpa, null);

        assertThatThrownBy(() -> adapter.save(job()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("M24 — limit = 100 (trần) vẫn lấy dư 1 dòng sentinel và trả nextCursor")
    @SuppressWarnings("unchecked")
    void maxPageSizeStillProducesNextCursor() {
        ExportJobJpaRepository jpa = mock(ExportJobJpaRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<UUID> ids = IntStream.range(0, 101).mapToObj(i -> UUID.randomUUID()).toList();
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        when(jdbc.query(anyString(), any(RowMapper.class), args.capture())).thenReturn((List) ids);
        when(jpa.findById(any())).thenAnswer(inv -> Optional.of(job()));
        ExportJobRepositoryAdapter adapter = new ExportJobRepositoryAdapter(jpa, jdbc);

        var page = adapter.findByUser(UUID.randomUUID(), null, 100);

        Object[] sqlArgs = args.getValue();
        assertThat(sqlArgs[sqlArgs.length - 1]).as("LIMIT = limit + 1").isEqualTo(101);
        assertThat(page.items()).hasSize(100);
        assertThat(page.nextCursor()).isNotNull();
    }

    private static ExportJob job() {
        return new ExportJob(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CC-EXP-2026-123456",
                LocalDate.of(2026, 9, 4), LocalDate.of(2026, 10, 3), "30D",
                List.of("TREND"), "vi", "Asia/Ho_Chi_Minh", Instant.parse("2026-10-03T04:00:00Z"));
    }
}
