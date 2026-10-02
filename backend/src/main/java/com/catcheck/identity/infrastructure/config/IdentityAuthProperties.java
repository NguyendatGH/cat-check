package com.catcheck.identity.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Tham so chinh sach xac thuc cua module identity (p11 §11.2.1, §11.3.1, §11.7.2).
 *
 * <p><b>Moi gia tri deu co {@link DefaultValue}</b> nen app khoi dong duoc ma khong
 * can khai trong {@code application.yml}. Cac khoang trong cho phep W3 chot lai
 * gia tri o {@code application*.yml} ma khong phai sua code.</p>
 *
 * <p>Bien moi truong KHONG nam o day: {@code OTP_PEPPER},
 * {@code OTP_PEPPER_VERSION}, {@code APP_PII_ENCRYPTION_KEYS},
 * {@code APP_PII_ENCRYPTION_WRITE_VERSION} duoc doc truc tiep trong
 * {@code ..infrastructure.crypto..} bang {@code @Value} vi chung la secret
 * (khong phai cau hinh chinh sach) va phai fail-fast theo rieng.</p>
 */
@ConfigurationProperties(prefix = "catcheck.auth")
@Validated
public record IdentityAuthProperties(

        /* --- OTP (p11 §11.2.1) --- */
        @NotNull @DefaultValue("5m") Duration otpTtl,
        @Min(1) @Max(20) @DefaultValue("5") int otpMaxAttempts,
        @NotNull @DefaultValue("10m") Duration otpTicketTtl,
        /**
         * p11 S5 + §11.2.1 + §11.13.1: <b>6 chữ số</b> chốt cứng (design {@code 01c-2} cũng
         * 6 ô nhập). Không cho hạ xuống dưới 6 — không gian 10^6 chỉ chấp nhận được
         * <i>vì</i> đã có giới hạn 5 lần thử + TTL 5 phút + rate limit.
         */
        @Min(6) @Max(8) @DefaultValue("6") int otpCodeLength,

        /* --- Mat khau (p11 S4, §11.3.1) --- */
        @Min(4) @Max(16) @DefaultValue("12") int bcryptStrength,
        @Min(8) @DefaultValue("8") int passwordMinLength,
        @Min(8) @DefaultValue("72") int passwordMaxLength,
        /**
         * So ngay giu lai danh sach mat khau pho bien. p11 §11.2.1/OQ-3: danh sach
         * tinh nap mot lan luc khoi dong, review 12 thang/lan.
         */
        @Min(1) @DefaultValue("365") int commonPasswordListMaxAgeDays,

        /* --- Khoa tai khoan do sai mat khau (p11 §11.7.2) --- */
        @Min(1) @DefaultValue("5") int lockoutThresholdShort,
        @NotNull @DefaultValue("15m") Duration lockoutDurationShort,
        @Min(1) @DefaultValue("10") int lockoutThresholdLong,
        @NotNull @DefaultValue("1h") Duration lockoutDurationLong,

        /* --- Phien (p11 §11.1.3) --- */
        @NotNull @DefaultValue("30d") Duration sessionMaxInactive,
        @NotNull @DefaultValue("12h") Duration sessionDefaultMaxInactive,
        @Min(1) @DefaultValue("5") int maxConcurrentSessions,

        /**
         * Cookie phien. KHONG phai ten mac dinh {@code JSESSIONID} de tranh
         * nhung ky tu cho thay the (session fixation). KHONG phai {@code CATCHECK_AUTH_TOKEN}
         * neu ten do dang de doan — p11 §11.1.3 yeu cau ten khu khoan doan.
         */
        @NotNull @DefaultValue("CATCHECK_SID") String sessionCookieName,

        /**
         * Bat filter xac thuc + CSRF cua module identity. Mac dinh {@code true} de
         * module hoat dong dung nhu spec ngay bay gio; xem {@code IdentitySecurityConfig}
         * de biet pham vi chuoi filter (chi {@code /api/v1/auth}, {@code /api/v1/account},
         * {@code /api/v1/users}) nen khong anh huong module khac.
         */
        @DefaultValue("true") boolean filterChainEnabled
) {
}
