package com.catcheck.cat.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc snapshot hồ sơ một mèo cho ngữ cảnh trợ lý AI (module {@code ai}) — xem
 * {@code docs/cat-rag-credit-pipeline.md} mục A2-(1)/A4-(1).
 *
 * <p><b>Kiểm quyền sở hữu NGAY TẠI ĐÂY.</b> {@link #findSnapshot} trả {@link Optional#empty()}
 * nếu {@code catId} không tồn tại HOẶC không thuộc {@code ownerId} — không ném lỗi, không phân
 * biệt hai trường hợp (cùng lý luận {@code HealthFlagQueryService#requireOwned}: không tiết lộ
 * cho người gọi biết "mèo tồn tại nhưng không phải của bạn"). Đây là điểm kiểm sở hữu <b>duy
 * nhất</b> mà {@code ai.application.CatContextPort} dựa vào trước khi gọi sang các cổng khác
 * ({@code scan.api.ScanHistoryQuery}, {@code insight.api.OpenHealthFlagQuery},
 * {@code reminder.api.ActiveReminderQuery}) bằng đúng {@code catId} vừa được xác nhận — các cổng
 * đó nhận thẳng {@code catId} và không tự kiểm sở hữu lại (cùng quy ước
 * {@code scan.api.ScanHistoryQuery} đang dùng cho {@code insight}/{@code export}).</p>
 *
 * <p><b>Cố ý KHÔNG có {@code notes}.</b> {@code cat.notes} là trường tự do chủ nuôi gõ tay, có
 * thể chứa số điện thoại bác sĩ, địa chỉ, tên người khác... — không có cách kiểm bằng regex đủ
 * tin cậy để loại PII khỏi văn bản tự do. Snapshot này sẽ được đưa thẳng vào prompt LLM nên lược
 * bỏ hoàn toàn {@code notes} là lựa chọn an toàn nhất, không phải sơ suất.</p>
 */
public interface CatContextQuery {

    Optional<CatProfileSnapshot> findSnapshot(UUID ownerId, UUID catId);

    /**
     * @param ageMonths tính từ {@code birth_date} hoặc {@code approx_age_months} (xem
     *                  {@code cat.domain.Cat#ageMonths}); {@code null} nếu dữ liệu tuổi hỏng
     *                  (birth_date ở tương lai)
     * @param survey    {@code null} nếu chưa khảo sát hoặc lần gần nhất là "bỏ qua"
     */
    record CatProfileSnapshot(
            UUID catId,
            String name,
            Integer ageMonths,
            String breedCode,
            String breedOther,
            String sex,
            Boolean neutered,
            BigDecimal weightKg,
            Instant weightUpdatedAt,
            SurveyAnswers survey,
            List<ClinicalSignEntry> recentClinicalSigns) {

        public CatProfileSnapshot {
            recentClinicalSigns = recentClinicalSigns == null ? List.of() : List.copyOf(recentClinicalSigns);
        }
    }

    /**
     * Vài câu trả lời chính của khảo sát 5 câu (p4 §4.4.4) — mã thô theo
     * {@code questionnaire/health-survey-v1.json}, KHÔNG dịch nghĩa (việc diễn giải thuộc tầng
     * wiring sau của {@code ai}).
     */
    record SurveyAnswers(
            String questionnaireVersion,
            String litterType,
            String urinaryHistory,
            String dietType,
            String urinationFrequency,
            List<String> symptoms,
            Instant submittedAt) {

        public SurveyAnswers {
            symptoms = symptoms == null ? List.of() : List.copyOf(symptoms);
        }
    }

    /** Một lần khai dấu hiệu lâm sàng — chỉ các lần trong 14 ngày gần nhất tính tới lúc gọi. */
    record ClinicalSignEntry(List<String> signs, Instant reportedAt, boolean acknowledged) {

        public ClinicalSignEntry {
            signs = signs == null ? List.of() : List.copyOf(signs);
        }
    }
}
