package com.catcheck.privacy.application;

import com.catcheck.privacy.spi.RegistrationConsentEvent;
import com.catcheck.privacy.domain.ConsentMethod;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserRegisteredEventListener {

    private static final String SURFACE_REGISTER = "register";
    private static final String DEFAULT_LOCALE = "vi";

    private final ConsentService consentService;

    public UserRegisteredEventListener(ConsentService consentService) {
        this.consentService = consentService;
    }

    @EventListener
    public void onUserRegistered(RegistrationConsentEvent event) {
        if (event.consents() == null || event.consents().isEmpty()) {
            return;
        }
        List<ConsentGrant> grants = event.consents().stream()
                .map(grant -> new ConsentGrant(grant.purposeCode(), grant.granted()))
                .toList();
        String locale = event.locale() != null && !event.locale().isBlank()
                ? event.locale() : DEFAULT_LOCALE;
        consentService.recordConsents(
                event.userId(),
                grants,
                ConsentMethod.WEB_CHECKBOX,
                SURFACE_REGISTER,
                locale,
                RequestEvidence.of(event.requestId(), event.ipAddress(), event.userAgent()));
    }
}
