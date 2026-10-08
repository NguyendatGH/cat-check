package com.catcheck.community.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CommunityReactionRequest(@NotBlank @Pattern(regexp = "(?i)LIKE|BOOKMARK") String reaction, boolean active) { }
