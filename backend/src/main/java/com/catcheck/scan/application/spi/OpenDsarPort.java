package com.catcheck.scan.application.spi;

import java.util.UUID;

/**
 * Có yêu cầu DSAR nào đang mở gắn với một người dùng không — điều kiện <b>duy nhất</b> cho phép
 * {@code DPO} xem ảnh scan (p8 L6, p14 §14.2.2 ô Q5, p11 §11.5.4 dòng "Ảnh scan").
 *
 * <p><b>Vì sao cổng này do chính {@code scan} tuyên bố thay vì gọi module {@code privacy}:</b>
 * cùng lý do đã ghi ở {@code scan.domain.port.CatOwnershipPort} — module {@code privacy} chưa
 * công bố named interface nào đọc được trạng thái {@code dsar_request}, và thêm một cổng vào
 * {@code privacy.api} nghĩa là sửa file thuộc module khác. Bản hiện thực
 * ({@code scan.infrastructure.persistence.JdbcOpenDsarAdapter}) đọc bảng {@code dsar_request}
 * bằng JDBC thuần, read-only, KHÔNG import type Java nào của {@code privacy}.</p>
 *
 * <p>Đặt ở {@code application.spi} chứ không ở {@code domain.port}: đây là một <i>điều kiện
 * phân quyền</i> của use case admin, không phải khái niệm của miền quét ảnh.</p>
 */
public interface OpenDsarPort {

    /**
     * @param userId chủ thể dữ liệu
     * @return {@code true} nếu có ít nhất một {@code dsar_request} của người này ở trạng thái
     *         chưa kết thúc ({@code status NOT IN ('COMPLETED','REJECTED')})
     */
    boolean hasOpenRequestFor(UUID userId);
}
