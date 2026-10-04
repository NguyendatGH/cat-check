package com.catcheck.admin.domain.port;

import com.catcheck.admin.domain.AdminMetrics;

public interface AdminMetricsQueryPort {
    AdminMetrics read();
}
