package com.catcheck.community.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CommunityModerationRequest(@NotBlank String action, @NotBlank String reason) { }
