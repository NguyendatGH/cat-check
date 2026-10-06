package com.catcheck.credit.application.spi;

import java.util.OptionalInt;

/**
 * Cổng đọc bảng cấu hình {@code app_setting} — hai khoá trần an toàn của L10:
 * {@code credit.admin_adjust_max_per_operation} (mặc định 200) và
 * {@code credit.admin_adjust_max_per_day} (mặc định 1 000), p14 §14.4.4 bước 5.
 *
 * <p><b>Vì sao đặt trần trong DB thay vì hằng số trong mã:</b> vận hành phải siết được ngay khi
 * có sự cố lạm dụng, không chờ một lần build + deploy. Mặc định vẫn nằm trong mã để app chạy
 * được trên một DB chưa seed (khoá thiếu hoặc không phải số ⇒ dùng mặc định).</p>
 *
 * <p><b>Bản song song của {@code cat.application.spi.AppSettingPort}</b>, cố ý lặp lại: bảng
 * {@code app_setting} thuộc module {@code shared} về mặt sở hữu nghiệp vụ nhưng {@code shared}
 * chưa công bố cổng dùng chung, và {@code shared} nằm ngoài phạm vi sở hữu của gói việc này. Một
 * interface hai phương thức lặp lại còn hơn là sửa file thuộc module khác — cùng lập luận đã ghi
 * ở {@code credit.infrastructure.persistence.RowReaders}. Xem handoff H15.153.</p>
 */
public interface AppSettingPort {

    /**
     * @return giá trị số của khoá, hoặc rỗng nếu khoá không tồn tại / không phải {@code INT} /
     *         lưu sai định dạng — cả ba được xử lý như nhau: dùng mặc định trong mã
     */
    OptionalInt findInt(String key);
}
