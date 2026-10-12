package com.catcheck.export.infrastructure.persistence;

import com.catcheck.export.domain.ExportJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private static ExportJob job() {
        return new ExportJob(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "CC-EXP-2026-123456",
                LocalDate.of(2026, 9, 4), LocalDate.of(2026, 10, 3), "30D",
                List.of("TREND"), "vi", "Asia/Ho_Chi_Minh", Instant.parse("2026-10-03T04:00:00Z"));
    }
}
