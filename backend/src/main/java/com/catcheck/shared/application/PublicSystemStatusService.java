package com.catcheck.shared.application;

import com.catcheck.shared.application.spi.AppSettingReader;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Nguồn dữ liệu cho K2 {@code GET /system/status} (p8 §8.4.11).
 *
 * <p>Mọi giá trị đọc từ {@code app_setting} và đều CÓ MẶC ĐỊNH AN TOÀN: bảng này hiện rỗng
 * (seed thuộc file {@code R__} của W3, xem V7__catalog.sql dòng 19) nên endpoint phải chạy
 * được ngay cả khi chưa cấu hình khoá nào — không cấu hình = không bảo trì.</p>
 *
 * <p>Không {@code @Transactional}: chỉ đọc, và một request công khai không cần bọc giao dịch.</p>
 */
@Service
public class PublicSystemStatusService {

    /** p4 dòng 1216: build version của app nằm ở {@code app_setting}, không hard-code. */
    static final String KEY_BUILD_VERSION = "app.build_version";
    static final String KEY_MAINTENANCE_ACTIVE = "app.maintenance_active";
    static final String KEY_MAINTENANCE_UNTIL = "app.maintenance_until";

    /** Namespace cờ tính năng công khai; khoá {@code secret = true} đã bị cổng đọc loại bỏ. */
    static final String NAMESPACE_FEATURE = "feature";

    /** Chưa cấu hình {@code app.build_version} thì nói thẳng là chưa biết, không bịa số. */
    static final String UNKNOWN_VERSION = "unknown";

    private final AppSettingReader appSettingReader;

    public PublicSystemStatusService(AppSettingReader appSettingReader) {
        this.appSettingReader = appSettingReader;
    }

    public String buildVersion() {
        return appSettingReader.findPublicValue(KEY_BUILD_VERSION)
                .filter(v -> !v.isBlank())
                .orElse(UNKNOWN_VERSION);
    }

    /** Mặc định KHÔNG bảo trì — thiếu cấu hình không được vô tình chặn cả app. */
    public boolean maintenanceActive() {
        return appSettingReader.findPublicValue(KEY_MAINTENANCE_ACTIVE)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    /** Mốc dự kiến xong; null khi không đặt (hoặc đặt rỗng). */
    public String maintenanceUntil() {
        return appSettingReader.findPublicValue(KEY_MAINTENANCE_UNTIL)
                .filter(v -> !v.isBlank())
                .orElse(null);
    }

    public Map<String, String> publicFeatureFlags() {
        return appSettingReader.findPublicNamespace(NAMESPACE_FEATURE);
    }
}
