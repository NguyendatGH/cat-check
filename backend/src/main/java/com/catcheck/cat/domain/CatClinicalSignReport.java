package com.catcheck.cat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Bản ghi "chủ nuôi khai dấu hiệu lâm sàng" (p4 C5, bảng {@code cat_clinical_sign_report}).
 *
 * <p><b>Đây là lịch sử khai báo, không phải kết luận y tế.</b> Tên hằng số và câu chữ hiển thị đều là
 * mô tả quan sát ("rặn nhiều", "thấy màu đỏ"), không phải tên bệnh. Quyết định #6 cấm tuyên bố chẩn
 * đoán từ ảnh cát và nội dung này cũng không được vượt quá mức đó.</p>
 *
 * <p><b>Tập hằng số, không bảng tra cứu.</b> p4 C5 dùng {@code VARCHAR(32)[]} + CHECK {@code <@}
 * để thay cho bảng nối. Ưu điểm: DB tự chặn giá trị lạ ngay cả khi có ai đó ghi thẳng bằng SQL,
 * và không tốn một bảng cho sáu giá trị cố định. Đổi danh sách này về sau phải kèm migration.</p>
 *
 * <p><b>{@code healthFlagId} có cột nhưng chưa có FK.</b> Bảng {@code health_flag} sinh ở V12, nên
 * V8 để cột trống; A6 thêm ràng buộc khóa ngoại sau. Ghi chú này quan trọng vì
 * {@code ddl-auto=validate} không kiểm tra FK nên thiếu FK sẽ không bị phát hiện tự động.</p>
 */
@Entity
@Table(name = "cat_clinical_sign_report")
public class CatClinicalSignReport {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "cat_id", nullable = false)
    private UUID catId;

    @Column(name = "reported_by", nullable = false)
    private UUID reportedBy;

    /**
     * Lưu đúng thứ tự người dùng chọn bằng {@code LinkedHashSet} → {@code String[]}: DB so sánh
     * {@code <@} là so sánh TẬP nên thứ tự không ảnh hưởng, nhưng giữ thứ tự chọn giúp con trời
     * đọc lại bản khai đúng như người dùng đã chọn.
     */
    @Column(name = "signs", nullable = false, columnDefinition = "varchar(32)[]")
    private String[] signs = new String[0];

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 24)
    private ClinicalSignSource source;

    /** Cờ cho biết màn hình chặn cảnh báo đã hiện khi khai; dùng để đo tỉ lệ bỏ qua. */
    @Column(name = "blocking_shown", nullable = false)
    private boolean blockingShown;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private Instant reportedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "health_flag_id")
    private UUID healthFlagId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Bắt buộc cho JPA. */
    protected CatClinicalSignReport() {
    }

    /**
     * D17 — lưu bản khai dấu hiệu lâm sàng.
     *
     * @param signs không rỗng; DB cũng có CHECK nhưng để lỗi nổi lên tầng nghiệp vụ với thông điệp
     *              rõ ràng hơn là để người dùng nhìn thấy mã lỗi ràng buộc của Postgres
     */
    public static CatClinicalSignReport create(UUID id, UUID catId, UUID reportedBy,
                                               Set<ClinicalSign> signs, ClinicalSignSource source,
                                               boolean blockingShown, Instant now) {
        Objects.requireNonNull(id, "cat_clinical_sign_report.id phải có giá trị");
        Objects.requireNonNull(catId, "cat_clinical_sign_report.catId phải có giá trị");
        Objects.requireNonNull(reportedBy, "cat_clinical_sign_report.reportedBy phải có giá trị");
        Objects.requireNonNull(source, "cat_clinical_sign_report.source phải có giá trị");
        if (signs == null || signs.isEmpty()) {
            throw new IllegalArgumentException("Phải chọn ít nhất một dấu hiệu lâm sàng");
        }
        CatClinicalSignReport report = new CatClinicalSignReport();
        report.id = id;
        report.catId = catId;
        report.reportedBy = reportedBy;
        report.source = source;
        report.blockingShown = blockingShown;
        report.reportedAt = now;
        report.createdAt = now;
        report.updatedAt = now;
        report.applySigns(signs);
        return report;
    }

    /** Ghi nhận bác sĩ đã xem bản khai — chỉ mục để PDF ghi "đã được xem xét". */
    public void acknowledge(Instant now) {
        this.acknowledgedAt = now;
        this.updatedAt = now;
    }

    /** A6 gọi khi đã sinh {@code health_flag}; chỉ để truyền kết quả, không tự sinh cờ. */
    public void linkHealthFlag(UUID healthFlagId, Instant now) {
        this.healthFlagId = Objects.requireNonNull(healthFlagId, "healthFlagId phải có giá trị");
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCatId() {
        return catId;
    }

    public UUID getReportedBy() {
        return reportedBy;
    }

    public Set<ClinicalSign> clinicalSigns() {
        return Arrays.stream(signs).map(ClinicalSign::valueOf).collect(
                LinkedHashSet::new, Set::add, Set::addAll);
    }

    public ClinicalSignSource getSource() {
        return source;
    }

    public boolean isBlockingShown() {
        return blockingShown;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public UUID getHealthFlagId() {
        return healthFlagId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private void applySigns(Set<ClinicalSign> values) {
        this.signs = values.stream().map(Enum::name).toArray(String[]::new);
    }
}
