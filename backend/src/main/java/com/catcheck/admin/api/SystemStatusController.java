package com.catcheck.admin.api;

import com.catcheck.admin.api.dto.SystemStatusResponse;
import com.catcheck.admin.application.SystemStatusService;
import com.catcheck.admin.domain.SystemStatus;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ví dụ minh hoạ tầng "api" chạy xuyên suốt 4 layer của module admin — mục tiêu M0 là chứng
 * minh khung kiến trúc compile được và chạy thật (không phải business logic đầy đủ). Endpoint
 * này KHÔNG thay thế {@code /actuator/health} — nó chỉ minh hoạ pattern module.
 */
@RestController
public class SystemStatusController {

    private final SystemStatusService systemStatusService;

    public SystemStatusController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @Operation(operationId = "getAdminSystemStatus", summary = "Trạng thái kết nối DB (ví dụ minh hoạ khung module)")
    @GetMapping("/api/v1/admin/system-status")
    public SystemStatusResponse getSystemStatus() {
        SystemStatus status = systemStatusService.currentStatus();
        return new SystemStatusResponse(status.databaseReachable(), status.checkedAt());
    }
}
