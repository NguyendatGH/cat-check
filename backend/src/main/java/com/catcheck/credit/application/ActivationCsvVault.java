package com.catcheck.credit.application;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giữ mã thô của một lô vừa sinh <b>trong bộ nhớ</b>, cho đúng một lần đọc — L22
 * {@code GET /admin/activation-codes/batches/{batchId}/csv}, <i>"một lần duy nhất, lần hai ⇒
 * {@code 410 ACTIVATION_CSV_ALREADY_DOWNLOADED}"</i>.
 *
 * <p><b>Vì sao trong bộ nhớ chứ không trong DB:</b> p5 §5.9 + p14 §14.3.2 mục 4 nói mã thô
 * <i>"sinh ra trong bộ nhớ, ghi thẳng vào file CSV trả về client... không lưu file mã thô lâu
 * dài trên server"</i>, và {@code V10__credit.sql} cố ý không có cột nào cho mã thô hay cho cờ
 * "đã tải CSV" (xem javadoc {@link ActivationCodeIssuanceService}). Thêm cột nghĩa là thêm
 * migration ngoài danh mục p4 §4.9.2 — CLAUDE.md cấm. Ghi mã thô xuống DB còn phá chính tính
 * chất khiến HMAC + pepper có giá trị: một bản dump DB lại chứa mã dùng được.</p>
 *
 * <p><b>Hệ quả đã biết và chấp nhận</b> (handoff H15.97): backend khởi động lại thì mã thô mất,
 * và trong triển khai nhiều instance thì CSV chỉ tải được từ instance đã sinh lô. Cả hai trả
 * cùng {@code 410} với thông điệp đã nói rõ đường ra (void lô, sinh lô mới) — đúng hệ quả mà
 * p14 đã cảnh báo người vận hành <b>trước khi</b> bấm "Sinh mã". MVP chạy một instance.</p>
 *
 * <p>TTL {@value #TTL_MINUTES} phút là lưới an toàn thứ hai: một lô bị sinh rồi admin đóng tab
 * không được phép giữ 50 000 mã dùng được trong heap đến hết đời tiến trình.</p>
 */
@Component
public class ActivationCsvVault {

    static final long TTL_MINUTES = 30;

    private static final Duration TTL = Duration.ofMinutes(TTL_MINUTES);

    private record Entry(String csv, Instant expiresAt) {
    }

    private final Map<String, Entry> byBatchId = new ConcurrentHashMap<>();
    private final Clock clock;

    public ActivationCsvVault(Clock clock) {
        this.clock = clock;
    }

    /** Nhận nội dung CSV của một lô vừa sinh. Ghi đè nếu trùng khoá (không xảy ra: batchId duy nhất). */
    public void store(String batchId, String csv) {
        purgeExpired();
        byBatchId.put(batchId, new Entry(csv, clock.instant().plus(TTL)));
    }

    /**
     * Lấy <b>và xoá</b> nội dung CSV. Lần gọi thứ hai luôn rỗng — đó là toàn bộ cơ chế
     * "một lần duy nhất", và nó nằm ở {@code remove()} nguyên tử của
     * {@link ConcurrentHashMap} chứ không ở một cờ boolean đọc-rồi-ghi (hai request bấm cùng lúc
     * sẽ cùng thắng với cờ).
     */
    public Optional<String> drain(String batchId) {
        purgeExpired();
        Entry entry = byBatchId.remove(batchId);
        if (entry == null || entry.expiresAt().isBefore(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(entry.csv());
    }

    /** CSV của lô này còn tải được không — cột {@code csvAvailable} của L21. */
    public boolean isAvailable(String batchId) {
        purgeExpired();
        return byBatchId.containsKey(batchId);
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        byBatchId.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }
}
