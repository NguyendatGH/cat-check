package com.catcheck.privacy.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RecordConsentsRequest(
        @NotEmpty(message = "{validation.privacy.consents.required}")
        @Valid
        List<ConsentGrantRequest> consents
) {
}
