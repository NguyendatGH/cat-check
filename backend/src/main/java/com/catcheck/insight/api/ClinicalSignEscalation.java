package com.catcheck.insight.api;

import java.util.List;
import java.util.UUID;

/**
 * <b>HỢP ĐỒNG CHO MODULE {@code cat}</b> — cảnh báo khẩn {@code URGENT_CLINICAL_SIGN} (p6 §6.9.6),
 * mức ưu tiên cao nhất toàn hệ thống, KHÔNG dựa trên pipeline màu mà dựa trên dấu hiệu chủ nuôi
 * tự khai ({@code cat_clinical_sign_report}, bảng do module {@code cat} sở hữu — V8).
 *
 * <p><b>Vì sao cổng này CHƯA được gọi ở đâu trong MVP:</b> endpoint khai báo
 * {@code POST /cats/{catId}/clinical-signs} (p8 nhóm D, D20) thuộc "Nhóm D — Hồ sơ mèo", nằm
 * ngoài phạm vi file mà A6 được sửa ({@code cat/**}). Module {@code cat} hiện CHƯA có bất kỳ
 * {@code @RestController} nào (kể cả D1-D19 cơ bản — xem {@code docs/handovers/A3.md} và
 * {@code docs/handovers/A6.md}), nên đây là một khoảng trống liên-module có trước A6, không phải
 * lỗi của module này. Cổng được công bố sẵn ở đây để khi module {@code cat} (hoặc W3) xây endpoint
 * đó, chỉ cần tiêm {@code insight.api.ClinicalSignEscalation} và gọi, không cần đợi insight sửa gì
 * thêm — đúng tinh thần dependency inversion đã dùng cho {@code ChartCatalog}.</p>
 */
public interface ClinicalSignEscalation {

    /**
     * Ghi nhận dấu hiệu lâm sàng và bắn {@code health_flag} mức {@code URGENT} — KHÔNG cooldown,
     * hiện lại mỗi lần khai (p6 §6.9.6, bất biến I11).
     *
     * @param catId               mèo được khai
     * @param scanId              lần quét kèm theo, {@code null} nếu khai từ khảo sát ban đầu
     * @param clinicalSignReportId id dòng {@code cat_clinical_sign_report} vừa ghi — dùng làm hậu
     *                              tố {@code dedupe_key} thay cho bucket giờ (p4 D12)
     * @param signs               tập con của {@code STRAINING, NO_URINE, CRYING, BLOOD_VISIBLE,
     *                            LETHARGY_ANOREXIA, EXCESSIVE_LICKING}
     */
    EscalationResult reportClinicalSigns(UUID catId, UUID scanId, UUID clinicalSignReportId, List<String> signs);

    /**
     * @param healthFlagId     id {@code health_flag} vừa tạo
     * @param blockingRequired {@code true} nếu phải hiện modal chặn màn (NO_URINE/STRAINING đơn lẻ,
     *                         hoặc ≥ 2 dấu hiệu cùng lúc — p6 §6.9.6)
     * @param explanationVi    câu cảnh báo đã render, nguyên văn theo p6 §6.9.6
     */
    record EscalationResult(UUID healthFlagId, boolean blockingRequired, String explanationVi) {
    }
}
