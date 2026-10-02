package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** A1 — đăng ký tài khoản (p8 §8.4.4 nhóm A). */
public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(min = 1, max = 120) String fullName,
        String phone,
        String referralCodeRaw,
        String locale,
        String otpTicket,
        List<ConsentGrant> consents) {

    public record ConsentGrant(
            @NotBlank String purposeCode,
            boolean granted) {
    }
}
