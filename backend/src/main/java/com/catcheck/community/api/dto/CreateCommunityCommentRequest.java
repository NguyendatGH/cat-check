package com.catcheck.community.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** JSON comment without an image; image comments go through the multipart variant of the same endpoint. */
public record CreateCommunityCommentRequest(@NotBlank @Size(max = 4000) String body, UUID parentCommentId) { }
