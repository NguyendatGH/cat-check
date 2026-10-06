package com.catcheck.credit.domain.port;

import com.catcheck.credit.domain.CreditLedgerRefType;
import com.catcheck.credit.domain.CreditLedgerType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cổng đọc sổ cái {@code credit_ledger} cho màn lịch sử giao dịch
 * ({@code GET /credits/ledger}, p8 H3).
 *
 * <p>Phân trang <b>keyset/cursor</b> chứ không phải offset: sổ cái là bảng chỉ INSERT nên dữ
 * liệu mới liên tục chèn ở đầu danh sách — offset sẽ làm bản ghi nhảy trang và lặp trang khi
 * người dùng cuộn (p8 §8.1.4).</p>
 */
public interface CreditLedgerQueryPort {

    /**
     * Một trang sổ cái, mới nhất trước.
     *
     * @param entries    các dòng của trang, sắp {@code created_at} giảm dần
     * @param hasMore    còn dòng nữa không
     * @param nextCursor khoá trang kế tiếp, {@code null} khi đã tới cuối
     */
    record LedgerPage(List<LedgerRow> entries, boolean hasMore, LedgerCursor nextCursor) {

        public LedgerPage {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    /**
     * Một dòng sổ cái đã đọc, đủ để render danh sách.
     *
     * @param id          id dòng
     * @param type        loại biến động
     * @param amount      dương/âm
     * @param balanceAfter số dư khả dụng toàn user sau giao dịch
     * @param batchId     lô bị biến động
     * @param packageCode mã gói của lô (null nếu dòng không gắn lô)
     * @param refType     loại tài nguyên tham chiếu
     * @param note        ghi chú
     * @param createdAt   thời điểm ghi
     */
    record LedgerRow(
            UUID id,
            CreditLedgerType type,
            int amount,
            int balanceAfter,
            UUID batchId,
            String packageCode,
            CreditLedgerRefType refType,
            String note,
            Instant createdAt
    ) {
    }

    /**
     * Khoá phân trang keyset: {@code (created_at, id)} của dòng cuối cùng đã trả.
     *
     * @param sortKey mốc sắp xếp = {@code created_at}
     * @param id      id dòng, khoá phụ để thứ tự tất định khi trùng {@code created_at}
     */
    record LedgerCursor(Instant sortKey, UUID id) {

        public LedgerCursor {
            if (sortKey == null || id == null) {
                throw new IllegalArgumentException("ledgerCursor thiếu trường bắt buộc");
            }
        }
    }

    /**
     * Đọc một trang sổ cái của user, mới nhất trước.
     *
     * @param userId chủ credit — bắt buộc có trong mọi truy vấn (bất biến I14: chỉ trả dữ liệu
     *                của chính chủ)
     * @param cursor khoá trang trước, {@code null} = trang đầu
     * @param limit  số dòng tối đa (p8 §8.1.4: mặc định 20, tối đa 100)
     */
    LedgerPage findByUser(UUID userId, LedgerCursor cursor, int limit);

    /**
     * Một trang sổ cái của user cho màn <b>quản trị</b>, phân trang <b>offset</b> kèm tổng số
     * dòng — cột {@code Trang = O} của p8 L9 ({@code GET /admin/users/{userId}/credits}).
     *
     * <p><b>Vì sao offset ở đây mà cursor ở {@link #findByUser}:</b> hai màn hỏi hai câu khác
     * nhau. Người dùng cuộn một dòng thời gian (cursor đúng); tổng đài đối soát một tài khoản và
     * cần "trang 3/7" + "tổng 128 dòng" — hai thứ cursor không cho (p8 §8.1.4). Bảng vẫn là
     * chỉ-INSERT nên offset có nhược điểm cố hữu (dòng mới chèn ở đầu làm lệch trang), nhưng với
     * sổ cái của <i>một</i> tài khoản xem trong vài phút thì đó đúng là đánh đổi p8 đã chấp nhận
     * cho mọi bảng admin. Xem handoff H15.150.</p>
     *
     * @param userId chủ sổ — bắt buộc có trong mọi truy vấn (bất biến I14)
     * @param offset số dòng bỏ qua
     * @param limit  số dòng tối đa (trần cứng 100)
     */
    LedgerOffsetPage findByUserForAdmin(UUID userId, int offset, int limit);

    /**
     * Một trang offset: các dòng + tổng số dòng. Tách khỏi {@link LedgerPage} thay vì nhồi cả
     * {@code nextCursor} lẫn {@code totalElements} vào một record — một record mà nửa số trường
     * luôn {@code null} là chỗ để lẫn hai chế độ phân trang mà p8 §8.1.4 cố ý giữ riêng.
     */
    record LedgerOffsetPage(List<LedgerRow> entries, long totalElements) {

        public LedgerOffsetPage {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }
}
