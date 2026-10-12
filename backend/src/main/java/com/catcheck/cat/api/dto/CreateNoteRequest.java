package com.catcheck.cat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateNoteRequest(
        String noteType,
        @NotBlank(message = "Nội dung ghi chú không được để trống")
        @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự")
        String body,
        LocalDate occurredOn
) {
}
