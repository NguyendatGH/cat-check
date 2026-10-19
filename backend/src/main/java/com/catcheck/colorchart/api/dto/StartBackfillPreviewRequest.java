package com.catcheck.colorchart.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StartBackfillPreviewRequest(
        @Pattern(regexp = "^[Pp](\\d+[Yy])?(\\d+[Mm])?(\\d+[Ww])?(\\d+[Dd])?$",
                message = "{validation.colorchart.window.format}")
        String window,

        @NotBlank
        @Size(min = 10, max = 500)
        String reason
) {
}
