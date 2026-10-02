package com.catcheck.identity.domain.port;

/**
 * Bam ma khoi phuc TOTP — {@code HMAC-SHA256(recovery_pepper, code)} (p11 §11.12.3).
 *
 * <p>KHONG dung BCrypt: salt ngau nhien cua BCrypt lam {@code UNIQUE(code_hash)}
 * khong the thuc thi, ma chinh chi muc do la thu chan hai admin vo tinh nhan trung
 * ma. HMAC + pepper ngoai DB giu dung co che cua OTP (§11.2.1) va ma kich hoat
 * (§11.7.4).</p>
 */
public interface RecoveryCodeHasher {

    /** Ma thoa — dung de so sanh constant-time. */
    String hash(int pepperVersion, String recoveryCode);

    int currentPepperVersion();

    /** So sanh constant-time — khong dung {@code String.equals}. */
    boolean matches(String recoveryCode, String codeHash, int pepperVersion);
}
