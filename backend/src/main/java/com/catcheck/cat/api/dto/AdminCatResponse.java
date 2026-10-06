package com.catcheck.cat.api.dto;

import com.catcheck.cat.application.AdminFreeTextMask;
import com.catcheck.cat.domain.Cat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Một hồ sơ mèo như màn quản trị thấy — {@code GET /api/v1/admin/users/{userId}/cats} (p8 L4).
 *
 * <p><b>Không có {@code avatarStorageKey}.</b> p8 §8.2.5 mục 3 cấm để {@code storage_key} rời
 * khỏi server; ở đây chỉ trả cờ {@link #hasAvatar} — tổng đài cần biết "bé này có ảnh chưa", chứ
 * không cần tự tải ảnh.</p>
 *
 * <p>Văn bản tự do ({@code name}, {@code breedOther}, {@code notes}) đi qua
 * {@link AdminFreeTextMask} khi {@code fullData = false}, đúng p11 §11.5.4 dòng "Hồ sơ mèo".
 * {@code coatColor} <b>không</b> bị che: nó là mô tả màu lông, không định danh ai, và là thứ
 * tổng đài dùng để xác nhận đang nói về đúng bé.</p>
 *
 * @param catId            id hồ sơ
 * @param publicCode       mã người dùng đọc được (chính là mã họ nói qua điện thoại)
 * @param name             tên bé — đã che khi {@code fullData = false}
 * @param status           {@code ACTIVE} | {@code ARCHIVED}
 * @param primary          có phải bé chính không
 * @param birthDate        ngày sinh, {@code null} nếu chủ chỉ nhập tuổi xấp xỉ
 * @param approxAgeMonths  tuổi xấp xỉ theo tháng, {@code null} nếu đã có ngày sinh
 * @param breedCode        mã giống trong danh mục
 * @param breedOther       giống tự nhập khi chọn "khác" — đã che khi {@code fullData = false}
 * @param coatColor        màu lông
 * @param sex              giới tính
 * @param neutered         đã triệt sản chưa, {@code null} nếu chủ chưa trả lời
 * @param weightKg         cân nặng gần nhất
 * @param weightUpdatedAt  lúc cập nhật cân nặng
 * @param notes            ghi chú của chủ — đã che khi {@code fullData = false}
 * @param hasAvatar        có ảnh đại diện không
 * @param createdAt        lúc tạo hồ sơ
 * @param updatedAt        lúc sửa gần nhất
 */
public record AdminCatResponse(
        String catId,
        String publicCode,
        String name,
        String status,
        boolean primary,
        LocalDate birthDate,
        Integer approxAgeMonths,
        String breedCode,
        String breedOther,
        String coatColor,
        String sex,
        Boolean neutered,
        BigDecimal weightKg,
        Instant weightUpdatedAt,
        String notes,
        boolean hasAvatar,
        Instant createdAt,
        Instant updatedAt
) {

    public static AdminCatResponse from(Cat cat, boolean fullData) {
        return new AdminCatResponse(
                cat.getId().toString(),
                cat.getPublicCode(),
                fullData ? cat.getName() : AdminFreeTextMask.text(cat.getName()),
                cat.getStatus() == null ? null : cat.getStatus().name(),
                cat.isPrimary(),
                cat.getBirthDate(),
                cat.getApproxAgeMonths(),
                cat.getBreedCode(),
                fullData ? cat.getBreedOther() : AdminFreeTextMask.text(cat.getBreedOther()),
                cat.getCoatColor(),
                cat.getSex() == null ? null : cat.getSex().name(),
                cat.getNeutered(),
                cat.getWeightKg(),
                cat.getWeightUpdatedAt(),
                fullData ? cat.getNotes() : AdminFreeTextMask.text(cat.getNotes()),
                cat.getAvatarStorageKey() != null,
                cat.getCreatedAt(),
                cat.getUpdatedAt());
    }
}
