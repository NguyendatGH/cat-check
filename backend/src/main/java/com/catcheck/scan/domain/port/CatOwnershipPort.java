package com.catcheck.scan.domain.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc-only tối thiểu về hồ sơ mèo mà module {@code scan} cần để kiểm quyền sở hữu
 * (p8 §8.5.4 {@code CAT_NOT_OWNED}) và để hiển thị tên mèo trong response — KHÔNG phải cổng
 * nghiệp vụ đầy đủ của module {@code cat}.
 *
 * <p><b>Vì sao cổng này do CHÍNH {@code scan} tuyên bố và hiện thực (không đảo chiều qua
 * {@code cat.api} như {@code ChartCatalog}/{@code colorchart}):</b> module {@code cat} ở M0/M1
 * chưa công bố named interface "api" nào có query port đọc hồ sơ (chỉ event + DTO HTTP — xem
 * {@code docs/handovers/A3.md}). Thêm cổng vào {@code cat.api} nghĩa là sửa file thuộc module
 * {@code cat}, ngoài phạm vi sở hữu của A6. Hiện thực ở
 * {@code scan.infrastructure.persistence.JdbcCatOwnershipAdapter} đọc trực tiếp bảng {@code cat}
 * bằng JDBC thuần (không JPA entity, không import type Java nào của module {@code cat}) — đúng
 * tinh thần "UUID + cổng" của R6, chỉ khác là bên hiện thực không phải module sở hữu bảng. Xem
 * judgment call ở {@code docs/handovers/A6.md}.</p>
 */
public interface CatOwnershipPort {

    Optional<CatSnapshot> findSnapshot(UUID catId);

    /** Bản chụp tối thiểu — chỉ đủ cho response scan, KHÔNG phải toàn bộ hồ sơ mèo. */
    record CatSnapshot(UUID catId, UUID ownerId, String name, boolean archived, boolean deleted) {

        public boolean isOwnedBy(UUID userId) {
            return !deleted && ownerId.equals(userId);
        }
    }
}
