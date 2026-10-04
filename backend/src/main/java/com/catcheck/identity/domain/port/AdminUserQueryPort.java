package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.AdminUserSummary;
import com.catcheck.identity.domain.UserStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Truy vấn danh sách/chi tiết người dùng cho màn quản trị — L1, L2 (p8 §8.4.12 mục (a)).
 *
 * <p>Tách khỏi {@link UserAccountRepository}: cổng đó phục vụ đường nghiệp vụ của chính người
 * dùng (một dòng {@code app_user} theo id/email), còn cổng này làm việc khác về bản chất — lọc,
 * phân trang, và <b>đếm chéo bảng</b> ({@code cat}, {@code scan}, {@code user_entitlement}).
 *
 * <p><b>Lệch so với p7 đã ghi handoff H15.103:</b> ba bảng đó thuộc module {@code cat},
 * {@code scan}, {@code credit}; cách đúng là mỗi module mở một cổng đọc rồi {@code identity}
 * (hoặc một module {@code admin} tổng hợp) gọi qua. Ở gói việc này, ba module đó nằm ngoài vùng
 * file được phép sửa, nên adapter đọc trực tiếp bằng {@code LEFT JOIN LATERAL}. Không có ArchUnit
 * rule nào bắt được điều này (ranh giới Modulith tính theo type Java, không theo tên bảng), nên
 * nó phải nằm trong handoff.</p>
 */
public interface AdminUserQueryPort {

    /**
     * L1 — tìm/lọc, phân trang offset.
     *
     * @param status      lọc theo trạng thái; {@code null} = mọi trạng thái
     * @param email       khớp email <b>CHÍNH XÁC</b> (không tìm mờ). p14 §14.3.2 mục 2 nói rõ lý
     *                    do: <i>"hạn chế tra cứu tràn lan là một biện pháp chống dò quét nội
     *                    bộ"</i>. {@code null} = không lọc theo email
     * @param highestPackage lọc theo gói cao nhất; {@code null} = không lọc
     */
    List<AdminUserSummary> search(UserStatus status, String email, String highestPackage,
                                  int offset, int limit);

    long count(UserStatus status, String email, String highestPackage);

    /** L2 — một người dùng kèm các con số tổng hợp. */
    Optional<AdminUserSummary> findById(UUID userId);
}
