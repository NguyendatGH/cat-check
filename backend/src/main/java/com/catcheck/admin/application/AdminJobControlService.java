package com.catcheck.admin.application;

import com.catcheck.admin.api.AdminOpsErrorCode;
import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobRunStatus;
import com.catcheck.shared.job.ManualJobTrigger;
import com.catcheck.shared.job.application.ManualJobLauncher;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * L65 {@code POST /api/v1/admin/jobs/{jobName}/run} — kich hoat thu cong mot job nen, ghi
 * {@code job_run.trigger_type = MANUAL} (p8 §8.4.12 muc (f), p14 §14.2.2 o Q26, p4 §K3).
 *
 * <p><b>Khong {@code @Transactional}.</b> Than job tu quan transaction cua no (thuong mot
 * transaction moi item, p12 §12.6.1 quy tac 3), va dong {@code job_run} duoc
 * {@code JobRunRecorder} mo/dong o {@code REQUIRES_NEW} de ton tai bat ke nghiep vu ben trong
 * thanh hay bai. Boc ca lan chay trong mot transaction cua tang nay se pha ca hai thiet ke do, va
 * mot job chay 10 phut se giu mot connection suot 10 phut.</p>
 *
 * <p><b>Hau qua: audit duoc ghi SAU khi job chay xong</b>, khong phai trong cung transaction nhu
 * ky hieu {@code Aud} cua p8 §8.3.2 mo ta ("audit fail ⇒ rollback ca hanh dong"). Day la mot cho
 * <b>khong the thoa man dong thoi</b>: mot lan chay job khong phai mot transaction, nen khong co
 * transaction nao de rollback. Bu lai, chinh dong {@code job_run} la dau vet khong the mat (no
 * duoc ghi o transaction rieng truoc khi than job chay), va audit mang ca
 * {@code jobRunStatus} nen hai nguon doi chieu duoc. Ghi handoff H15.182.</p>
 */
@Service
public class AdminJobControlService {

    private final ManualJobLauncher launcher;
    private final AuditLogService auditLogService;

    public AdminJobControlService(ManualJobLauncher launcher, AuditLogService auditLogService) {
        this.launcher = launcher;
        this.auditLogService = auditLogService;
    }

    /**
     * @param dryRun chi dem, khong ghi — than job phai tu ton trong (p15 REQ-RET-01); lan chay
     *               dry-run <b>van</b> de lai mot dong {@code job_run} (p4 §K3)
     * @throws NotFoundException {@code 404 JOB_NOT_FOUND} — ten job khong co trong danh muc
     * @throws ConflictException {@code 409 JOB_NOT_MANUALLY_RUNNABLE} — job co that nhung chua
     *                           dang ky {@code ManualJobTrigger}; hoac {@code 409 JOBS_DISABLED}
     */
    public JobOutcome run(String jobName, boolean dryRun, UUID adminId, String adminRole,
                          String reason, String requestId, String ipAddress, String userAgent) {
        if (!launcher.isKnownJob(jobName)) {
            throw new NotFoundException(AdminOpsErrorCode.JOB_NOT_FOUND, jobName);
        }
        if (!launcher.jobsEnabled()) {
            throw new ConflictException(AdminOpsErrorCode.JOBS_DISABLED, jobName);
        }
        ManualJobTrigger trigger = launcher.findRunnable(jobName)
                .orElseThrow(() -> new ConflictException(
                        AdminOpsErrorCode.JOB_NOT_MANUALLY_RUNNABLE,
                        String.join(", ", launcher.runnableJobNames())));

        JobOutcome outcome = launcher.run(trigger, dryRun);

        // outcome.status() = FAILED nghia la than job da nem; JobRunner da bat va dong dong
        // job_run. Audit phai phan anh dieu do (ERROR, khong phai SUCCESS) — neu khong, so audit
        // noi "admin da chay job thanh cong" trong khi job that bai.
        auditLogService.record(AuditEvent.builder()
                .actor(AuditActor.admin(adminId, adminRole))
                .subject(AuditSubjectType.SYSTEM, null)
                .action("ADMIN_JOB_RUN_TRIGGERED")
                .outcome(outcome.status() == JobRunStatus.FAILED ? AuditOutcome.ERROR : AuditOutcome.SUCCESS)
                .requestId(requestId).ipAddress(ipAddress).userAgent(userAgent)
                .meta("jobName", jobName)
                .meta("triggerType", "MANUAL")
                .meta("dryRun", dryRun)
                .meta("jobRunStatus", outcome.status().name())
                .meta("itemsProcessed", outcome.itemsProcessed())
                .meta("itemsDeleted", outcome.itemsDeleted())
                .meta("itemsFailed", outcome.itemsFailed())
                .meta("reason", reason)
                .build());
        return outcome;
    }
}
