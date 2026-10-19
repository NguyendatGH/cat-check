package com.catcheck.insight.application;

import com.catcheck.insight.domain.port.HealthFlagRepository;
import com.catcheck.scan.api.ScanReassignedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class ScanReassignmentListener {

    private final HealthFlagRepository healthFlagRepository;

    public ScanReassignmentListener(HealthFlagRepository healthFlagRepository) {
        this.healthFlagRepository = healthFlagRepository;
    }

    @EventListener
    public void onScanReassigned(ScanReassignedEvent event) {
        healthFlagRepository.deleteByTriggerScanId(event.scanId());
    }
}
