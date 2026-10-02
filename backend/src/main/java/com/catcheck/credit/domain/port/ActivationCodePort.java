package com.catcheck.credit.domain.port;

import com.catcheck.credit.domain.ActivationCode;
import com.catcheck.credit.domain.ActivationCodeStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc/ghi {@code activation_code}.
 *
 * <p>Tra cứu CHỈ theo {@code code_hash} — DB không có mã thô, không thể có (p5 §5.9). Đó là
 * lý do p8 §8.4 nhóm admin ghi rõ <i>"không tra theo mã đầy đủ — chỉ có hash trong DB"</i>.</p>
 */
public interface ActivationCodePort {

    Optional<ActivationCode> findByCodeHash(String codeHash);

    /** Tạo mới một mã ở trạng thái {@link ActivationCodeStatus#ISSUED}. */
    void insert(ActivationCode code);

    /**
     * Chuyển {@code ISSUED → REDEEMED} một cách <b>có điều kiện</b>, trả về {@code true} nếu
     * thực sự chuyển.
     *
     * <p>Điều kiện {@code WHERE status = 'ISSUED'} là lớp chống double-redeem thứ hai bên cạnh
     * khóa ở tầng ứng dụng: hai request song song cùng mang một mã sẽ chỉ một request thắng
     * (bất biến I24 — một mã đổi ra đúng một lô, bởi đúng một tài khoản).</p>
     *
     * <p>Phải chạy trong cùng transaction với việc tạo {@code credit_batch} và dòng ledger
     * {@code GRANT} (p5 R1).</p>
     */
    boolean markRedeemed(UUID codeId, UUID userId, Instant redeemedAt);

    /** Admin vô hiệu hoá một mã (in hỏng, thu hồi lô sản xuất) — M6. */
    boolean markVoid(UUID codeId);

    /** Lô mã đã phát hành theo gói, phân trang offset cho màn admin — M6. */
    List<ActivationCode> findIssuedCodes(String packageCode, ActivationCodeStatus status, int offset, int limit);

    long countIssuedCodes(String packageCode, ActivationCodeStatus status);

    /**
     * Khoá dòng mã và <b>trả về trạng thái ĐÃ KHOÁ</b> để chống hai request kích hoạt cùng lúc
     * trên cùng một mã.
     *
     * <p>Phải được gọi <b>trước</b> {@link #markRedeemed} trong cùng transaction, và phải dùng
     * giá trị trả về chứ không dùng lại object đã đọc từ {@link #findByCodeHash}: khoá chỉ ngăn
     * ghi, nó không làm cho object đã nạp vào bộ nhớ tự nhiên mới theo. Đọc lại sau khoá là điều
     * kiện cần để phát hiện request thứ hai xếp hàng phía sau (p5 R1, bất biến I24).</p>
     *
     * @return dòng mã sau khi khoá, rỗng nếu dòng không còn tồn tại
     */
    Optional<ActivationCode> lockById(UUID codeId);
}
