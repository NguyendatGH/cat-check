package com.catcheck.scan.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcScanQueryRepositoryRuleOrderTest {

    @Test
    @SuppressWarnings("unchecked")
    void ruleLookbackBreaksCapturedAtTiesById() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        JdbcScanQueryRepository repository = new JdbcScanQueryRepository(jdbc, JsonMapper.builder().build());

        repository.findRecentForRules(UUID.randomUUID(), Instant.parse("2026-10-01T00:00:00Z"), 10);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue()).contains("ORDER BY s.captured_at DESC, s.id DESC");
    }
}
