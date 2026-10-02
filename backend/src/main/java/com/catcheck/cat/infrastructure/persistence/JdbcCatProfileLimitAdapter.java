package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.application.spi.CatProfileLimit;
import com.catcheck.cat.application.spi.CatProfileLimitPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link CatProfileLimitPort} đọc trực tiếp {@code user_entitlement.max_cat_profiles} bằng
 * {@code JdbcTemplate} — cùng lý do kiến trúc với {@code JdbcUserAccountPortAdapter} (xem javadoc
 * lớp đó): cổng SPI này thuộc {@code cat.application.spi}, và module {@code credit} (chủ sở hữu
 * thật của {@code user_entitlement}, {@code V10__credit.sql}) chưa expose named interface đọc hạn
 * mức. Đọc thẳng bằng SQL, KHÔNG import type nào của {@code com.catcheck.credit.*}.
 *
 * <p><b>Đơn giản hoá có chủ đích:</b> {@link CatProfileLimit#requiredPackageCode()} luôn trả
 * {@code null} ở đây — tính "gói nào cần nâng lên để có hạn mức cao hơn" đòi hỏi so sánh
 * {@code package_plan.max_cat_profiles} giữa các gói, là nghiệp vụ thật sự của module credit
 * (A4), không phải việc suy luận từ cat. {@code CatProfileService} đã tự xử lý {@code null} này
 * bằng cách rơi về CTA "SUPPORT" (xem {@code CatProfileService.assertWithinLimits}). Khi credit
 * expose {@code credit.api} có port đọc hạn mức kèm gói đề xuất, thay adapter này bằng một cổng
 * gọi thẳng port đó — không cần đổi chữ ký {@link CatProfileLimitPort}.</p>
 *
 * <p>{@code max_cat_profiles IS NULL} (hoặc không có dòng) nghĩa là "gói không giới hạn thêm" theo
 * đúng chú thích của {@code package_plan.max_cat_profiles} trong {@code V7__catalog.sql}
 * ("NULL = không giới hạn") — ánh xạ thành {@link Optional#empty()} để chỉ còn trần cứng áp dụng,
 * đúng hợp đồng javadoc của {@link CatProfileLimitPort#limitFor(UUID)}.</p>
 */
@Repository
class JdbcCatProfileLimitAdapter implements CatProfileLimitPort {

    private final JdbcTemplate jdbc;

    JdbcCatProfileLimitAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CatProfileLimit> limitFor(UUID ownerId) {
        List<Integer> rows = jdbc.query(
                "SELECT max_cat_profiles FROM user_entitlement WHERE user_id = ?",
                (rs, rowNum) -> {
                    int value = rs.getInt("max_cat_profiles");
                    return rs.wasNull() ? null : value;
                },
                ownerId);
        if (rows.isEmpty() || rows.getFirst() == null) {
            return Optional.empty();
        }
        return Optional.of(new CatProfileLimit(rows.getFirst(), null));
    }
}
