package com.catcheck.export.domain;

/** Trạng thái job xuất PDF — cột {@code export_job.status} (p4 G1). */
public enum ExportStatus {
    QUEUED,
    RUNNING,
    READY,
    FAILED,
    EXPIRED
}
