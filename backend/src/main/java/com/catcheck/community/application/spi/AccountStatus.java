package com.catcheck.community.application.spi;

/**
 * Sáu trạng thái vòng đời tài khoản, sao chép từ {@code app_user.status} (p4 §4.4.2).
 *
 * <p><b>Sao chép, không dùng chung kiểu.</b> Module {@code identity} sở hữu enum gốc, nhưng kiểu
 * domain không được đi qua biên module (p7 §7.3) — {@code community/package-info.java} chỉ cho
 * {@code allowedDependencies = { "shared", "audit::api" }}, không có {@code identity}. Đây CHÍNH
 * XÁC là kỹ thuật {@code cat.application.spi.AccountStatus} đã dùng (xem javadoc của lớp đó):
 * module này khai báo bản sao chỉ đủ dùng, và adapter ở
 * {@code community.infrastructure.persistence} đọc thẳng {@code app_user.status} bằng JDBC thô
 * thay vì import type của identity.</p>
 *
 * <p>Sáu giá trị này phải khớp CHECK constraint ở p4 §4.4.2; thêm/bớt giá trị là vi phạm hợp đồng
 * và DB sẽ từ chối bản ghi.</p>
 */
public enum AccountStatus {

    /** Chưa xác thực email — chặn đăng nhập. */
    PENDING_VERIFICATION,

    /** Bình thường, mọi thao tác được phép. */
    ACTIVE,

    /** Admin khoá tài khoản, thu hồi phiên ngay. */
    LOCKED,

    /** Người dùng yêu cầu hạn chế xử lý: CHỈ ĐỌC (p15 §15.4.7). */
    RESTRICTED,

    /** Đang trong 7 ngày ân hạn xoá tài khoản, không đăng nhập được. */
    DELETION_REQUESTED,

    /** Đã thực thi xoá, dữ liệu còn lại nhưng không nhận diện được ai. */
    ANONYMIZED;

    /**
     * Chiều ngược của {@code name()}.
     *
     * @throws IllegalArgumentException nếu DB có giá trị ngoài sáu giá trị trên — trường hợp này
     *                                  KHÔNG nên nuốt: nó có nghĩa schema lệch với mã nguồn, và nuốt
     *                                  đi sẽ biến lỗi cấu hình thành lỗi phân quyền
     */
    public static AccountStatus fromWire(String value) {
        if (value == null) {
            throw new IllegalArgumentException("account status không được null");
        }
        for (AccountStatus candidate : values()) {
            if (candidate.name().equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("account status không hợp lệ: " + value);
    }

    /**
     * I19 (p4 §4.4.9): tài khoản {@code RESTRICTED} không được xử lý gì ngoài lưu trữ — áp dụng
     * cho mọi luồng ghi của module community (đăng bài, bình luận, reaction, report).
     *
     * <p>Các trạng thái còn lại đều không vào được cổng ghi vì chúng đã bị chặn ở tầng phiên
     * (chưa đăng nhập / bị khoá / không đăng nhập được), nên ở đây chỉ cần phân biệt
     * "được ghi" với "bị hạn chế".</p>
     */
    public boolean allowsWrite() {
        return this == ACTIVE;
    }
}
