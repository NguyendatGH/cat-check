package com.catcheck.credit.domain.port;

import com.catcheck.credit.domain.Entitlement;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc/ghi {@code user_entitlement} — bảng dẫn xuất 1-1 với {@code app_user}
 * (p4 §4.1.1: "một user một dòng" là bất biến của DB chứ không phải của code).
 *
 * <p><b>Phân biệt read-only với ghi là cố ý.</b> {@link #findOrDefault} thuần đọc, nên an toàn
 * trong transaction {@code readOnly = true}; {@link #ensureRow} mới ghi. Nếu gộp hai việc thành
 * một {@code findOrCreate} thì mọi màn hình chỉ đọc quyền cũng phải mở transaction ghi, và trên
 * PostgreSQL {@code INSERT} trong transaction read-only sẽ ném lỗi.</p>
 */
public interface UserEntitlementPort {

    Optional<Entitlement> find(UUID userId);

    /**
     * Đọc entitlement, trả về dòng mặc định <b>trong bộ nhớ</b> nếu chưa có dòng. Thuần đọc.
     *
     * <p>Không có dòng và có dòng mặc định là cùng một trạng thái nghiệp vụ, nên trả về giá trị mặc
     * định còn hơn là ghi: user mới chưa kích hoạt gói nào thì chưa kích hoạt, quyền bằng
     * {@link com.catcheck.credit.domain.PlanFeatures#none()}, {@code trial_scans_used = 0}
     * (p5 R6 — 3 lượt trial, tính theo tài khoản, không theo lô, không hết hạn). Dòng thật sẽ được
     * tạo ở lần ghi đầu tiên (kích hoạt gói, hoặc tiêu lượt trial).</p>
     */
    Entitlement findOrDefault(UUID userId, Instant now);

    /**
     * Tạo dòng mặc định nếu chưa có — idempotent, chạy trước mọi thao tác ghi lên
     * {@code user_entitlement}.
     */
    void ensureRow(UUID userId, Instant now);

    /**
     * Ghi đè toàn bộ dòng entitlement — dùng sau khi kích hoạt gói (p5 R1/R5). Upsert nên gọi
     * được cả khi dòng chưa tồn tại.
     */
    void save(Entitlement entitlement);

    /**
     * Tăng {@code trial_scans_used} đúng một lần, trả về {@code true} nếu tăng thành công.
     *
     * <p>Phải là một câu {@code UPDATE ... SET trial_scans_used = trial_scans_used + 1} có điều
     * kiện số lượt còn lại, KHÔNG đọc-rồi-ghi trong Java: hai lượt trial gửi song song sẽ thành
     * ba. Không ghi dòng ledger nào (p5 R6: trial không tốn credit lô) và không đụng
     * {@code credit_batch} nào (p17 C10).</p>
     */
    boolean incrementTrialScansUsed(UUID userId, int trialScanLimit, Instant now);

    /**
     * {@code max(expires_at)} của các lô user đã từng kích hoạt, {@code null} nếu chưa có lô nào
     * — nguồn sự thật cho {@code user_entitlement.write_access_until} (bất biến I28).
     */
    Optional<Instant> maxActivatedBatchExpiry(UUID userId);
}
