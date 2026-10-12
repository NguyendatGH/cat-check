package com.catcheck.identity.application.privacy;

import com.catcheck.identity.domain.port.AuthenticatedSessionGateway;
import com.catcheck.privacy.spi.StepUpVerificationPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class PrivacyStepUpVerificationAdapter implements StepUpVerificationPort {

    private final AuthenticatedSessionGateway sessionGateway;

    PrivacyStepUpVerificationAdapter(AuthenticatedSessionGateway sessionGateway) {
        this.sessionGateway = sessionGateway;
    }

    @Override
    public boolean isVerified(UUID userId, String scope) {
        return sessionGateway.currentUserId()
                .filter(userId::equals)
                .map(ignored -> sessionGateway.isReauthenticated())
                .orElse(Boolean.FALSE);
    }
}
