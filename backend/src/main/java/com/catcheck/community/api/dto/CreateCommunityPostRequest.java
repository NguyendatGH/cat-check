package com.catcheck.community.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateCommunityPostRequest(
        @NotBlank @Pattern(regexp = "(?i)QA|TIP|EXPERIENCE") String category,
        @NotBlank @Size(max = 180) String title,
        @NotBlank @Size(max = 10000) String body,
        @Size(max = 8) List<@NotBlank @Size(max = 48) String> tags) { }
