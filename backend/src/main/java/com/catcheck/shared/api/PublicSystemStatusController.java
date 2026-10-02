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

/**
 * K2 — {@code GET /api/v1/system/status}, CÔNG KHAI (p8 §8.4.11).
 *
 * <p><b>Vì sao nằm ở {@code shared} chứ không phải {@code admin}:</b> module {@code admin}
 * đã có {@code SystemStatusController} nhưng đó là endpoint quản trị
 * ({@code /api/v1/admin/system-status}) và toàn bộ {@code /api/v1/admin/**} nay bị chặn theo
 * role admin ở {@code SecurityConfig} — đặt một endpoint công khai vào đó thì hoặc bị chặn,
 * hoặc phải khoét lỗ trong luật phân quyền. Bảng nguồn {@code app_setting} cũng thuộc module
 * {@code shared} (V7__catalog.sql dòng 11, p7 §7.2.3), nên đây là chỗ đúng.</p>
 *
 * <p>Tên class có tiền tố {@code Public} để không trùng bean name với
 * {@code admin.api.SystemStatusController} (Spring sinh bean theo tên class đơn giản ⇒ trùng
 * tên là lỗi khởi động).</p>
 */
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "Trạng thái hệ thống công khai")
public class PublicSystemStatusController {

    private final PublicSystemStatusService systemStatusService;

    public PublicSystemStatusController(PublicSystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    /**
     * SPA gọi khi khởi động để quyết định có hiện banner bảo trì không. Cache ngắn (30s):
     * đủ để chịu tải lúc nhiều tab cùng mở, nhưng vẫn bật/tắt bảo trì gần như tức thì.
     */
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
