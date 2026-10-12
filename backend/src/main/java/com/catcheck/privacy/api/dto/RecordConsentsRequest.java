package com.catcheck.privacy.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RecordConsentsRequest(
        @NotEmpty(message = "consents không được rỗng")
        @Valid
        List<ConsentGrantRequest> consents
) {
}
