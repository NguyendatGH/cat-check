package com.catcheck.cat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Hồ sơ mèo — aggregate gốc của module cat, ánh xạ 1-1 với bảng {@code cat} (p4 C1).
 *
 * <p><b>Không có cột {@code userId}.</b> p4 đặt tên cột là {@code owner_id}; đó là tên do phần sở
 * hữu miền đặt và là nguồn chuẩn (spec/04-index.md §2). Kiểu vẫn là UUID thuần, không dùng
 * {@code @ManyToOne} trỏ sang {@code app_user} (R6 + p7 §7.3: kiểu domain không đi qua biên module).</p>
 *
 * <p><b>Xoá là xoá mềm.</b> {@link #deletedAt} giữ lại hồ sơ vì {@code scan.cat_id} và các file PDF
 * đã xuất vẫn trỏ tới nó; xoá vật lý sẽ làm lịch sử mất nghĩa (p4 §4.8.2).</p>
 *
 * <p><b>Không có cột {@code version}.</b> p4 không định nghĩa version cho {@code cat}, nên
 * {@code ETag}/{@code If-Match} của D4 lấy từ {@code updated_at}. Thêm cột version làm
 * {@code ddl-auto=validate} fail.</p>
 */
@Entity
@Table(name = "cat")
public class Cat {

    /** p4 C1: {@code CC-VN-<6 ký tự Crockford Base32>}. */
    public static final String PUBLIC_CODE_PREFIX = "CC-VN-";

    /** Crockford Base32 loại bỏ I, L, O, U để tránh nhầm khi đọc qua điện thoại. */
    public static final String CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    public static final int PUBLIC_CODE_LENGTH = 6;
    public static final int NAME_MAX_LENGTH = 60;
    public static final int MAX_AGE_MONTHS = 360;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "approx_age_months")
    private Integer approxAgeMonths;

    @Column(name = "breed_code", length = 48)
    private String breedCode;

    @Column(name = "breed_other", columnDefinition = "text")
    private String breedOther;

    @Column(name = "coat_color", length = 48)
    private String coatColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false, length = 8)
    private CatSex sex = CatSex.UNKNOWN;

    @Column(name = "neutered")
    private Boolean neutered;

    @Column(name = "weight_kg", precision = 4, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "weight_updated_at")
    private Instant weightUpdatedAt;

    @Column(name = "avatar_storage_key", columnDefinition = "text")
    private String avatarStorageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "avatar_storage_provider", length = 16)
    private AvatarStorageProvider avatarStorageProvider;

    @Column(name = "public_code", nullable = false, length = 24)
    private String publicCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CatStatus status = CatStatus.ACTIVE;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Bắt buộc cho JPA — mọi thay đổi đi qua factory hoặc phương thức nghiệp vụ. */
    protected Cat() {
    }

    /**
     * Tạo hồ sơ mèo mới.
     *
     * @param birthDate        ngày sinh nếu chủ biết
     * @param approxAgeMonths  tuổi ước lượng nếu chỉ biết "hai tuổi ba tháng"
     * @throws IllegalArgumentException nếu truyền CẢ HAI hoặc KHÔNG CÓ trường nào — p8 §8.5.3 định
     *                                nghĩa {@code CAT_AGE_CONFLICT} và {@code ageMonths} chỉ tính từ
     *                                một nguồn
     */
    public static Cat create(
            UUID id,
            UUID ownerId,
            String name,
            LocalDate birthDate,
            Integer approxAgeMonths,
            String publicCode,
            LocalDate today,
            Instant now) {

        Objects.requireNonNull(id, "cat.id phải có giá trị");
        Objects.requireNonNull(ownerId, "cat.ownerId phải có giá trị");
        Objects.requireNonNull(today, "cat.today phải có giá trị - domain không tự đọc đồng hồ hệ thống");
        Cat cat = new Cat();
        cat.id = id;
        cat.ownerId = ownerId;
        cat.createdAt = now;
        cat.updatedAt = now;
        cat.applyName(name);
        cat.applyAgeSource(birthDate, approxAgeMonths, today);
        cat.publicCode = requirePublicCode(publicCode);
        return cat;
    }

    // ------------------------------------------------------------ sửa thông tin

    /**
     * D4 — merge-patch. Mỗi tham số {@code null} nghĩa là giữ nguyên, nên không có cách nào
     * "xoá sạch" một trường bằng PATCH; muốn xoá thì dùng một giá trị rỗng được chấp nhận riêng.
     */
    public void update(
            String name,
            LocalDate birthDate,
            boolean ageTouched,
            Integer approxAgeMonths,
            String breedCode,
            String breedOther,
            String coatColor,
            CatSex sex,
            Boolean neutered,
            BigDecimal weightKg,
            String notes,
            LocalDate today,
            Instant now) {

        if (name != null) {
            applyName(name);
        }
        applyAgePatch(birthDate, ageTouched, approxAgeMonths, today);
        if (breedCode != null) {
            this.breedCode = breedCode;
        }
        if (breedOther != null) {
            this.breedOther = breedOther;
        }
        if (coatColor != null) {
            this.coatColor = coatColor;
        }
        if (sex != null) {
            this.sex = sex;
        }
        if (neutered != null) {
            this.neutered = neutered;
        }
        if (weightKg != null) {
            applyWeight(weightKg, now);
        }
        if (notes != null) {
            this.notes = notes;
        }
        this.updatedAt = now;
    }

    /**
     * Gán các thuộc tính mô tả ngay lúc tạo hồ sơ (D2).
     *
     * <p>Tách khỏi {@link #update} vì lúc tạo mọi thứ đều {@code null} và gọi {@code update} với
     * toàn bộ tham số {@code null} sẽ giống hệt việc "không sửa gì" — hai ý nghĩa khác nhau dễ bị
     * nhầm khi đọc lại code.</p>
     */
    public void applyProfile(
            String breedCode,
            String breedOther,
            String coatColor,
            CatSex sex,
            Boolean neutered,
            BigDecimal weightKg,
            String notes,
            LocalDate today,
            Instant now) {

        if (breedCode != null) {
            this.breedCode = breedCode;
        }
        if (breedOther != null) {
            this.breedOther = breedOther;
        }
        if (coatColor != null) {
            this.coatColor = coatColor;
        }
        if (sex != null) {
            this.sex = sex;
        }
        if (neutered != null) {
            this.neutered = neutered;
        }
        if (weightKg != null) {
            applyWeight(weightKg, now);
        }
        if (notes != null) {
            this.notes = notes;
        }
        this.updatedAt = now;
    }

    /** D6 — "bé đã mất / đã cho đi". Dữ liệu và biểu đồ vẫn xem được. */
    public void archive(Instant now) {
        this.status = CatStatus.ARCHIVED;
        this.updatedAt = now;
    }

    /** D7 — đưa trở lại theo dõi. */
    public void unarchive(Instant now) {
        this.status = CatStatus.ACTIVE;
        this.updatedAt = now;
    }

    /**
     * D8 — đặt làm mèo chính. Chỉ mèo {@code ACTIVE} và chưa xoá mềm mới được làm mèo chính
     * (p8: {@code CAT_PRIMARY_REQUIRES_ACTIVE}); ràng buộc "một mèo chính mỗi chủ" do partial
     * unique index {@code uq_cat_owner_primary} bảo vệ ở tầng DB.
     */
    public void makePrimary(Instant now) {
        this.primary = true;
        this.updatedAt = now;
    }

    /** Bỏ cờ mèo chính — cần khi chủ đổi sang bé khác, vì chỉ được có tối đa một. */
    public void clearPrimary(Instant now) {
        this.primary = false;
        this.updatedAt = now;
    }

    /**
     * D10 — gắn ảnh đại diện. Ghi cả khoá lưu trữ lẫn provider, không lưu URL.
     *
     * <p>Nhận khoá ở dạng {@link String}, KHÔNG phải {@code media.api.StorageKey}: ArchUnit R1 cấm
     * class trong {@code ..domain..} phụ thuộc package {@code ..api..}, và mẫu {@code ..api..} khớp
     * cả package api của module khác chứ không chỉ của chính nó. Tầng application chuyển
     * {@code String} → {@code StorageKey} khi cần đọc tệp.</p>
     */
    public void attachAvatar(String storageKey, AvatarStorageProvider provider, Instant now) {
        this.avatarStorageKey = Objects.requireNonNull(storageKey, "cat.avatarStorageKey phải có giá trị");
        this.avatarStorageProvider = provider;
        this.updatedAt = now;
    }

    /** D11 — gỡ ảnh đại diện. Xoá tệp là việc của module media, gọi sau khi transaction thành công. */
    public void detachAvatar(Instant now) {
        this.avatarStorageKey = null;
        this.avatarStorageProvider = null;
        this.updatedAt = now;
    }

    /** D5 — xoá mềm. Mèo chính thì bỏ cờ trước, vì index một phần đang lọc {@code is_primary}. */
    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.primary = false;
        this.updatedAt = now;
    }

    // ---------------------------------------------------------------- getters

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Integer getApproxAgeMonths() {
        return approxAgeMonths;
    }

    public String getBreedCode() {
        return breedCode;
    }

    public String getBreedOther() {
        return breedOther;
    }

    public String getCoatColor() {
        return coatColor;
    }

    public CatSex getSex() {
        return sex;
    }

    public Boolean getNeutered() {
        return neutered;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public Instant getWeightUpdatedAt() {
        return weightUpdatedAt;
    }

    public String getAvatarStorageKey() {
        return avatarStorageKey;
    }

    public AvatarStorageProvider getAvatarStorageProvider() {
        return avatarStorageProvider;
    }

    public String getPublicCode() {
        return publicCode;
    }

    public CatStatus getStatus() {
        return status;
    }

    public boolean isPrimary() {
        return primary;
    }

    public String getNotes() {
        return notes;
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

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isArchived() {
        return status == CatStatus.ARCHIVED;
    }

    public boolean hasAvatar() {
        return avatarStorageKey != null;
    }

    /** Khoá ảnh dạng thô để tầng application dựng lại {@code StorageKey}; rỗng nếu chưa có ảnh. */
    public Optional<String> avatarKeyValue() {
        return Optional.ofNullable(avatarStorageKey);
    }

    /**
     * Tuổi tính theo tháng, luôn lấy từ MỘT nguồn (p8 §8.5.3).
     *
     * @param today ngày tính, luôn do {@link Clock} cấp chứ không gọi {@code LocalDate.now()}
     */
    public Integer ageMonths(LocalDate today) {
        if (birthDate != null) {
            if (birthDate.isAfter(today)) {
                return null;
            }
            return (int) Period.between(birthDate, today).toTotalMonths();
        }
        return approxAgeMonths;
    }

    // ---------------------------------------------------------------- internals

    private void applyName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("cat.name không được rỗng");
        }
        String trimmed = value.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("cat.name dài tối đa " + NAME_MAX_LENGTH + " ký tự");
        }
        this.name = trimmed;
    }

    private void applyAgeSource(LocalDate birthDate, Integer approxAgeMonths, LocalDate today) {
        if (birthDate != null && approxAgeMonths != null) {
            throw new IllegalArgumentException(
                    "Chỉ chọn một cách nhập tuổi: birthDate hoặc approxAgeMonths (CAT_AGE_CONFLICT)");
        }
        if (birthDate == null && approxAgeMonths == null) {
            throw new IllegalArgumentException("Phải có birthDate hoặc approxAgeMonths");
        }
        if (birthDate != null && birthDate.isAfter(today)) {
            throw new IllegalArgumentException("cat.birthDate không được ở tương lai");
        }
        if (approxAgeMonths != null && (approxAgeMonths < 0 || approxAgeMonths > MAX_AGE_MONTHS)) {
            throw new IllegalArgumentException("cat.approxAgeMonths phải nằm trong 0.." + MAX_AGE_MONTHS);
        }
        this.birthDate = birthDate;
        this.approxAgeMonths = approxAgeMonths;
    }

    private void applyAgePatch(LocalDate birthDate, boolean ageTouched, Integer approxAgeMonths, LocalDate today) {
        if (!ageTouched) {
            return;
        }
        if (birthDate != null) {
            if (birthDate.isAfter(today)) {
                throw new IllegalArgumentException("cat.birthDate không được ở tương lai");
            }
            this.birthDate = birthDate;
            this.approxAgeMonths = null;
            return;
        }
        if (approxAgeMonths == null || approxAgeMonths < 0 || approxAgeMonths > MAX_AGE_MONTHS) {
            throw new IllegalArgumentException("cat.approxAgeMonths phải nằm trong 0.." + MAX_AGE_MONTHS);
        }
        this.approxAgeMonths = approxAgeMonths;
        this.birthDate = null;
    }

    private void applyWeight(BigDecimal value, Instant now) {
        if (value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(new BigDecimal("30")) >= 0) {
            throw new IllegalArgumentException("cat.weightKg phải nằm trong (0, 30)");
        }
        this.weightKg = value;
        this.weightUpdatedAt = now;
    }

    private static String requirePublicCode(String value) {
        if (value == null || !value.startsWith(PUBLIC_CODE_PREFIX)
                || value.length() != PUBLIC_CODE_PREFIX.length() + PUBLIC_CODE_LENGTH) {
            throw new IllegalArgumentException("cat.publicCode phải có dạng " + PUBLIC_CODE_PREFIX + "xxxxxx");
        }
        return value;
    }

    /** Sinh mã hiển thị từ nguồn ngẫu nhiên — nhận entropy từ bên ngoài để domain không tự gọi RNG. */
    public static String newPublicCode(Entropy entropy) {
        StringBuilder code = new StringBuilder(PUBLIC_CODE_PREFIX);
        for (int i = 0; i < PUBLIC_CODE_LENGTH; i++) {
            code.append(CROCKFORD_ALPHABET.charAt(entropy.nextIndex(CROCKFORD_ALPHABET.length())));
        }
        return code.toString();
    }

    /** Nguồn ngẫu nhiên, tiêm vào để domain không phụ thuộc {@code SecureRandom} (kiểm thử được). */
    @FunctionalInterface
    public interface Entropy {
        int nextIndex(int bound);
    }
}
