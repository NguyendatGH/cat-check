package com.catcheck.cat.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Một lần trả lời bộ khảo sát sức khoẻ 5 câu của một mèo (p4 C3, bảng {@code cat_health_survey}).
 *
 * <p>Câu trả lời lưu ở cột JSONB chứ không tách bảng con. Lý do do p4 nêu và lý do này vẫn đúng:
 * bộ câu hỏi có version và sẽ thêm/bớt câu, mà Phase 1 không có truy vấn nào lọc theo một câu
 * trả lời cụ thể. Tách bảng con bây giờ sẽ tạo ra một lần migrate dữ liệu chỉ để đổi bộ câu hỏi.</p>
 *
 * <p><b>Phân biệt "bỏ qua" với "trả lời".</b> {@code skipped = true} là người dùng bấm bỏ qua, và
 * CHECK của DB bắt buộc {@code submitted_at IS NULL}; ngược lại phải có {@code submitted_at}.
 * Hai ràng buộc cùng nhau làm trạng thái giả không thể tồn tại.</p>
 *
 * <p>Bản ghi là bất biến sau khi gửi: sửa câu trả lời tạo bản ghi mới giữ {@code created_at} cũ, vì
 * đây là dữ liệu đưa vào hồ sơ PDF cho bác sĩ (p4 §4.8.2).</p>
 */
@Entity
@Table(name = "cat_health_survey")
public class CatHealthSurvey {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "cat_id", nullable = false)
    private UUID catId;

    /** Chuỗi version của bộ câu hỏi, ví dụ {@code v1}. Ghi kèm để dữ liệu cũ vẫn đọc được. */
    @Column(name = "questionnaire_version", nullable = false, length = 16)
    private String questionnaireVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> answers = Map.of();

    @Column(name = "skipped", nullable = false)
    private boolean skipped;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Bắt buộc cho JPA. */
    protected CatHealthSurvey() {
    }

    private CatHealthSurvey(UUID id, UUID catId, String questionnaireVersion, boolean skipped,
                            Map<String, Object> answers, UUID submittedBy, Instant now) {
        this.id = Objects.requireNonNull(id, "cat_health_survey.id phải có giá trị");
        this.catId = Objects.requireNonNull(catId, "cat_health_survey.catId phải có giá trị");
        this.questionnaireVersion = Objects.requireNonNull(
                questionnaireVersion, "cat_health_survey.questionnaireVersion phải có giá trị");
        this.skipped = skipped;
        this.answers = Map.copyOf(answers);
        this.submittedBy = submittedBy;
        // BUG THẬT ĐÃ SỬA (không phải judgment call): bản gốc của A3 không bao giờ gán
        // submittedAt, nên MỌI lần submitted() đều vi phạm CHECK ck_cat_health_survey_submitted
        // (p4 C3: "skipped HOẶC submitted_at IS NOT NULL") và INSERT sẽ bị Postgres từ chối ngay
        // khi context thật chạy tới đây — compile vẫn PASS vì Java không kiểm CHECK constraint.
        this.submittedAt = skipped ? null : now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * D18 — người dùng trả lời đủ 5 câu.
     *
     * <p>{@code submittedBy} luôn là chủ của mèo, không nhận từ client: nếu nhận từ client thì
     * request có thể ghi hộ người khác vào lịch sử bác sĩ đọc.</p>
     */
    public static CatHealthSurvey submitted(UUID id, UUID catId, String questionnaireVersion,
                                           Map<String, Object> answers, UUID submittedBy, Instant now) {
        return new CatHealthSurvey(id, catId, questionnaireVersion, false, answers, submittedBy, now);
    }

    /**
     * D18 — người dùng bấm bỏ qua cả bộ khảo sát.
     *
     * <p>Vẫn tạo bản ghi thay vì không tạo: nếu vắng mặt thì "chưa hỏi" và "hỏi rồi, không muốn
     * trả lời" trông giống nhau, và sau đó không phân biệt được để nhắc lại.</p>
     */
    public static CatHealthSurvey skipped(UUID id, UUID catId, String questionnaireVersion,
                                          UUID submittedBy, Instant now) {
        return new CatHealthSurvey(id, catId, questionnaireVersion, true, Map.of(), submittedBy, now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCatId() {
        return catId;
    }

    public String getQuestionnaireVersion() {
        return questionnaireVersion;
    }

    public Map<String, Object> getAnswers() {
        return answers;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
