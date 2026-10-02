package com.catcheck.cat.application.spi;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng hỏi giới hạn số hồ sơ mèo theo gói, do module {@code credit} hiện thực.
 *
 * <p>C31 chốt hai lớp giới hạn: <b>entitlement theo gói</b> (lớp này) và <b>trần cứng</b> trong
 * {@link AppSettingPort}. Hai lớp khác nhau về ý nghĩa: entitlement là quyền đã mua nên khi vượt thì
 * CTA là "nâng gói"; trần cứng là giới hạn kỹ thuật CatCheck chấp nhận nên khi vượt thì CTA là "liên
 * hệ hỗ trợ". Kiểm đúng thứ tự, không thì người dùng bị đẩy vào ngõ cụt.</p>
 */
public interface CatProfileLimitPort {

    /**
     * @return hạn mức theo gói, hoặc {@link Optional#empty()} nếu chưa xác định được (module credit
     *         chưa sẵn sàng, hoặc tài khoản không có gói nào) — khi đó chỉ áp trần cứng
     */
    Optional<CatProfileLimit> limitFor(UUID ownerId);
}
