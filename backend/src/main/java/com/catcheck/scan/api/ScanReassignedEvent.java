package com.catcheck.scan.api;

import java.util.UUID;

public record ScanReassignedEvent(UUID scanId, UUID fromCatId, UUID toCatId) {
}
