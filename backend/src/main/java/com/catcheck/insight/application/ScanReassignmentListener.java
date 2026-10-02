package com.catcheck.insight.application;

import com.catcheck.insight.domain.port.HealthFlagRepository;
import com.catcheck.scan.api.ScanReassignedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Thu hồi {@code health_flag} mồ côi khi một scan bị gán lại cho mèo khác (p6 §6.10.3).
 *
 * <p>Phạm vi MVP (xem javadoc {@link ScanReassignedEvent}): chỉ xoá các flag có
 * {@code trigger_scan_id} = scan vừa đổi — KHÔNG tính lại toàn bộ rule R1-R3 cho cả hai mèo.</p>
 */
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
