package com.catcheck.community.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CommunityReportRequest(UUID postId, UUID commentId, @NotBlank @Size(max = 32) String reason, @Size(max = 1000) String details) { }
