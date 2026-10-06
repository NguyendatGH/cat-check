package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.application.export.DataExportJobService;

/**
 * Kết quả L52 — {@code 202 Accepted}: gói dữ liệu cá nhân được sinh ở job nền
 * ({@code DataExportJob}, p15 §15.4.5), không sinh đồng bộ trong request.
 *
 * <p><b>Không có field nào chứa token tải.</b> Link một lần đi tới
 * {@code dsar_request.contact_email} qua email {@code PRIVACY_EXPORT_READY} — xem javadoc
 * {@code DataExportJobService#enqueueOnBehalf}.</p>
 *
 * @param publicRef  mã tra cứu {@code DSAR-2026-000123}
 * @param status     trạng thái {@code dsar_export_job} sau lệnh
 * @param dispatched {@code false} = đã có gói/job nên lệnh này không sinh gói thứ hai
 */
public record AdminDsarExportResponse(String publicRef, String status, Boolean dispatched) {

    public static AdminDsarExportResponse from(DataExportJobService.AdminDispatch dispatch) {
        return new AdminDsarExportResponse(dispatch.publicRef(), dispatch.status(), dispatch.dispatched());
    }
}
