package com.catcheck.admin.api.dto;

import com.catcheck.admin.domain.AppSettingRow;

import java.time.Instant;

/**
 * Mot khoa cau hinh — L69 {@code GET /admin/settings}.
 *
 * <p><b>{@code value} o day LUON la gia tri da che neu {@code secret = true}</b>: factory
 * {@link #from} chi nhan dong da di qua {@code AppSettingRow.masked()} (xem
 * {@code AdminSettingsService.listMasked}). p8 L69 ghi dung mot dieu kien cho endpoint nay —
 * "gia tri {@code secret = true} bi che" — nen do la bat bien cua chinh DTO.
 *
 * <p><b>{@code etag} nam trong body, khong chi o header.</b> L69 tra ve mot DANH SACH, nen mot
 * header {@code ETag} duy nhat khong dai dien cho dong nao; ma L70 lai doi {@code If-Match} cua
 * <b>tung khoa</b>. Khong gui etag theo tung dong thi client phai gọi L69 mot lan nua cho moi
 * khoa truoc khi sua no.
 *
 * @param updatedBy co y KHONG tra ve: do la {@code app_user.id} cua mot admin khac, va L69 khong
 *                  can tiet lo ai sua gi (cau do thuoc L68 {@code audit_log}, noi co phan quyen
 *                  theo role rieng)
 */
public record AppSettingResponse(
        String key,
        String value,
        String valueType,
        String description,
        Boolean secret,
        Instant updatedAt,
        String etag) {

    public static AppSettingResponse from(AppSettingRow maskedRow, String etag) {
        return new AppSettingResponse(
                maskedRow.key(), maskedRow.value(), maskedRow.valueType(), maskedRow.description(),
                maskedRow.secret(), maskedRow.updatedAt(), etag);
    }
}
