package com.catcheck.community.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommunityCommentRequest(@NotBlank @Size(max = 4000) String body) { }
