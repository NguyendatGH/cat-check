package com.catcheck.credit.domain.port;

import com.catcheck.credit.domain.ActivationBatchSummary;
import com.catcheck.credit.domain.ActivationCode;
import com.catcheck.credit.domain.ActivationCodeFilter;
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
     * Tạo nhiều mã trong <b>một</b> lần gửi lệnh (JDBC batch).
     *
     * <p>Không phải tối ưu hoá sớm: p8 L20 cho phép sinh tới 50 000 mã một lần, và 50 000 lần
     * {@link #insert} là 50 000 round-trip tới Postgres — ở độ trễ mạng nội bộ 1 ms thì đã là
     * ~50 giây trong MỘT transaction, đủ để request timeout và để lại transaction dài khoá
     * {@code activation_code} suốt thời gian đó.</p>
     */
    void insertAll(List<ActivationCode> codes);

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

    /** L19/L23 — một mã theo khoá chính, cho màn admin. */
    Optional<ActivationCode> findById(UUID codeId);

    /** L19 — tra cứu theo bộ lọc của màn admin, phân trang offset (p8 §8.1.4 cột {@code O}). */
    List<ActivationCode> search(ActivationCodeFilter filter, int offset, int limit);

    /** Tổng số dòng khớp {@code filter} — để màn admin hiện tổng và nhảy trang. */
    long count(ActivationCodeFilter filter);

    /**
     * L21 — danh sách lô đã phát hành, gom theo {@code production_batch}, mới nhất trước.
     *
     * <p>Dòng có {@code production_batch IS NULL} bị bỏ qua: nó không thuộc lô nào nên không có
     * {@code batchId} để trỏ tới.</p>
     */
    List<ActivationBatchSummary> listBatches(int offset, int limit);

    /** Số lô (số giá trị {@code production_batch} khác nhau, không tính NULL). */
    long countBatches();

    /** L22/L24 — một lô theo định danh {@code production_batch}. */
    Optional<ActivationBatchSummary> findBatch(String productionBatch);

    /**
     * L24 — vô hiệu hoá cả lô. Chỉ đụng các mã còn {@code ISSUED}: mã {@code REDEEMED} đã tạo
     * {@code credit_batch} nên không void được (p14 §14.3.2 mục 4), mã {@code VOID} thì đã xong.
     *
     * @return số mã thực sự chuyển sang {@code VOID}
     */
    int markBatchVoid(String productionBatch);

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
