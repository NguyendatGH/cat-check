package com.catcheck.community.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CommunityReactionRequest(@NotBlank String reaction, boolean active) { }
