package com.catcheck.shared.api;

import com.catcheck.shared.api.dto.SystemStatusView;
import com.catcheck.shared.application.PublicSystemStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "Trạng thái hệ thống công khai")
public class PublicSystemStatusController {

    private final PublicSystemStatusService systemStatusService;

    public PublicSystemStatusController(PublicSystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @Operation(
            operationId = "getSystemStatus",
            summary = "Trạng thái hệ thống công khai",
            description = """
                    buildVersion, cờ bảo trì và cờ tính năng công khai. Không cần đăng nhập. \
                    Chưa cấu hình app_setting ⇒ maintenance.active = false, features rỗng, \
                    buildVersion = "unknown".""")
    @GetMapping("/status")
    public ResponseEntity<SystemStatusView> getSystemStatus() {
        SystemStatusView body = new SystemStatusView(
                systemStatusService.buildVersion(),
                new SystemStatusView.MaintenanceView(
                        systemStatusService.maintenanceActive(),
                        systemStatusService.maintenanceUntil()),
                systemStatusService.publicFeatureFlags());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .body(body);
    }
}
