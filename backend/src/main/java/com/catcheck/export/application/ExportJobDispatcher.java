package com.catcheck.export.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.UUID;

@Component
public class ExportJobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ExportJobDispatcher.class);

    private final TaskExecutor exportExecutor;
    private final ExportGenerationService generationService;
    private final Clock clock;

    public ExportJobDispatcher(
            @Qualifier("exportExecutor") TaskExecutor exportExecutor,
            ExportGenerationService generationService,
            Clock clock) {
        this.exportExecutor = exportExecutor;
        this.generationService = generationService;
        this.clock = clock;
    }

    public void dispatchAfterCommit(UUID jobId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            submit(jobId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit(jobId);
            }
        });
    }

    private void submit(UUID jobId) {
        try {
            exportExecutor.execute(() -> generationService.generate(jobId));
        } catch (TaskRejectedException rejected) {
            log.warn("export_job {} bi tu choi khoi hang doi: {}", jobId, rejected.getMessage());
            generationService.markRejected(jobId, clock.instant());
        }
    }
}
