package com.catcheck.cat.application.spi;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * Cổng đọc bảng cấu hình {@code app_setting}, do module sở hữu bảng đó hiện thực.
 *
 * <p>C31: {@code cat.max_per_user} là TRẦN CỨNG, mặc định 8, độc lập với gói cước. Đặt trong DB
 * thay vì hằng số trong mã để vận hành có thể siết mà không cần build lại.</p>
 */
public interface AppSettingPort {

    /**
     * @return giá trị số của khoá, hoặc rỗng nếu khoá không tồn tại hoặc không phải số — cả hai đều
     *         được xử lý như nhau: dùng mặc định trong mã và ghi log cảnh báo
     */
    OptionalInt findInt(String key, UUID requesterId);
}
