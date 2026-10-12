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

@Service
public class AdminJobControlService {

    private final ManualJobLauncher launcher;
    private final AuditLogService auditLogService;

    public AdminJobControlService(ManualJobLauncher launcher, AuditLogService auditLogService) {
        this.launcher = launcher;
        this.auditLogService = auditLogService;
    }

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
