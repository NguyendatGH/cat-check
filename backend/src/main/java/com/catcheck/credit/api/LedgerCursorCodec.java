package com.catcheck.credit.api;

import com.catcheck.credit.domain.port.CreditLedgerQueryPort.LedgerCursor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/**
 * Mã hoá/giải mã con trỏ phân trang keyset — gói {@code (created_at, id)} thành một chuỗi
 * base64url để đưa lên URL.
 *
 * <p><b>Vì sao là con trỏ mà không phải offset:</b> {@code credit_ledger} là bảng chỉ INSERT, dữ
 * liệu mới liên tục chèn ở đầu danh sách. Offset sẽ làm bản ghi nhảy trang và lặp trang khi
 * người dùng đang cuộn (p8 §8.1.4). Con trỏ là ảnh chụp vị trí, nên dữ liệu chèn thêm không làm
 * sai trang đang xem.</p>
 *
 * <p>Định dạng: {@code <epochMilli>.<uuid>} rồi base64url không đệm. Cố ý <b>không</b> ký
 * cursor: nó chỉ chứa khoá sắp xếp, không chứa dữ liệu, và mọi truy vấn đều lọc theo
 * {@code user_id} của người đang đăng nhập — sửa cursor chỉ đổi vị trí bắt đầu, không đổi được
 * dữ liệu trả về. Dùng {@link #parse(String)} trả {@code null} thay vì ném, để cursor hỏng do
 * client tự dựng không biến thành 500.</p>
 */
final class LedgerCursorCodec {

    private static final String SEPARATOR = ".";

    private LedgerCursorCodec() {
    }

    /** Đóng gói khoá trang kế tiếp, hoặc {@code null} khi đã tới cuối danh sách. */
    static String encode(LedgerCursor cursor) {
        if (cursor == null) {
            return null;
        }
        String raw = cursor.sortKey().toEpochMilli() + SEPARATOR + cursor.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Giải mã con trỏ; {@code null} nếu rỗng hoặc hỏng (coi như trang đầu). */
    static LedgerCursor parse(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.indexOf(SEPARATOR);
            if (separator <= 0) {
                return null;
            }
            return new LedgerCursor(
                    Instant.ofEpochMilli(Long.parseLong(raw.substring(0, separator))),
                    UUID.fromString(raw.substring(separator + 1)));
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            return null;
        }
    }
}
