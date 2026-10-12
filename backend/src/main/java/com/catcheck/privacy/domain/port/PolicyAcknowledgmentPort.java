package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.PolicyAcknowledgment;
import com.catcheck.privacy.domain.PolicySurface;

import java.util.UUID;

public interface PolicyAcknowledgmentPort {

    void append(PolicyAcknowledgment acknowledgment);

    boolean exists(UUID userId, UUID policyVersionId, PolicySurface surface);
}
