package com.catcheck.admin.domain.port;

import com.catcheck.admin.domain.AdminMetrics;

public interface AdminMetricsQueryPort {
    AdminMetrics read();

    /** Số tài khoản theo {@code app_user.status} trên toàn bộ dữ liệu (chỉ số đếm, không PII). */
    java.util.Map<String, Long> countUsersByStatus();
}
