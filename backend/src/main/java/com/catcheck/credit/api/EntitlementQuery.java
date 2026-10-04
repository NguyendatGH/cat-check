package com.catcheck.credit.api;

import com.catcheck.credit.domain.Entitlement;

import java.util.UUID;

/**
 * <b>HỢP ĐỒNG ĐỌC QUYỀN — module {@code cat} (giới hạn hồ sơ mèo) và module khác cần dùng.</b>
 *
 * <p>Toàn bộ các phép kiểm tra đều <b>read-only, không khoá dòng nào</b> — chúng chạy ở bước
 * kiểm tra quyền trước khi tốn CPU hoặc ghi dữ liệu (p8 §8.3.1 bước 4c).</p>
 *
 * <p><b>Không bao giờ cache trong phiên</b> (p8 H4 / p11 §11.5.5): sau khi kích hoạt gói, quyền
 * phải có hiệu lực ngay ở lần gọi kế tiếp.</p>
 *
 * <p>Module gọi phải khai báo {@code allowedDependencies = {..., "credit::api"}}
 * trong {@code package-info.java} của mình (xem {@code docs/handovers/A4.md}).</p>
 */
public interface EntitlementQuery {

    /**
     * Quyền hiện tại, tạo dòng mặc định nếu chưa có. Tương đương
     * {@code GET /entitlements/me} (p8 H4).
     */
    Entitlement snapshot(UUID userId);

    /**
     * Số hồ sơ mèo tối đa theo gói hiện tại, {@code null} = không giới hạn (p5 §5.3).
     */
    Integer maxCatProfiles(UUID userId);

    /** Tính năng có mở không — quyền ĐỌC, giữ vĩnh viễn (p5 R5). */
    boolean isFeatureEnabled(UUID userId, Feature feature);

    /** Features callers may query without importing credit domain types. */
    enum Feature {
        HISTORY,
        TREND,
        REMINDER,
        EXPORT,
        STORE_IMAGE
    }

    /**
     * Còn quyền tạo MỚI (scan mới, tạo hồ sơ mèo, đặt reminder mới) hay không — quyền này
     * <b>có</b> hết hạn cùng credit, khác quyền đọc (p5 R5).
     */
    boolean hasWriteAccess(UUID userId);

    /**
     * Kiểm tra hạn mức hồ sơ mèo khi TẠO MỚI (bất biến I27).
     *
     * <p>Hạ gói KHÔNG xoá hồ sơ cũ: user từng có gói Multi tạo 4 hồ sơ rồi hạ xuống Daily thì 4
     * hồ sơ cũ vẫn còn, chỉ là không tạo thêm được (p5 R5, p17 C13).</p>
     *
     * <p>Hàm này <b>không</b> ném {@code CAT_PROFILE_LIMIT_REACHED}: mã lỗi đó thuộc danh mục
     * miền mèo (p8 §8.2.4(e)) nên do module {@code cat} ném, chỉ cần số ở đây.</p>
     *
     * @param currentCount số hồ sơ mèo chưa xoá hiện có của user
     * @return kết quả để module cat dựng thông điệp
     */
    CatProfileAllowance checkCatProfileAllowance(UUID userId, int currentCount);

    /**
     * Kết quả kiểm tra hạn mức hồ sơ mèo.
     *
     * @param allowed          tạo thêm được hay không
     * @param currentCount     số hồ sơ hiện có
     * @param maxCatProfiles   hạn mức theo gói, {@code null} = không giới hạn
     * @param currentPackage   gói hiện tại, {@code null} nếu chưa kích hoạt gói nào
     * @param requiredPackage  gói cần kích hoạt để mở thêm — tham số cho CTA
     */
    record CatProfileAllowance(
            boolean allowed,
            int currentCount,
            Integer maxCatProfiles,
            String currentPackage,
            String requiredPackage
    ) {
    }
}
