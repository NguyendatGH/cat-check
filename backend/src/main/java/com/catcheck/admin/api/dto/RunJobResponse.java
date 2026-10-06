package com.catcheck.admin.api.dto;

/**
 * Ket qua L65. Tra ve <b>ket qua that cua lan chay</b>, khong phai mot {@code 202} rong: job
 * chay dong bo trong request nay (chung deu la job theo lo, co tran {@code maxItemsPerRun}), nen
 * nguoi bam nut doc duoc ngay "da xet bao nhieu, xoa bao nhieu, loi bao nhieu" thay vi phai mo
 * man L64 doi.
 *
 * @param status      {@code SUCCESS} | {@code PARTIAL} | {@code FAILED} | {@code SKIPPED_THRESHOLD}
 * @param triggerType luon {@code MANUAL} — p8 L65. Tra ve tuong minh de client doi chieu duoc voi
 *                    dong {@code job_run} tuong ung o L64
 * @param errorSummary tom tat NGAN, khong stack trace va khong PII (p4 §K3)
 */
public record RunJobResponse(
        String jobName,
        String status,
        String triggerType,
        Boolean dryRun,
        Integer itemsProcessed,
        Integer itemsDeleted,
        Integer itemsFailed,
        String errorSummary) {
}
