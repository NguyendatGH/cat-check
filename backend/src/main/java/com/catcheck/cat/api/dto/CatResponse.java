package com.catcheck.cat.api.dto;

import com.catcheck.cat.domain.Cat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * D3, D4, D6, D7, D8, D10, D11 — trả về hồ sơ mèo.
 *
 * <p><b>DTO là record</b> (R14). Không có JPA annotation, không nhắm tới entity — domain ở đâu
 * thì domain ở đó.</p>
 */
public record CatResponse(
        String id,
        String publicCode,
        String name,
        LocalDate birthDate,
        Integer approxAgeMonths,
        Integer ageMonths,
        String breedCode,
        String breedName,
        String breedOther,
        String coatColor,
        String sex,
        Boolean neutered,
        BigDecimal weightKg,
        Instant weightUpdatedAt,
        String avatarUrl,
        String status,
        boolean isPrimary,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * @param today dùng để tính {@code ageMonths} — luôn lấy từ {@link java.time.Clock} đã inject
     *              ở tầng gọi (controller), domain/DTO không tự đọc đồng hồ hệ thống (R13)
     * @param breedName tên giống theo locale hiện tại, {@code null} nếu hồ sơ không chọn giống
     * @param avatarUrl URL đã ký (nếu có ảnh), {@code null} nếu chưa gắn ảnh đại diện
     */
    public static CatResponse from(Cat cat, String breedName, String avatarUrl, LocalDate today) {
        return new CatResponse(
                cat.getId().toString(),
                cat.getPublicCode(),
                cat.getName(),
                cat.getBirthDate(),
                cat.getApproxAgeMonths(),
                cat.ageMonths(today),
                cat.getBreedCode(),
                breedName,
                cat.getBreedOther(),
                cat.getCoatColor(),
                cat.getSex().name(),
                cat.getNeutered(),
                cat.getWeightKg(),
                cat.getWeightUpdatedAt(),
                avatarUrl,
                cat.getStatus().name(),
                cat.isPrimary(),
                cat.getNotes(),
                cat.getCreatedAt(),
                cat.getUpdatedAt());
    }
}
