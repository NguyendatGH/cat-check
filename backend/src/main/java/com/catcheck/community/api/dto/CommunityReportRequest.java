package com.catcheck.community.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CommunityReportRequest(UUID postId, UUID commentId, @NotBlank @Pattern(regexp = "SPAM|MISLEADING|HARASSMENT|PRIVACY|OTHER") String reason, @Size(max = 1000) String details) { }
