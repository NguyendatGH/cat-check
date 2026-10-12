package com.catcheck.identity.api.dto;

public record UpdateProfileRequest(
        String fullName,
        String phone,
        String locale,
        String timezone) {
}
