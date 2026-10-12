package com.catcheck.scan.domain.port;

import com.catcheck.scan.domain.ScanReassignment;

import java.util.List;
import java.util.UUID;

public interface ScanReassignmentRepository {

    ScanReassignment save(ScanReassignment reassignment);

    List<ScanReassignment> findByScanIdOrderByChangedAtDesc(UUID scanId);

    long countByScanId(UUID scanId);
}
