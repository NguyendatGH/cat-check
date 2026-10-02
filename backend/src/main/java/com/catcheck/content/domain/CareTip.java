package com.catcheck.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Bài viết chăm sóc mèo — ánh xạ 1-1 với bảng {@code care_tip} (p4 H1).
 *
 * <p>Kiểu dữ liệu: dùng <b>một bảng duy nhất</b> cho cả ba loại nội dung. p4 §4.1.7 cấm
 * {@code content_article}/{@code content_category}; {@link CareTipKind} và {@link CareTipCategory}
 * là CỘT, không phải bảng.</p>
 *
 * <p>Đây là aggregate gốc của module content: {@code author_id} và {@code reviewed_by} là cột UUID
 * thuần trỏ sang {@code app_user} (module identity) — KHÔNG dùng {@code @ManyToOne} (R6: cấm
 * association JPA trỏ ra ngoài module; p7 §7.3: kiểu domain không đi qua biên module).</p>
 *
 * <p>Không có cột {@code version}: p4 định nghĩa {@code care_tip} không có version, nên
 * {@code If-Match}/{@code ETag} của PATCH lấy từ {@code updated_at}. Thêm cột version sẽ làm
 * {@code spring.jpa.hibernate.ddl-auto=validate} fail.</p>
 */
@Entity
@Table(name = "care_tip")
public class CareTip {

    /** p4 H1: độ dài tối đa của tiêu đề, ràng buộc {@code ck_care_tip_title}. */
    public static final int TITLE_MAX_LENGTH = 200;

    private static final int SLUG_MAX_LENGTH = 120;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, length = SLUG_MAX_LENGTH)
    private String slug;

    /** Nhóm bản dịch: cùng một bài ở nhiều {@link #locale} dùng chung {@code translation_group}. */
    @Column(name = "translation_group", nullable = false)
    private UUID translationGroup;

    @Column(name = "locale", nullable = false, length = 8)
    private String locale;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 12)
    private CareTipKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 40)
    private CareTipCategory category;

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "body_md", columnDefinition = "text")
    private String bodyMd;

    @Column(name = "cover_image_url", columnDefinition = "text")
    private String coverImageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", nullable = false, columnDefinition = "jsonb")
    private List<String> tags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private CareTipStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 24)
    private ClaimType claimType;

    @Column(name = "source_reference", columnDefinition = "text")
    private String sourceReference;

    @Column(name = "sort_weight", nullable = false)
    private int sortWeight;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Bắt buộc cho JPA; không dùng trực tiếp — hãy gọi {@link #createDraft}. */
    protected CareTip() {
    }

    /**
     * Tạo bản nháp mới. Chỉ {@link CareTipStatus#DRAFT} được tạo trực tiếp: muốn công bố phải đi
     * qua {@link #submitReview()} rồi {@link #publish}, để mỗi bước đều để lại dấu vết ai làm gì
     * (p14 §14.3.5).
     */
    public static CareTip createDraft(
            UUID id,
            String slug,
            UUID translationGroup,
            String locale,
            CareTipKind kind,
            String title,
            ClaimType claimType,
            UUID authorId,
            Instant now) {

        Objects.requireNonNull(id, "careTip.id phải có giá trị");
        Objects.requireNonNull(translationGroup, "careTip.translationGroup phải có giá trị");
        Objects.requireNonNull(claimType, "careTip.claimType phải có giá trị - p4 §4.4.8 không có mặc định");

        CareTip tip = new CareTip();
        tip.id = id;
        tip.translationGroup = translationGroup;
        tip.status = CareTipStatus.DRAFT;
        tip.sortWeight = 100;
        tip.authorId = authorId;
        tip.createdAt = now;
        tip.updatedAt = now;
        tip.applySlug(slug);
        tip.applyLocale(locale);
        tip.kind = Objects.requireNonNull(kind, "careTip.kind phải có giá trị");
        tip.applyTitle(title);
        tip.claimType = claimType;
        return tip;
    }

    // -------------------------------------------------------------- chuyển trạng thái

    /** Bản nháp hoặc bản đã gửi duyệt lại được sửa tiếp; bài đã công bố thì phải tạo bản dịch mới. */
    public void revise(String title, String summary, String bodyMd, ClaimType claimType,
                      String sourceReference, Integer sortWeight, Instant now) {
        requireEditable();
        applyTitle(title);
        this.summary = summary;
        this.bodyMd = bodyMd;
        this.claimType = Objects.requireNonNull(claimType, "claimType không được null");
        this.sourceReference = sourceReference;
        if (sortWeight != null) {
            this.sortWeight = sortWeight;
        }
        touch(now);
    }

    public void applyCategory(CareTipCategory category, Instant now) {
        this.category = category;
        touch(now);
    }

    public void applyTags(List<String> tags, Instant now) {
        this.tags = tags == null ? new ArrayList<>() : List.copyOf(tags);
        touch(now);
    }

    public void submitReview(Instant now) {
        if (status != CareTipStatus.DRAFT && status != CareTipStatus.IN_REVIEW) {
            throw new IllegalStateException("Chỉ bản nháp mới gửi duyệt được, hiện tại: " + status);
        }
        this.status = CareTipStatus.IN_REVIEW;
        touch(now);
    }

    /**
     * Chuyển sang {@link CareTipStatus#PUBLISHED}. Người duyệt phải khác người soạn — quy tắc
     * {@code CONTENT_SELF_APPROVAL_FORBIDDEN} của L44 được kiểm ở tầng application vì cần so sánh
     * với danh tính người gọi, mà domain không biết người gọi là ai.
     */
    public void publish(UUID reviewerId, String reviewNote, Instant now) {
        if (status != CareTipStatus.IN_REVIEW) {
            throw new IllegalStateException("Chỉ bài đang chờ duyệt mới công bố được, hiện tại: " + status);
        }
        if (claimType.requiresSourceReference() && isBlank(sourceReference)) {
            throw new IllegalStateException(
                    "Bài tuyên bố " + claimType + " thì bắt buộc phải có source_reference (I31)");
        }
        this.reviewedBy = reviewerId;
        this.reviewNote = reviewNote;
        this.reviewedAt = now;
        this.publishedAt = now;
        this.status = CareTipStatus.PUBLISHED;
        touch(now);
    }

    public void archive(Instant now) {
        if (status == CareTipStatus.ARCHIVED) {
            return;
        }
        this.status = CareTipStatus.ARCHIVED;
        touch(now);
    }

    public void restoreToDraft(Instant now) {
        this.status = CareTipStatus.DRAFT;
        this.publishedAt = null;
        this.reviewedBy = null;
        this.reviewedAt = null;
        touch(now);
    }

    /** Gỡ khỏi hiển thị bằng xoá mềm — {@code deleted_at} (p4 H1). */
    public void softDelete(Instant now) {
        this.deletedAt = now;
        touch(now);
    }

    // ---------------------------------------------------------------- getters

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public UUID getTranslationGroup() {
        return translationGroup;
    }

    public String getLocale() {
        return locale;
    }

    public CareTipKind getKind() {
        return kind;
    }

    public CareTipCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getBodyMd() {
        return bodyMd;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }

    public CareTipStatus getStatus() {
        return status;
    }

    public ClaimType getClaimType() {
        return claimType;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public int getSortWeight() {
        return sortWeight;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isPublished() {
        return status == CareTipStatus.PUBLISHED;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Bài đã công bố thì sửa tại chỗ sẽ làm nội dung đang hiển thị đổi mà không qua duyệt. */
    public boolean isEditable() {
        return status == CareTipStatus.DRAFT || status == CareTipStatus.IN_REVIEW;
    }

    public Optional<String> nonBlankSourceReference() {
        return isBlank(sourceReference) ? Optional.empty() : Optional.of(sourceReference);
    }

    // ---------------------------------------------------------------- internals

    private void requireEditable() {
        if (!isEditable()) {
            throw new IllegalStateException(
                    "Bài đang ở trạng thái " + status + " thì không sửa tại chỗ được (L42 chỉ sửa bản nháp)");
        }
    }

    private void applySlug(String value) {
        if (isBlank(value) || value.length() > SLUG_MAX_LENGTH) {
            throw new IllegalArgumentException("careTip.slug phải có 1.." + SLUG_MAX_LENGTH + " ký tự");
        }
        this.slug = value;
    }

    private void applyLocale(String value) {
        if (!"vi".equals(value) && !"en".equals(value)) {
            throw new IllegalArgumentException("careTip.locale chỉ nhận 'vi' hoặc 'en': " + value);
        }
        this.locale = value;
    }

    private void applyTitle(String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("careTip.title không được rỗng");
        }
        String trimmed = value.trim();
        if (trimmed.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("careTip.title dài tối đa " + TITLE_MAX_LENGTH + " ký tự");
        }
        this.title = trimmed;
    }

    private void touch(Instant now) {
        this.updatedAt = now;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
