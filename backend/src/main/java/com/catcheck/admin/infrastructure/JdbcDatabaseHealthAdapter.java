package com.catcheck.admin.infrastructure;

import com.catcheck.admin.application.DatabaseHealthPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter tầng infrastructure: chứng minh app thật sự nói chuyện được với PostgreSQL thật (mục
 * tiêu M0 "app rỗng chạy được trên trình duyệt với DB thật") bằng một câu SELECT 1 tối giản,
 * không đụng tới bảng nghiệp vụ nào (M0 chưa có bảng nghiệp vụ).
 */
@Component
public class JdbcDatabaseHealthAdapter implements DatabaseHealthPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDatabaseHealthAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isDatabaseReachable() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (DataAccessException ex) {
            return false;
        }
    }
}
