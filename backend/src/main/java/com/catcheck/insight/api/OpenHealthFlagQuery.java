package com.catcheck.insight.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OpenHealthFlagQuery {

    List<OpenFlag> openFlagsFor(UUID userId, UUID catId, int limit);

    record OpenFlag(String ruleCode, String severity, Instant triggeredAt) {
    }
}
