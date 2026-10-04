package com.catcheck.shared.api;

import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.security.AdminApiErrorCode;

import java.time.Instant;

/**
 * {@code ETag} + {@code If-Match} cho các endpoint ghi cấu hình admin — p8 §8.1.11.
 *
 * <p>Định dạng {@code W/"<updatedAt epoch ms>-<khoá>"}, giống hệt {@code colorchart.api.ETag}
 * (lớp đó package-private nên không dùng lại được từ module khác). Dùng weak validator vì
 * {@code updated_at} chính xác tới millisecond: hai lần ghi trong cùng một millisecond cho cùng
 * một ETag, điều mà strong validator sẽ coi là khác nhau một cách oan uổng.</p>
 */
public final class AdminETag {

    private AdminETag() {
    }

    /**
     * @param updatedAt {@code updated_at} của dòng; {@code null} (bảng chưa có trigger ghi) thì
     *                  quy về epoch 0 để ETag vẫn ổn định thay vì ném lỗi
     * @param key       khoá tự nhiên hoặc id của dòng
     */
    public static String of(Instant updatedAt, String key) {
        long epochMilli = updatedAt == null ? 0L : updatedAt.toEpochMilli();
        return "W/\"" + epochMilli + "-" + key + "\"";
    }

    /**
     * @throws BusinessRuleException {@code 428 PRECONDITION_REQUIRED} nếu thiếu header,
     *                               {@code 412 RESOURCE_MODIFIED} nếu không khớp
     */
    public static void requireMatch(String ifMatch, String expected) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new BusinessRuleException(AdminApiErrorCode.PRECONDITION_REQUIRED);
        }
        String normalized = ifMatch.strip();
        if (!normalized.equals(expected) && !normalized.equals("*")) {
            throw new BusinessRuleException(AdminApiErrorCode.RESOURCE_MODIFIED, expected);
        }
    }
}
