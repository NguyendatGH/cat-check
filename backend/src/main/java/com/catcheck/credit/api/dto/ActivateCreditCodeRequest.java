package com.catcheck.credit.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record ActivateCreditCodeRequest(
        @NotBlank(message = "{credit.activation.code.required}")
        @Size(max = 40, message = "{credit.activation.code.too-long}")
        @Pattern(
                regexp = "^[\\x20-\\x7E]+$",
                message = "{credit.activation.code.malformed}")
        String code
) {
}
