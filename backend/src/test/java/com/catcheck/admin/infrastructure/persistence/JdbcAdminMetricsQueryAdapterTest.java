package com.catcheck.admin.infrastructure.persistence;

import com.catcheck.admin.domain.AdminMetrics;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class JdbcAdminMetricsQueryAdapterTest {

    @Test
    void activeCatsExcludesSoftDeletedCats() {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        Mockito.when(jdbc.queryForObject(anyString(), eq(Long.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (!sql.contains("FROM cat ")) {
                return 0L;
            }
            long liveActive = 1;
            long softDeletedActive = 1;
            return sql.contains("deleted_at IS NULL") ? liveActive : liveActive + softDeletedActive;
        });

        AdminMetrics metrics = new JdbcAdminMetricsQueryAdapter(jdbc).read();

        assertThat(metrics.activeCats()).isEqualTo(1);
    }
}
