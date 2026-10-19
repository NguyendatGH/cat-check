package com.catcheck.shared.job;

public record JobOutcome(
        JobRunStatus status,
        int itemsProcessed,
        int itemsDeleted,
        int itemsFailed,
        String errorSummary
) {

    public static final int MAX_ERROR_SUMMARY_LENGTH = 500;

    public JobOutcome {
        if (status == null) {
            throw new IllegalArgumentException("jobOutcome.status phải có giá trị");
        }
        if (status == JobRunStatus.RUNNING) {
            throw new IllegalArgumentException("jobOutcome không được mang trạng thái RUNNING");
        }
        if (itemsProcessed < 0 || itemsDeleted < 0 || itemsFailed < 0) {
            throw new IllegalArgumentException("jobOutcome: số đếm không được âm (ck_job_run_count)");
        }
        errorSummary = truncate(errorSummary);
    }

    public static JobOutcome of(int itemsProcessed, int itemsDeleted, int itemsFailed, String errorSummary) {
        JobRunStatus status = itemsFailed == 0 ? JobRunStatus.SUCCESS : JobRunStatus.PARTIAL;
        return new JobOutcome(status, itemsProcessed, itemsDeleted, itemsFailed, errorSummary);
    }

    public static JobOutcome success(int itemsProcessed) {
        return new JobOutcome(JobRunStatus.SUCCESS, itemsProcessed, 0, 0, null);
    }

    public static JobOutcome failed(String errorSummary) {
        return new JobOutcome(JobRunStatus.FAILED, 0, 0, 0, errorSummary);
    }

    public static JobOutcome skippedThreshold(int itemsProcessed, String errorSummary) {
        return new JobOutcome(JobRunStatus.SKIPPED_THRESHOLD, itemsProcessed, 0, 0, errorSummary);
    }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= MAX_ERROR_SUMMARY_LENGTH
                ? value
                : value.substring(0, MAX_ERROR_SUMMARY_LENGTH);
    }
}
