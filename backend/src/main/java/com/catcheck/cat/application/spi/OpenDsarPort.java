package com.catcheck.cat.application.spi;

import java.util.UUID;

/**
 * Có yêu cầu DSAR nào đang mở gắn với một người dùng không — điều kiện <b>duy nhất</b> cho phép
 * {@code DPO} xem hồ sơ mèo ở dạng đầy đủ (p8 L4: <i>"dữ liệu che; DPO thấy đầy đủ khi có DSAR
 * mở"</i>; p14 §14.2.2 ô Q3; p11 §11.5.4 dòng "Hồ sơ mèo").
 *
 * <p><b>Vì sao cổng này do chính {@code cat} tuyên bố thay vì gọi module {@code privacy}:</b>
 * cùng lý do đã ghi ở {@link AppSettingPort} và ở {@code scan.domain.port.CatOwnershipPort} —
 * module {@code privacy} chưa công bố named interface nào đọc được trạng thái
 * {@code dsar_request}, và thêm cổng vào {@code privacy.api} nghĩa là sửa file thuộc module
 * khác. Bản hiện thực ({@code cat.infrastructure.persistence.JdbcOpenDsarAdapter}) đọc bảng
 * {@code dsar_request} bằng JDBC thuần, read-only, KHÔNG import type Java nào của
 * {@code privacy}. Bản song song tồn tại ở {@code scan.application.spi} — xem handoff H15.153.</p>
 */
public interface OpenDsarPort {

    /**
     * @param userId chủ thể dữ liệu
     * @return {@code true} nếu có ít nhất một {@code dsar_request} của người này ở trạng thái
     *         chưa kết thúc ({@code status NOT IN ('COMPLETED','REJECTED')})
     */
    boolean hasOpenRequestFor(UUID userId);
}
