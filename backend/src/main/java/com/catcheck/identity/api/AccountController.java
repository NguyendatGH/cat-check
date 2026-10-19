package com.catcheck.identity.api;

import com.catcheck.identity.api.dto.ChangePasswordRequest;
import com.catcheck.identity.api.dto.EmailChangeConfirmRequest;
import com.catcheck.identity.api.dto.EmailChangeRequestRequest;
import com.catcheck.identity.api.dto.IdentityItem;
import com.catcheck.identity.api.dto.NotificationPreferencesResponse;
import com.catcheck.identity.api.dto.ProfileResponse;
import com.catcheck.identity.api.dto.TotpVerifyRequest;
import com.catcheck.identity.api.dto.UpdateNotificationPreferencesRequest;
import com.catcheck.identity.api.dto.UpdateProfileRequest;
import com.catcheck.identity.application.AuthPrincipal;
import com.catcheck.identity.application.AuthRequestContext;
import com.catcheck.identity.application.EmailChangeService;
import com.catcheck.identity.application.MfaService;
import com.catcheck.identity.application.ProfileService;
import com.catcheck.identity.application.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AccountController {

    private final ProfileService profileService;
    private final EmailChangeService emailChangeService;
    private final MfaService mfaService;
    private final SessionService sessionService;
    private final com.catcheck.audit.api.AuditAccessLogQuery auditAccessLogQuery;

    public AccountController(ProfileService profileService,
                             EmailChangeService emailChangeService,
                             MfaService mfaService,
                             SessionService sessionService,
                             com.catcheck.audit.api.AuditAccessLogQuery auditAccessLogQuery) {
        this.auditAccessLogQuery = auditAccessLogQuery;
        this.profileService = profileService;
        this.emailChangeService = emailChangeService;
        this.mfaService = mfaService;
        this.sessionService = sessionService;
    }


    @Operation(operationId = "getUsersMe", summary = "Ho so cua chinh minh")
    @GetMapping("/users/me")
    public ProfileResponse myProfile(@AuthenticationPrincipal AuthPrincipal principal) {
        ProfileService.ProfileView view = profileService.profile(principal.userId());
        return new ProfileResponse(
                view.id(), view.email(), view.fullName(), view.phone(), view.locale(),
                view.timezone(), view.status(), view.onboardingStatus(), view.emailVerified(),
                view.identities(), view.createdAt());
    }

    @Operation(operationId = "patchUsersMe", summary = "Sua ho so (merge-patch)")
    @PatchMapping("/users/me")
    public ProfileResponse updateMyProfile(
            @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        ProfileService.ProfileView view = profileService.updateProfile(
                principal.userId(), request.fullName(), request.phone(),
                request.locale(), request.timezone());
        return new ProfileResponse(
                view.id(), view.email(), view.fullName(), view.phone(), view.locale(),
                view.timezone(), view.status(), view.onboardingStatus(), view.emailVerified(),
                view.identities(), view.createdAt());
    }


    @Operation(operationId = "getUsersMeAvatar", summary = "Anh dai dien")
    @GetMapping("/users/me/avatar")
    public ResponseEntity<byte[]> myAvatar(@AuthenticationPrincipal AuthPrincipal principal) {
        ProfileService.AvatarData avatar = profileService.readAvatar(principal.userId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .body(avatar.content());
    }

    @Operation(operationId = "putUsersMeAvatar", summary = "Tai anh dai dien")
    @PutMapping("/users/me/avatar")
    public Map<String, Object> uploadMyAvatar(@RequestParam("file") MultipartFile file,
                                              @AuthenticationPrincipal AuthPrincipal principal) {
        try {
            String storageKey = profileService.uploadAvatar(
                    principal.userId(), file.getBytes(), file.getContentType());
            return Map.of("avatarUrl", "/api/v1/users/me/avatar");
        } catch (java.io.IOException ex) {
            throw new com.catcheck.shared.error.BusinessRuleException(
                    IdentityErrorCode.AVATAR_INVALID);
        }
    }

    @Operation(operationId = "deleteUsersMeAvatar", summary = "Go anh dai dien")
    @DeleteMapping("/users/me/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMyAvatar(@AuthenticationPrincipal AuthPrincipal principal) {
        profileService.deleteAvatar(principal.userId());
    }


    @Operation(operationId = "postAccountPassword", summary = "Doi mat khau")
    @PostMapping("/account/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
                               @AuthenticationPrincipal AuthPrincipal principal,
                               HttpServletRequest httpRequest) {
        sessionService.changePassword(principal.userId(), request.currentPassword(),
                request.newPassword(), context(httpRequest));
    }


    @Operation(operationId = "postAccountEmailChangeRequest", summary = "Yeu cau doi email")
    @PostMapping("/account/email-change/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> requestEmailChange(
            @Valid @RequestBody EmailChangeRequestRequest request,
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest) {
        emailChangeService.requestChange(principal.userId(), request.newEmail(),
                context(httpRequest));
        return Map.of("otpExpiresAt", "", "maskedNewEmail", maskEmail(request.newEmail()));
    }

    @Operation(operationId = "postAccountEmailChangeConfirm", summary = "Xac nhan doi email")
    @PostMapping("/account/email-change/confirm")
    public Map<String, Object> confirmEmailChange(
            @Valid @RequestBody EmailChangeConfirmRequest request,
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest) {
        emailChangeService.confirmChange(principal.userId(), request.otpTicket(),
                context(httpRequest));
        return Map.of("emailChanged", true);
    }


    @Operation(operationId = "getAccountIdentities", summary = "Phuong thuc dang nhap da lien ket")
    @GetMapping("/account/identities")
    public Map<String, Object> myIdentities(@AuthenticationPrincipal AuthPrincipal principal) {
        List<ProfileService.IdentityView> identities = profileService.identities(principal.userId());
        List<IdentityItem> items = identities.stream()
                .map(i -> new IdentityItem(
                        i.provider(), i.providerEmail(), i.emailVerified(), i.lastUsedAt()))
                .toList();
        return Map.of("items", items);
    }

    @Operation(operationId = "deleteAccountIdentity", summary = "Go lien ket Google")
    @DeleteMapping("/account/identities/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkIdentity(@PathVariable String provider,
                               @AuthenticationPrincipal AuthPrincipal principal) {
        profileService.unlinkIdentity(principal.userId(), provider);
    }


    @Operation(operationId = "getAccountNotificationPreferences", summary = "Tuy chon thong bao")
    @GetMapping("/account/notification-preferences")
    public NotificationPreferencesResponse getNotificationPreferences(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return NotificationPreferencesResponse.of(
                profileService.notificationPreferences(principal.userId()));
    }

    @Operation(operationId = "putAccountNotificationPreferences", summary = "Doi tuy chon thong bao")
    @PutMapping("/account/notification-preferences")
    public NotificationPreferencesResponse putNotificationPreferences(
            @RequestBody UpdateNotificationPreferencesRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return NotificationPreferencesResponse.of(profileService.updateNotificationPreferences(
                principal.userId(),
                UpdateNotificationPreferencesRequest.toPreferences(request)));
    }


    @Operation(operationId = "getAccountPrivacyAccessLog", summary = "Nhat ky truy cap du lieu")
    @GetMapping("/account/privacy/access-log")
    public Map<String, Object> accessLog(@AuthenticationPrincipal AuthPrincipal principal) {
        List<Map<String, Object>> items = auditAccessLogQuery.accessesToUserData(principal.userId(), 100).stream()
                .map(entry -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("occurredAt", entry.occurredAt());
                    row.put("actorType", entry.actorType());
                    row.put("actorRole", entry.actorRole());
                    row.put("action", entry.action());
                    row.put("result", entry.result());
                    return row;
                })
                .toList();
        return Map.of("items", items);
    }


    @Operation(operationId = "getAccountMfaTotp", summary = "Trang thai TOTP")
    @GetMapping("/account/mfa/totp")
    public Map<String, Object> totpStatus(@AuthenticationPrincipal AuthPrincipal principal) {
        MfaService.TotpStatusView status = mfaService.status(principal.userId());
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.status() == null ? "NONE" : status.status().name());
        body.put("activatedAt", status.activatedAt());
        body.put("recoveryCodesRemaining", status.recoveryCodesRemaining());
        body.put("lockedUntil", status.lockedUntil());
        return body;
    }

    @Operation(operationId = "postAccountMfaTotpInit", summary = "Bat dau enroll TOTP")
    @PostMapping("/account/mfa/totp/init")
    public Map<String, Object> totpInit(@AuthenticationPrincipal AuthPrincipal principal,
                                        HttpServletRequest httpRequest) {
        MfaService.Enrollment enrollment = mfaService.init(
                principal.userId(), principal.email(), principal.mfaLevel(), context(httpRequest));
        return Map.of(
                "secretBase32", enrollment.secretBase32(),
                "otpauthUri", enrollment.otpauthUri(),
                "expiresAt", enrollment.expiresAt().toString(),
                "digits", enrollment.digits(),
                "periodSeconds", enrollment.periodSeconds(),
                "algorithm", enrollment.algorithm());
    }

    @Operation(operationId = "postAccountMfaTotpConfirm", summary = "Xac nhan enroll TOTP")
    @PostMapping("/account/mfa/totp/confirm")
    public Map<String, Object> totpConfirm(@Valid @RequestBody TotpVerifyRequest request,
                                           @AuthenticationPrincipal AuthPrincipal principal,
                                           HttpServletRequest httpRequest) {
        MfaService.Confirmation confirmation = mfaService.confirm(
                principal.userId(), request.code(), context(httpRequest));
        return Map.of(
                "recoveryCodes", confirmation.recoveryCodes(),
                "activatedAt", confirmation.activatedAt().toString(),
                "mfaLevel", "TOTP");
    }

    @Operation(operationId = "postAccountMfaTotpRecoveryCodesRegenerate", summary = "Sinh lai ma khoi phuc")
    @PostMapping("/account/mfa/totp/recovery-codes/regenerate")
    public Map<String, Object> regenerateRecoveryCodes(
            @AuthenticationPrincipal AuthPrincipal principal,
            HttpServletRequest httpRequest) {
        MfaService.Confirmation confirmation = mfaService.regenerateRecoveryCodes(
                principal.userId(), principal.mfaLevel(), context(httpRequest));
        return Map.of(
                "recoveryCodes", confirmation.recoveryCodes(),
                "activatedAt", confirmation.activatedAt().toString());
    }

    @Operation(operationId = "deleteAccountMfaTotp", summary = "Tu go TOTP")
    @DeleteMapping("/account/mfa/totp")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTotp(@AuthenticationPrincipal AuthPrincipal principal,
                           HttpServletRequest httpRequest) {
        mfaService.delete(principal.userId(), principal.mfaLevel(), context(httpRequest));
    }


    private AuthRequestContext context(HttpServletRequest request) {
        return AuthRequestContext.of(
                request.getHeader("X-Request-Id"),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String maskedLocal = local.length() <= 1
                ? "*" : local.charAt(0) + "***" + local.charAt(local.length() - 1);
        return maskedLocal + "@" + parts[1];
    }
}
