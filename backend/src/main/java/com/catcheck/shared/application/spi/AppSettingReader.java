package com.catcheck.shared.application.spi;

import java.util.Map;
import java.util.Optional;

/**
 * Đọc {@code app_setting} — key/value store cấu hình runtime.
 *
 * <p>Bảng này thuộc module {@code shared} (V7__catalog.sql dòng 11, p7 §7.2.3). Module khác
 * KHÔNG query thẳng mà đi qua cổng SPI riêng của mình — xem
 * {@code cat.application.spi.AppSettingPort}.</p>
 *
 * <p><b>Chỉ đọc.</b> Ghi cấu hình là việc của admin panel (W3), không thuộc phạm vi cổng này.</p>
 */
public interface AppSettingReader {

    /** Giá trị thô của một khoá, rỗng nếu chưa cấu hình. Không bao giờ trả khoá {@code secret}. */
    Optional<String> findPublicValue(String key);

    /**
     * Mọi khoá công khai (secret = false) thuộc một namespace, ví dụ {@code "feature"}.
     *
     * @return map {tên khoá sau dấu chấm → giá trị thô}; rỗng nếu chưa cấu hình khoá nào
     */
    Map<String, String> findPublicNamespace(String namespacePrefix);
}
