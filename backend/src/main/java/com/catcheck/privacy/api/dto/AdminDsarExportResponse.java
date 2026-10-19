package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.application.export.DataExportJobService;

public record AdminDsarExportResponse(String publicRef, String status, Boolean dispatched) {

    public static AdminDsarExportResponse from(DataExportJobService.AdminDispatch dispatch) {
        return new AdminDsarExportResponse(dispatch.publicRef(), dispatch.status(), dispatch.dispatched());
    }
}
