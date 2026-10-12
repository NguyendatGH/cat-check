package com.catcheck.colorchart.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StartBackfillPreviewRequest(
        @Pattern(regexp = "^[Pp](\\d+[Yy])?(\\d+[Mm])?(\\d+[Ww])?(\\d+[Dd])?$",
                message = "window phải là chu kỳ ISO-8601, ví dụ P90D")
        String window,

        @NotBlank
        @Size(min = 10, max = 500)
        String reason
) {
}
