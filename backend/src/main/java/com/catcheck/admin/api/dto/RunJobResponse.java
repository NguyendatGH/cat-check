package com.catcheck.admin.api.dto;

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
