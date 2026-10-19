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

@Entity
@Table(name = "cat")
public class Cat {

    public static final String PUBLIC_CODE_PREFIX = "CC-VN-";

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

    protected Cat() {
    }

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

    public void archive(Instant now) {
        this.status = CatStatus.ARCHIVED;
        this.updatedAt = now;
    }

    public void unarchive(Instant now) {
        this.status = CatStatus.ACTIVE;
        this.updatedAt = now;
    }

    public void makePrimary(Instant now) {
        this.primary = true;
        this.updatedAt = now;
    }

    public void clearPrimary(Instant now) {
        this.primary = false;
        this.updatedAt = now;
    }

    public void attachAvatar(String storageKey, AvatarStorageProvider provider, Instant now) {
        this.avatarStorageKey = Objects.requireNonNull(storageKey, "cat.avatarStorageKey phải có giá trị");
        this.avatarStorageProvider = provider;
        this.updatedAt = now;
    }

    public void detachAvatar(Instant now) {
        this.avatarStorageKey = null;
        this.avatarStorageProvider = null;
        this.updatedAt = now;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.primary = false;
        this.updatedAt = now;
    }


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

    public Optional<String> avatarKeyValue() {
        return Optional.ofNullable(avatarStorageKey);
    }

    public Integer ageMonths(LocalDate today) {
        if (birthDate != null) {
            if (birthDate.isAfter(today)) {
                return null;
            }
            return (int) Period.between(birthDate, today).toTotalMonths();
        }
        return approxAgeMonths;
    }


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
            throw new CatAgeConflictException();
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
        if (birthDate != null && approxAgeMonths != null) {
            throw new CatAgeConflictException();
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

    public static String newPublicCode(Entropy entropy) {
        StringBuilder code = new StringBuilder(PUBLIC_CODE_PREFIX);
        for (int i = 0; i < PUBLIC_CODE_LENGTH; i++) {
            code.append(CROCKFORD_ALPHABET.charAt(entropy.nextIndex(CROCKFORD_ALPHABET.length())));
        }
        return code.toString();
    }

    @FunctionalInterface
    public interface Entropy {
        int nextIndex(int bound);
    }
}
